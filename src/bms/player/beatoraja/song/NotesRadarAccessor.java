package bms.player.beatoraja.song;

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import bms.model.BMSModel;
import bms.player.beatoraja.MainState;
import bms.player.beatoraja.PlayerResource;
import bms.player.beatoraja.select.MusicSelector;

/**
 * 選択中/プレイ中の譜面のノーツレーダーを別スレッドで解析し、結果を保持する。
 * Decoding a chart takes up to a few seconds, so the render thread only ever reads the cache: while the
 * cursor moves through the song list just the latest selection waits for analysis.
 */
public final class NotesRadarAccessor {

	private static final int CACHE_SIZE = 128;
	/** 解析できなかった譜面 */
	private static final double[] UNAVAILABLE = new double[0];

	private static final Map<String, double[]> cache = Collections.synchronizedMap(
			new LinkedHashMap<String, double[]>(CACHE_SIZE, 0.75f, true) {
				@Override
				protected boolean removeEldestEntry(Map.Entry<String, double[]> eldest) {
					return size() > CACHE_SIZE;
				}
			});
	private static final Set<String> queued = ConcurrentHashMap.newKeySet();
	/** 解析は1件ずつ。待ちは最新の1件だけ残す */
	private static final ThreadPoolExecutor worker = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
			new ArrayBlockingQueue<>(1), r -> {
				final Thread t = new Thread(r, "Notes radar analysis");
				t.setDaemon(true);
				return t;
			}, (r, executor) -> {
				if (!executor.isShutdown()) {
					final Runnable discarded = executor.getQueue().poll();
					if (discarded instanceof Analysis a) {
						queued.remove(a.key);
					}
					executor.execute(r);
				}
			});

	private NotesRadarAccessor() {
	}

	/**
	 * 状態ごとの対象譜面のノーツレーダー。選曲画面では選択中の譜面、それ以外では読み込んだ譜面
	 */
	public static Result get(MainState state) {
		final PlayerResource resource = state.resource;
		if (resource == null) {
			return Result.NONE;
		}
		final int lnmode = resource.getPlayerConfig().getLnmode();
		if (state instanceof MusicSelector) {
			final SongData song = resource.getSongdata();
			return song != null ? get(song.getPath(), song.getSha256(), lnmode) : Result.NONE;
		}
		final BMSModel model = resource.getBMSModel();
		if (model != null) {
			return get(model.getPath(), model.getSHA256(), lnmode);
		}
		final SongData song = resource.getSongdata();
		return song != null ? get(song.getPath(), song.getSha256(), lnmode) : Result.NONE;
	}

	/**
	 * @return 解析済みの値。未解析の場合は解析を予約する
	 */
	public static Result get(String path, String sha256, int lnmode) {
		if (path == null || path.isEmpty()) {
			return Result.NONE;
		}
		final String key = path + ":" + sha256 + ":" + lnmode;
		final double[] values = cache.get(key);
		if (values != null) {
			return values == UNAVAILABLE ? Result.NONE : new Result(key, values);
		}
		if (queued.add(key)) {
			worker.execute(new Analysis(key, path, lnmode));
		}
		return new Result(key, null);
	}

	private record Analysis(String key, String path, int lnmode) implements Runnable {
		@Override
		public void run() {
			double[] values = UNAVAILABLE;
			try {
				final BMSModel model = PlayerResource.decodeBMSModel(Path.of(path), lnmode);
				if (model != null) {
					values = NotesRadar.analyze(model);
				}
			} catch (Throwable e) {
				Logger.getGlobal().warning("ノーツレーダー解析失敗 : " + path + " : " + e.getMessage());
			}
			cache.put(key, values);
			queued.remove(key);
		}
	}

	/**
	 * @param key 譜面のキー。対象譜面がない場合はnull
	 * @param values 6項目の値(NotesRadarの添字順)。解析中または対象譜面がない場合はnull
	 */
	public record Result(String key, double[] values) {
		public static final Result NONE = new Result(null, null);

		public boolean isReady() {
			return values != null;
		}

		public boolean isAnalyzing() {
			return key != null && values == null;
		}

		public double get(int index) {
			return values != null ? values[index] : 0;
		}
	}
}
