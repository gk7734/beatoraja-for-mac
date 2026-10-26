import bms.player.beatoraja.audio.AudioUnitDevice;
import bms.player.beatoraja.audio.CoreAudioDevice;
import bms.player.beatoraja.audio.GdxAudioDeviceDriver;
import bms.player.beatoraja.AudioConfig;
import bms.player.beatoraja.Config;

/** Writes only silence to the real default output through the Audio Unit path. Run on an Apple Silicon Mac. */
public class AudioUnitSmokeTest {
    public static void main(String[] args) throws Exception {
        int rate = CoreAudioDevice.defaultSampleRate();
        System.out.println("Default output sample rate: " + rate);
        for (int frames : new int[] {128, 256, 384}) {
            AudioUnitDevice device = new AudioUnitDevice(rate, frames);
            try {
                device.setVolume(0);
                System.out.println("promoted=" + AudioUnitDevice.promoteCurrentThread(rate, frames));
                // Two seconds of silence, paced by the device: the writes must take about that long.
                final int blocks = rate * 2 / frames;
                final short[] silence = new short[frames * 2];
                final long start = System.nanoTime();
                for (int i = 0; i < blocks; i++) device.writeSamples(silence, 0, silence.length);
                final double seconds = (System.nanoTime() - start) / 1e9;
                AudioUnitDevice.Info info = device.info();
                System.out.printf("frames=%d io=%d ring=%d deviceLatency=%d latency=%.1fms underruns=%d paced=%.2fs%n",
                        frames, info.ioFrames(), info.ringFrames(), info.deviceLatencyFrames(),
                        device.getLatency() * 1000.0 / rate, info.underruns(), seconds);
                if (seconds < 1.8) throw new AssertionError("writes were not paced by the device");
                device.pause();
                device.resume();
                device.writeSamples(new float[512], 0, 512);
                try { device.writeSamples(new short[2], 0, 3); throw new AssertionError("range check"); }
                catch (IndexOutOfBoundsException expected) {}
            } finally { device.dispose(); device.dispose(); }
            try { device.writeSamples(new short[2], 0, 2); throw new AssertionError("closed check"); }
            catch (IllegalStateException expected) {}
        }
        Config config = new Config();
        config.validate();
        config.getAudioConfig().setDriver(AudioConfig.DriverType.CoreAudioUnit);
        config.getAudioConfig().setSampleRate(0);
        config.getAudioConfig().setDeviceBufferSize(256);
        for (int cycle = 0; cycle < 3; cycle++) {
            GdxAudioDeviceDriver driver = new GdxAudioDeviceDriver(config);
            Thread.sleep(200);
            driver.dispose();
        }
        Thread.sleep(100);
        boolean leaked = Thread.getAllStackTraces().keySet().stream()
                .anyMatch(t -> t.isAlive() && t.getName().equals("Core Audio Mixer"));
        if (leaked) throw new AssertionError("Mixer thread leaked");
        System.out.println("PASS: paced output, bounds, close, mixer lifecycle");
    }
}
