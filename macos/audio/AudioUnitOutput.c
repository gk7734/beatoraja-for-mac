// Low-latency output through the default output Audio Unit (AUHAL). The render callback runs on Core Audio's
// real-time IO thread and only copies from a single-producer/single-consumer ring that the Java mixer fills: it
// never enters the JVM, allocates or takes a lock. After each callback it wakes the mixer with a Mach semaphore.
#include <AudioToolbox/AudioToolbox.h>
#include <CoreAudio/CoreAudio.h>
#include <jni.h>
#include <mach/mach.h>
#include <mach/mach_time.h>
#include <mach/thread_policy.h>
#include <stdatomic.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

// Ring capacity in mixer blocks: one being played, one ready, one being mixed.
#define RING_BLOCKS 3

typedef struct {
    AudioUnit unit;
    semaphore_t space;
    int16_t *ring;              // interleaved stereo
    uint32_t capacity;          // frames
    _Atomic uint64_t written;   // frames, advanced by the mixer
    _Atomic uint64_t read;      // frames, advanced by the render callback
    _Atomic uint64_t underruns; // callbacks that found less data than requested once playback started
    _Atomic uint64_t hostTime;  // host time of the last callback's output, for later clock mapping
    _Atomic int primed;
    int running;
    uint32_t ioFrames;
    uint32_t deviceLatency;     // device + safety offset + stream latency, in device frames
} Output;

static void fail(JNIEnv *env, const char *operation, OSStatus status) {
    char message[160];
    snprintf(message, sizeof(message), "Core Audio Unit %s failed (OSStatus %d)", operation, (int)status);
    (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/IllegalStateException"), message);
}

static OSStatus render(void *context, AudioUnitRenderActionFlags *flags, const AudioTimeStamp *time,
                       UInt32 bus, UInt32 frames, AudioBufferList *data) {
    Output *out = context;
    int16_t *dst = data->mBuffers[0].mData;
    const uint64_t r = atomic_load_explicit(&out->read, memory_order_relaxed);
    const uint64_t w = atomic_load_explicit(&out->written, memory_order_acquire);
    const uint32_t n = w - r < frames ? (uint32_t)(w - r) : frames;
    const uint32_t start = (uint32_t)(r % out->capacity);
    const uint32_t first = n < out->capacity - start ? n : out->capacity - start;
    memcpy(dst, out->ring + start * 2, first * 4);
    memcpy(dst + first * 2, out->ring, (n - first) * 4);
    if (n < frames) {
        memset(dst + n * 2, 0, (frames - n) * 4);
        if (atomic_load_explicit(&out->primed, memory_order_relaxed))
            atomic_fetch_add_explicit(&out->underruns, 1, memory_order_relaxed);
        if (n == 0) *flags |= kAudioUnitRenderAction_OutputIsSilence;
    }
    atomic_store_explicit(&out->read, r + n, memory_order_release);
    if (time->mFlags & kAudioTimeStampHostTimeValid)
        atomic_store_explicit(&out->hostTime, time->mHostTime, memory_order_relaxed);
    semaphore_signal(out->space);
    return noErr;
}

static void destroy(Output *out) {
    if (out->unit) {
        AudioOutputUnitStop(out->unit);
        AudioUnitUninitialize(out->unit);
        AudioComponentInstanceDispose(out->unit);
    }
    if (out->space) semaphore_destroy(mach_task_self(), out->space);
    free(out->ring);
    free(out);
}

static UInt32 deviceUInt32(AudioDeviceID device, AudioObjectPropertySelector selector, AudioObjectPropertyScope scope) {
    AudioObjectPropertyAddress address = {selector, scope, kAudioObjectPropertyElementMain};
    UInt32 value = 0, size = sizeof(value);
    return AudioObjectGetPropertyData(device, &address, 0, NULL, &size, &value) == noErr ? value : 0;
}

static UInt32 outputLatency(AudioDeviceID device) {
    UInt32 latency = deviceUInt32(device, kAudioDevicePropertyLatency, kAudioObjectPropertyScopeOutput)
        + deviceUInt32(device, kAudioDevicePropertySafetyOffset, kAudioObjectPropertyScopeOutput);
    AudioObjectPropertyAddress address = {kAudioDevicePropertyStreams, kAudioObjectPropertyScopeOutput,
        kAudioObjectPropertyElementMain};
    AudioStreamID stream = 0;
    UInt32 size = sizeof(stream);
    // Only the first output stream; the default output unit plays through it.
    if (AudioObjectGetPropertyData(device, &address, 0, NULL, &size, &stream) == noErr && size >= sizeof(stream))
        latency += deviceUInt32(stream, kAudioStreamPropertyLatency, kAudioObjectPropertyScopeGlobal);
    return latency;
}

JNIEXPORT jlong JNICALL Java_bms_player_beatoraja_audio_AudioUnitDevice_open
  (JNIEnv *env, jclass cls, jint rate, jint frames) {
    if (rate < 8000 || rate > 192000 || frames < 32 || frames > 16384) {
        fail(env, "invalid format", kAudio_ParamError); return 0;
    }
    Output *out = calloc(1, sizeof(Output));
    if (!out) { fail(env, "allocation", -108); return 0; }
    out->capacity = (uint32_t)frames * RING_BLOCKS;
    out->ring = calloc(out->capacity * 2, sizeof(int16_t));
    if (!out->ring || semaphore_create(mach_task_self(), &out->space, SYNC_POLICY_FIFO, 0) != KERN_SUCCESS) {
        out->space = 0; destroy(out); fail(env, "allocation", -108); return 0;
    }
    // The default output unit follows the system output device when the user switches it.
    AudioComponentDescription description = {kAudioUnitType_Output, kAudioUnitSubType_DefaultOutput,
        kAudioUnitManufacturer_Apple, 0, 0};
    AudioComponent component = AudioComponentFindNext(NULL, &description);
    OSStatus status = component ? AudioComponentInstanceNew(component, &out->unit) : kAudioUnitErr_InvalidElement;
    if (status != noErr) { out->unit = NULL; destroy(out); fail(env, "open", status); return 0; }

    AudioStreamBasicDescription format = {0};
    format.mSampleRate = rate;
    format.mFormatID = kAudioFormatLinearPCM;
    format.mFormatFlags = kLinearPCMFormatFlagIsSignedInteger | kLinearPCMFormatFlagIsPacked;
    format.mBytesPerPacket = format.mBytesPerFrame = 4;
    format.mFramesPerPacket = 1;
    format.mChannelsPerFrame = 2;
    format.mBitsPerChannel = 16;
    status = AudioUnitSetProperty(out->unit, kAudioUnitProperty_StreamFormat, kAudioUnitScope_Input, 0,
        &format, sizeof(format));
    AURenderCallbackStruct callback = {render, out};
    if (status == noErr)
        status = AudioUnitSetProperty(out->unit, kAudioUnitProperty_SetRenderCallback, kAudioUnitScope_Input, 0,
            &callback, sizeof(callback));

    // Ask the device for an IO buffer as small as one mixer block, within what it allows.
    AudioDeviceID device = kAudioObjectUnknown;
    UInt32 size = sizeof(device);
    if (status == noErr)
        status = AudioUnitGetProperty(out->unit, kAudioOutputUnitProperty_CurrentDevice, kAudioUnitScope_Global, 0,
            &device, &size);
    if (status == noErr) {
        AudioObjectPropertyAddress address = {kAudioDevicePropertyBufferFrameSizeRange,
            kAudioObjectPropertyScopeGlobal, kAudioObjectPropertyElementMain};
        AudioValueRange range = {0, 0};
        size = sizeof(range);
        UInt32 io = (UInt32)frames;
        if (AudioObjectGetPropertyData(device, &address, 0, NULL, &size, &range) == noErr && range.mMaximum > 0) {
            if (io < range.mMinimum) io = (UInt32)range.mMinimum;
            if (io > range.mMaximum) io = (UInt32)range.mMaximum;
        }
        // Another app holding a smaller buffer on the same device can make this fail; keep the device's size.
        AudioUnitSetProperty(out->unit, kAudioDevicePropertyBufferFrameSize, kAudioUnitScope_Global, 0,
            &io, sizeof(io));
    }
    if (status == noErr) status = AudioUnitInitialize(out->unit);
    if (status != noErr) { destroy(out); fail(env, "configure", status); return 0; }
    out->ioFrames = deviceUInt32(device, kAudioDevicePropertyBufferFrameSize, kAudioObjectPropertyScopeGlobal);
    out->deviceLatency = outputLatency(device);
    return (jlong)(intptr_t)out;
}

// Java serializes write/control/close. Blocks until the ring has room for the whole write.
JNIEXPORT void JNICALL Java_bms_player_beatoraja_audio_AudioUnitDevice_write
  (JNIEnv *env, jclass cls, jlong handle, jshortArray samples, jint offset, jint count) {
    Output *out = (Output *)(intptr_t)handle;
    const uint32_t frames = (uint32_t)count / 2;
    if (frames > out->capacity) { fail(env, "write size", kAudio_ParamError); return; }
    const uint64_t w = atomic_load_explicit(&out->written, memory_order_relaxed);
    int timedOut = 0;
    while (out->capacity - (w - atomic_load_explicit(&out->read, memory_order_acquire)) < frames) {
        if (timedOut) { fail(env, "output timeout", -1); return; }
        // A suspended process (debugger, system stall) wakes past the timeout even though the callback may
        // have drained the ring meanwhile, so a timeout only fails after one more look.
        mach_timespec_t timeout = {1, 0};
        timedOut = semaphore_timedwait(out->space, timeout) == KERN_OPERATION_TIMED_OUT;
    }
    const uint32_t start = (uint32_t)(w % out->capacity);
    const uint32_t first = frames < out->capacity - start ? frames : out->capacity - start;
    (*env)->GetShortArrayRegion(env, samples, offset, (jsize)first * 2, out->ring + start * 2);
    (*env)->GetShortArrayRegion(env, samples, offset + (jint)first * 2, (jsize)(frames - first) * 2, out->ring);
    if ((*env)->ExceptionCheck(env)) return;
    atomic_store_explicit(&out->written, w + frames, memory_order_release);
    // Start once the ring is full so the first callbacks do not run dry.
    if (!out->running && w + frames >= out->capacity) {
        OSStatus status = AudioOutputUnitStart(out->unit);
        if (status != noErr) { fail(env, "start", status); return; }
        out->running = 1;
        atomic_store_explicit(&out->primed, 1, memory_order_relaxed);
    }
}

JNIEXPORT void JNICALL Java_bms_player_beatoraja_audio_AudioUnitDevice_close
  (JNIEnv *env, jclass cls, jlong handle) { destroy((Output *)(intptr_t)handle); }

JNIEXPORT void JNICALL Java_bms_player_beatoraja_audio_AudioUnitDevice_control
  (JNIEnv *env, jclass cls, jlong handle, jint action, jfloat volume) {
    Output *out = (Output *)(intptr_t)handle;
    OSStatus status = noErr;
    if (action == 0) {
        status = AudioUnitSetParameter(out->unit, kHALOutputParam_Volume, kAudioUnitScope_Global, 0, volume, 0);
    } else if (action == 1) {
        if (out->running) status = AudioOutputUnitStop(out->unit);
        out->running = 0;
    } else if (!out->running && atomic_load_explicit(&out->primed, memory_order_relaxed)) {
        status = AudioOutputUnitStart(out->unit);
        out->running = status == noErr;
    }
    if (status != noErr) fail(env, "control", status);
}

// {IO buffer frames, device output latency frames, underruns, ring frames}
JNIEXPORT void JNICALL Java_bms_player_beatoraja_audio_AudioUnitDevice_info
  (JNIEnv *env, jclass cls, jlong handle, jlongArray result) {
    Output *out = (Output *)(intptr_t)handle;
    const jlong values[4] = {out->ioFrames, out->deviceLatency,
        (jlong)atomic_load_explicit(&out->underruns, memory_order_relaxed), out->capacity};
    (*env)->SetLongArrayRegion(env, result, 0, 4, values);
}

// Gives the calling (mixer) thread a real-time time-constraint policy for one block period, so it is scheduled
// in time to refill the ring. The kernel demotes a thread that overruns its computation budget, so a slow mix
// cannot starve the system.
JNIEXPORT jboolean JNICALL Java_bms_player_beatoraja_audio_AudioUnitDevice_promoteCurrentThread
  (JNIEnv *env, jclass cls, jint rate, jint frames) {
    if (rate <= 0 || frames <= 0) return JNI_FALSE;
    mach_timebase_info_data_t timebase;
    mach_timebase_info(&timebase);
    const double period = 1e9 * frames / rate * timebase.denom / timebase.numer;
    struct thread_time_constraint_policy policy;
    policy.period = (uint32_t)period;
    policy.computation = (uint32_t)(period / 2);
    policy.constraint = (uint32_t)period;
    policy.preemptible = 1;
    mach_port_t thread = mach_thread_self();
    kern_return_t result = thread_policy_set(thread, THREAD_TIME_CONSTRAINT_POLICY, (thread_policy_t)&policy,
        THREAD_TIME_CONSTRAINT_POLICY_COUNT);
    mach_port_deallocate(mach_task_self(), thread);
    return result == KERN_SUCCESS ? JNI_TRUE : JNI_FALSE;
}
