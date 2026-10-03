#include <AudioToolbox/AudioToolbox.h>
#include <CoreAudio/CoreAudio.h>
#include <stdio.h>
#include <jni.h>
#include <pthread.h>
#include <stdint.h>
#include <stdlib.h>
#include <time.h>
#include <errno.h>

#define BUFFER_COUNT 3
typedef struct {
    AudioQueueRef queue;
    AudioQueueBufferRef buffers[BUFFER_COUNT];
    int available[BUFFER_COUNT];
    pthread_mutex_t lock;
    pthread_cond_t ready;
    int started;
} Output;

static void fail(JNIEnv *env, const char *operation, OSStatus status) {
    char message[160];
    snprintf(message, sizeof(message), "Core Audio %s failed (OSStatus %d)", operation, (int)status);
    (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/IllegalStateException"), message);
}

static void completed(void *context, AudioQueueRef queue, AudioQueueBufferRef buffer) {
    Output *out = context;
    pthread_mutex_lock(&out->lock);
    for (int i = 0; i < BUFFER_COUNT; i++)
        if (out->buffers[i] == buffer) out->available[i] = 1;
    pthread_cond_signal(&out->ready);
    pthread_mutex_unlock(&out->lock);
}

static void destroy(Output *out) {
    if (out->queue) AudioQueueDispose(out->queue, true);
    pthread_cond_destroy(&out->ready);
    pthread_mutex_destroy(&out->lock);
    free(out);
}

JNIEXPORT jlong JNICALL Java_bms_player_beatoraja_audio_CoreAudioDevice_open
  (JNIEnv *env, jclass cls, jint rate, jint frames) {
    if (rate < 8000 || rate > 192000 || frames < 32 || frames > 16384) {
        fail(env, "invalid format", kAudio_ParamError); return 0;
    }
    Output *out = calloc(1, sizeof(Output));
    if (!out) { fail(env, "allocation", -108); return 0; }
    pthread_mutex_init(&out->lock, NULL);
    pthread_cond_init(&out->ready, NULL);
    AudioStreamBasicDescription format = {0};
    format.mSampleRate = rate;
    format.mFormatID = kAudioFormatLinearPCM;
    format.mFormatFlags = kLinearPCMFormatFlagIsSignedInteger | kLinearPCMFormatFlagIsPacked;
    format.mBytesPerPacket = format.mBytesPerFrame = 4;
    format.mFramesPerPacket = 1;
    format.mChannelsPerFrame = 2;
    format.mBitsPerChannel = 16;
    OSStatus status = AudioQueueNewOutput(&format, completed, out, NULL, NULL, 0, &out->queue);
    for (int i = 0; status == noErr && i < BUFFER_COUNT; i++) {
        status = AudioQueueAllocateBuffer(out->queue, frames * 4, &out->buffers[i]);
        out->available[i] = 1;
    }
    if (status != noErr) { destroy(out); fail(env, "open", status); return 0; }
    return (jlong)(intptr_t)out;
}

// Java serializes write/control/close. The callback only returns buffers, never enters the JVM.
JNIEXPORT void JNICALL Java_bms_player_beatoraja_audio_CoreAudioDevice_write
  (JNIEnv *env, jclass cls, jlong handle, jshortArray samples, jint offset, jint count) {
    Output *out = (Output *)(intptr_t)handle;
    struct timespec deadline;
    clock_gettime(CLOCK_REALTIME, &deadline);
    deadline.tv_sec += 1;
    pthread_mutex_lock(&out->lock);
    int index = -1;
    while (index < 0) {
        for (int i = 0; i < BUFFER_COUNT; i++) if (out->available[i]) { index = i; break; }
        if (index < 0 && pthread_cond_timedwait(&out->ready, &out->lock, &deadline) != 0) {
            pthread_mutex_unlock(&out->lock);
            fail(env, "output timeout", -1); return;
        }
    }
    out->available[index] = 0;
    pthread_mutex_unlock(&out->lock);
    AudioQueueBufferRef buffer = out->buffers[index];
    (*env)->GetShortArrayRegion(env, samples, offset, count, buffer->mAudioData);
    if ((*env)->ExceptionCheck(env)) return;
    buffer->mAudioDataByteSize = count * sizeof(jshort);
    OSStatus status = AudioQueueEnqueueBuffer(out->queue, buffer, 0, NULL);
    if (status == noErr && !out->started) {
        status = AudioQueueStart(out->queue, NULL);
        if (status == noErr) out->started = 1;
    }
    if (status != noErr) fail(env, "write/start", status);
}

JNIEXPORT void JNICALL Java_bms_player_beatoraja_audio_CoreAudioDevice_close
  (JNIEnv *env, jclass cls, jlong handle) { destroy((Output *)(intptr_t)handle); }

JNIEXPORT void JNICALL Java_bms_player_beatoraja_audio_CoreAudioDevice_control
  (JNIEnv *env, jclass cls, jlong handle, jint action, jfloat volume) {
    Output *out = (Output *)(intptr_t)handle;
    OSStatus status;
    if (action == 0) status = AudioQueueSetParameter(out->queue, kAudioQueueParam_Volume, volume);
    else if (action == 1) status = AudioQueuePause(out->queue);
    else status = AudioQueueStart(out->queue, NULL);
    if (status != noErr) fail(env, "control", status);
}

JNIEXPORT jint JNICALL Java_bms_player_beatoraja_audio_CoreAudioDevice_defaultSampleRate
  (JNIEnv *env, jclass cls) {
    AudioDeviceID device = kAudioObjectUnknown;
    UInt32 size = sizeof(device);
    AudioObjectPropertyAddress address = {kAudioHardwarePropertyDefaultOutputDevice,
        kAudioObjectPropertyScopeGlobal, kAudioObjectPropertyElementMain};
    OSStatus status = AudioObjectGetPropertyData(kAudioObjectSystemObject, &address, 0, NULL, &size, &device);
    Float64 rate = 0;
    if (status == noErr && device != kAudioObjectUnknown) {
        address.mSelector = kAudioDevicePropertyNominalSampleRate;
        size = sizeof(rate);
        status = AudioObjectGetPropertyData(device, &address, 0, NULL, &size, &rate);
    }
    if (status != noErr || rate < 8000 || rate > 192000) {
        fail(env, "default output sample rate", status); return 0;
    }
    return (jint)rate;
}
