import bms.player.beatoraja.audio.CoreAudioDevice;
import bms.player.beatoraja.audio.GdxAudioDeviceDriver;
import bms.player.beatoraja.AudioConfig;
import bms.player.beatoraja.Config;

/** Writes only silence to the real default output. Run on an Apple Silicon Mac. */
public class CoreAudioSmokeTest {
    public static void main(String[] args) throws Exception {
        int rate = CoreAudioDevice.defaultSampleRate();
        System.out.println("Default output sample rate: " + rate);
        for (int cycle = 0; cycle < 5; cycle++) {
            CoreAudioDevice device = new CoreAudioDevice(cycle == 0 ? rate : 44100, 256);
            try {
                device.setVolume(0);
                for (int i = 0; i < 100; i++) device.writeSamples(new short[512], 0, 512);
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
        config.getAudioConfig().setDriver(AudioConfig.DriverType.CoreAudio);
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
        System.out.println("PASS: repeated playback, bounds, close, mixer lifecycle");
    }
}
