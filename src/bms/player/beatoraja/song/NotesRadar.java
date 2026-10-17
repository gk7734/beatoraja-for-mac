package bms.player.beatoraja.song;

import java.util.ArrayDeque;

import bms.model.BMSModel;
import bms.model.LongNote;
import bms.model.Mode;
import bms.model.Note;
import bms.model.NormalNote;
import bms.model.TimeLine;

/**
 * ノーツレーダー。譜面の傾向を6項目(NOTES, PEAK, SCRATCH, SOF-LAN, CHARGE, CHORD)の0-200の値で表す。
 * Same measure as the extended engines' NotesRadar.analyze, which skins such as IIDX-light read through
 * NUMBER 650-655 and the radargraph object.
 */
public final class NotesRadar {

	public static final int NOTES = 0;
	public static final int PEAK = 1;
	public static final int SCRATCH = 2;
	public static final int SOFLAN = 3;
	public static final int CHARGE = 4;
	public static final int CHORD = 5;
	public static final int LENGTH = 6;

	public static final double MAX_VALUE = 200.0;

	private NotesRadar() {
	}

	/**
	 * @param model 譜面
	 * @return 6項目の値(0-200)。添字はNOTES, PEAK, SCRATCH, SOFLAN, CHARGE, CHORD
	 */
	public static double[] analyze(BMSModel model) {
		final Mode mode = model.getMode();
		final int keys = mode.key;
		final boolean[] scratch = new boolean[keys];
		for (int i = 0; i < keys; i++) {
			scratch[i] = mode.isScratchKey(i);
		}
		int notes = 0;
		int scratches = 0;
		int chords = 0;
		int peak = 0;
		int window = 0;
		long first = Long.MAX_VALUE;
		long last = 0;
		double charge = 0;
		double soflan = 0;
		double prev = Double.NaN;
		// 直近1秒間のノーツ {時刻, 数}
		final ArrayDeque<long[]> recent = new ArrayDeque<>();

		for (TimeLine tl : model.getAllTimeLines()) {
			final long time = tl.getTime();
			if (time < 0) {
				continue;
			}
			int count = 0;
			if (tl.existNote()) {
				for (int i = 0; i < keys; i++) {
					final Note note = tl.getNote(i);
					boolean counted = false;
					if (note instanceof NormalNote) {
						counted = true;
					} else if (note instanceof LongNote ln && !ln.isEnd()) {
						counted = true;
						final LongNote pair = ln.getPair();
						if (pair != null) {
							final long end = pair.getTime();
							charge += Math.max(0, end - time) / 1000.0;
							last = Math.max(last, end);
						}
					}
					if (counted) {
						count++;
						if (scratch[i]) {
							scratches++;
						}
					}
				}
			}
			if (count > 0) {
				notes += count;
				chords += count - 1;
				first = Math.min(first, time);
				last = Math.max(last, time);
				while (!recent.isEmpty() && time - recent.peekFirst()[0] >= 1000) {
					window -= (int) recent.pollFirst()[1];
				}
				recent.addLast(new long[] { time, count });
				window += count;
				peak = Math.max(peak, window);
			}
			final double speed = tl.getBPM() * tl.getScroll();
			if (Double.isFinite(speed)) {
				if (Double.isFinite(prev) && speed != prev) {
					final double a = Math.max(1.0, Math.abs(prev));
					final double b = Math.max(1.0, Math.abs(speed));
					soflan += Math.min(4.0, Math.abs(Math.log(b / a) / Math.log(2.0)));
					if (Math.signum(prev) != Math.signum(speed)) {
						soflan += 1.0;
					}
				}
				prev = speed;
			}
			final double stop = tl.getStop();
			if (stop > 0) {
				soflan += Math.min(2.0, stop / 500.0);
			}
		}

		final double[] values = new double[LENGTH];
		if (notes == 0) {
			return values;
		}
		final double length = Math.max(1.0, (last - first) / 1000.0);
		values[NOTES] = notes / length * 10.0;
		values[PEAK] = peak * 5.0;
		values[SCRATCH] = scratches / length * 50.0;
		values[SOFLAN] = soflan / length * 300.0;
		values[CHARGE] = charge / length * 100.0;
		values[CHORD] = chords / length * 20.0;
		for (int i = 0; i < LENGTH; i++) {
			values[i] = Double.isFinite(values[i]) ? Math.max(0.0, Math.min(MAX_VALUE, values[i])) : 0.0;
		}
		return values;
	}
}
