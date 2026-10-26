package bms.player.beatoraja.audio;

import com.badlogic.gdx.audio.AudioDevice;
import java.util.Objects;

/**
 * Low-latency Apple output through the default output Audio Unit. Core Audio's real-time callback copies from a
 * ring of {@code frames * 3} frames that this device fills and never calls into the JVM; the device IO buffer is
 * set to {@code frames} where the hardware allows.
 */
public final class AudioUnitDevice implements AudioDevice {
    static { System.loadLibrary("beatoraja_coreaudio"); }
    private long handle;
    private final int frames;
    private boolean paused;

    public AudioUnitDevice(int sampleRate, int frames) {
        this.frames = frames;
        handle = open(sampleRate, frames);
    }
    private void requireOpen() {
        if (handle == 0) throw new IllegalStateException("Core Audio Unit device is closed");
    }
    @Override public boolean isMono() { return false; }
    /** Ring, IO buffer and the device's reported output latency, in frames. */
    @Override public synchronized int getLatency() {
        final Info info = info();
        return (int) (info.ringFrames() + info.ioFrames() + info.deviceLatencyFrames());
    }
    @Override public synchronized void writeSamples(short[] samples, int offset, int count) {
        Objects.checkFromIndexSize(offset, count, samples.length);
        if ((count & 1) != 0) throw new IllegalArgumentException("Stereo samples must be paired");
        requireOpen();
        if (paused) throw new IllegalStateException("Core Audio Unit device is paused");
        while (count > 0) {
            int size = Math.min(count, frames * 2);
            write(handle, samples, offset, size);
            count -= size;
            offset += size;
        }
    }
    @Override public void writeSamples(float[] samples, int offset, int count) {
        Objects.checkFromIndexSize(offset, count, samples.length);
        short[] pcm = new short[count];
        for (int i = 0; i < count; i++)
            pcm[i] = (short)(Math.max(-1f, Math.min(1f, samples[offset + i])) * 32767);
        writeSamples(pcm, 0, count);
    }
    @Override public synchronized void setVolume(float volume) {
        requireOpen();
        if (!Float.isFinite(volume)) throw new IllegalArgumentException("Non-finite volume");
        control(handle, 0, Math.max(0, Math.min(1, volume)));
    }
    @Override public synchronized void pause() { requireOpen(); control(handle, 1, 0); paused = true; }
    @Override public synchronized void resume() { requireOpen(); control(handle, 2, 0); paused = false; }
    @Override public synchronized void dispose() {
        if (handle != 0) { close(handle); handle = 0; }
    }

    /** Output state for logs and latency measurement. Underruns count callbacks that found the ring short. */
    public record Info(long ioFrames, long deviceLatencyFrames, long underruns, long ringFrames) {}

    public synchronized Info info() {
        requireOpen();
        final long[] values = new long[4];
        info(handle, values);
        return new Info(values[0], values[1], values[2], values[3]);
    }

    /** Puts the calling thread on a real-time schedule with one {@code frames} period. Returns false if refused. */
    public static native boolean promoteCurrentThread(int sampleRate, int frames);

    private static native long open(int sampleRate, int frames);
    private static native void write(long handle, short[] samples, int offset, int count);
    private static native void close(long handle);
    private static native void control(long handle, int action, float volume);
    private static native void info(long handle, long[] result);
}
