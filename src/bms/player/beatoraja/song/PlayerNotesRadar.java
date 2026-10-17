package bms.player.beatoraja.song;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import bms.model.BMSModel;
import bms.model.Mode;
import bms.player.beatoraja.DecodedImageCache;
import bms.player.beatoraja.MainController;
import bms.player.beatoraja.MainState;
import bms.player.beatoraja.PlayDataAccessor;
import bms.player.beatoraja.PlayerResource;
import bms.player.beatoraja.ScoreData;
import bms.player.beatoraja.select.MusicSelector;

/**
 * プレイヤーのノーツレーダー(PLAYER NOTES RADAR)。
 * IIDXと同じ計算: 各譜面の値 = 譜面のノーツレーダー値 x スコア率(EXスコア / 理論値、百分率の小数点以下2桁で切り捨て)。
 * 楽曲ごとに項目別で最も高い譜面の値を代表とし、項目ごとに上位10曲の合計を10で割る(10曲未満でも10で割る)。
 * クリアの有無は問わない。Matches the official manual (each attribute totals 10 target songs; its result screen
 * shows the top 10 summing to 351.78 for a PLAYER NOTES RADAR of 35.17), so it grows as more songs are played well.
 * SP (5/7 keys) and DP (10/14 keys) are kept apart; other modes form their own group. Computed on a worker thread from all score records
 * whenever the select screen is entered; chart radars are cached on disk because decoding every played chart
 * again on each start would take seconds.
 */
public final class PlayerNotesRadar {

	public static final int TOP = 10;

	private static final String CACHE_VERSION = "notes-radar-v1";

	private static final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
		final Thread t = new Thread(r, "Player notes radar");
		t.setDaemon(true);
		return t;
	});
	private static final AtomicInteger generation = new AtomicInteger();
	/** 最新の計算結果。nullの間は未計算 */
	private static volatile Snapshot radar;
	/** ひとつ前の計算結果(リザルトでの上昇表示用) */
	private static volatile Snapshot previous;
	private static volatile int radarGeneration;
	private static volatile boolean computing;

	/** sha256 -> 譜面のノーツレーダー値 */
	private static final Map<String, double[]> chartRadar = new ConcurrentHashMap<>();
	private static boolean chartRadarLoaded;

	private PlayerNotesRadar() {
	}

	/**
	 * ランキングの1曲。楽曲を代表する譜面(その項目で値が最も高い譜面)の情報
	 *
	 * @param value 値(譜面のノーツレーダー値 x スコア率)
	 * @param difficulty 譜面の難易度(0: 不明, 1: BEGINNER ... 5: LEGGENDARIA)
	 * @param song 楽曲の識別子(同じフォルダの譜面で共通)
	 */
	public record RankEntry(double value, String title, int level, int difficulty, String song) {
	}

	/**
	 * @return 譜面が属する楽曲の識別子。ランキングの曲(RankEntry.song)と比べる
	 */
	public static String songOf(SongData song) {
		return song != null ? songKey(song) : null;
	}

	/**
	 * @param values グループ(SPは7、DPは14、それ以外はモードID)ごとの6項目の値
	 * @param ranking グループごと、項目ごとの上位TOP曲(値の高い順)
	 */
	record Snapshot(Map<Integer, double[]> values, Map<Integer, List<List<RankEntry>>> ranking) {
		static final Snapshot EMPTY = new Snapshot(Map.of(), Map.of());
	}

	/**
	 * 現在のスコアから計算し直す。選曲画面に入るたび、およびリザルトでスコアを保存した後に呼ぶ
	 */
	public static void refresh(MainController main) {
		final int gen = generation.incrementAndGet();
		computing = true;
		worker.execute(() -> {
			if (gen != generation.get()) {
				// より新しい要求が待っている
				return;
			}
			try {
				final Snapshot result = compute(main.getPlayDataAccessor(), main.getSongDatabase());
				previous = radar;
				radar = result;
				radarGeneration = gen;
			} catch (Throwable e) {
				Logger.getGlobal().warning("プレイヤーノーツレーダー計算失敗 : " + e.getMessage());
			} finally {
				if (gen == generation.get()) {
					computing = false;
				}
			}
		});
	}

	/**
	 * プレイヤーのノーツレーダー。選曲画面ではモードフィルタ(フィルタなしはSP)、それ以外は読み込んだ譜面のモードのもの
	 */
	public static NotesRadarAccessor.Result get(MainState state) {
		final Snapshot r = radar;
		final int group = groupOf(state);
		final String key = "player:" + group + ":" + radarGeneration;
		if (r == null) {
			return computing ? new NotesRadarAccessor.Result(key, null) : NotesRadarAccessor.Result.NONE;
		}
		final double[] values = r.values().get(group);
		return new NotesRadarAccessor.Result(key, values != null ? values : new double[NotesRadar.LENGTH]);
	}

	/**
	 * @return ひとつ前の計算での値。前回の計算がない場合はnull
	 */
	public static double[] getPrevious(MainState state) {
		final Snapshot p = previous;
		if (p == null) {
			return null;
		}
		final double[] values = p.values().get(groupOf(state));
		return values != null ? values : new double[NotesRadar.LENGTH];
	}

	/**
	 * @param index 項目(NotesRadarの添字)
	 * @return 項目の上位曲(値の高い順、最大TOP曲)。未計算の場合は空
	 */
	public static List<RankEntry> getRanking(MainState state, int index) {
		final Snapshot r = radar;
		final List<List<RankEntry>> ranking = r != null ? r.ranking().get(groupOf(state)) : null;
		return ranking != null ? ranking.get(index) : List.of();
	}

	/**
	 * @return 対象のプレイスタイル。SP, DP、それ以外はモード名
	 */
	public static String styleOf(MainState state) {
		final int group = groupOf(state);
		if (group == Mode.BEAT_7K.id) {
			return "SP";
		}
		if (group == Mode.BEAT_14K.id) {
			return "DP";
		}
		for (Mode mode : Mode.values()) {
			if (mode.id == group) {
				return mode.hint;
			}
		}
		return "";
	}

	private static int groupOf(MainState state) {
		if (state.resource == null) {
			return Mode.BEAT_7K.id;
		}
		if (!(state instanceof MusicSelector)) {
			final BMSModel model = state.resource.getBMSModel();
			if (model != null && model.getMode() != null) {
				return group(model.getMode().id);
			}
		}
		final Mode mode = state.resource.getPlayerConfig().getMode();
		return group(mode != null ? mode.id : Mode.BEAT_7K.id);
	}

	/** SP(5/7鍵) と DP(10/14鍵) をまとめる */
	private static int group(int modeId) {
		if (modeId == Mode.BEAT_5K.id) {
			return Mode.BEAT_7K.id;
		}
		if (modeId == Mode.BEAT_10K.id) {
			return Mode.BEAT_14K.id;
		}
		return modeId;
	}

	/**
	 * @return グループごとの6項目の値と上位曲
	 */
	static Snapshot compute(PlayDataAccessor playdata, SongDatabaseAccessor songdb) {
		loadChartRadar();
		final List<ScoreData> scores = playdata.readScoreDatas("1");
		if (scores == null) {
			return Snapshot.EMPTY;
		}
		// sha256 -> 最高EXスコア達成率 (LNモード違いのスコアは良い方)
		final Map<String, Double> rates = new HashMap<>();
		for (ScoreData score : scores) {
			if (score.getNotes() <= 0 || score.getSha256() == null) {
				continue;
			}
			rates.merge(score.getSha256(), scoreRate(score.getExscore(), score.getNotes()), Math::max);
		}
		if (rates.isEmpty()) {
			return Snapshot.EMPTY;
		}
		// getSongDatas(String[])は結果を引数の順に並べ直す(件数の2乗)ため、分けて問い合わせる
		final String[] hashes = rates.keySet().toArray(new String[0]);
		final List<SongData> songs = new ArrayList<>();
		for (int i = 0; i < hashes.length; i += 200) {
			songs.addAll(Arrays.asList(songdb.getSongDatas(
					Arrays.copyOfRange(hashes, i, Math.min(hashes.length, i + 200)))));
		}

		// group -> 楽曲 -> 項目ごとの代表譜面。楽曲の代表は項目ごとに、その楽曲の譜面で最も値が高い譜面
		final Map<Integer, Map<String, RankEntry[]>> plays = new HashMap<>();
		final Map<String, Boolean> seen = new HashMap<>();
		for (SongData song : songs) {
			final String sha256 = song.getSha256();
			if (sha256 == null || seen.put(sha256, Boolean.TRUE) != null) {
				continue;
			}
			final double[] values = chartRadar(song);
			if (values == null) {
				continue;
			}
			final double rate = rates.getOrDefault(sha256, 0.0);
			final RankEntry[] best = plays.computeIfAbsent(group(song.getMode()), k -> new HashMap<>())
					.computeIfAbsent(songKey(song), k -> new RankEntry[NotesRadar.LENGTH]);
			for (int i = 0; i < best.length; i++) {
				final double value = values[i] * rate;
				if (best[i] == null || value > best[i].value()) {
					best[i] = new RankEntry(value, song.getTitle(), song.getLevel(), song.getDifficulty(), songKey(song));
				}
			}
		}

		final Map<Integer, double[]> result = new HashMap<>();
		final Map<Integer, List<List<RankEntry>>> ranking = new HashMap<>();
		for (Map.Entry<Integer, Map<String, RankEntry[]>> e : plays.entrySet()) {
			final double[] values = new double[NotesRadar.LENGTH];
			final List<List<RankEntry>> tops = new ArrayList<>(NotesRadar.LENGTH);
			for (int i = 0; i < values.length; i++) {
				final int index = i;
				final List<RankEntry> top = e.getValue().values().stream().map(entries -> entries[index])
						.sorted((a, b) -> Double.compare(b.value(), a.value())).limit(TOP).toList();
				double sum = 0;
				for (RankEntry entry : top) {
					sum += entry.value();
				}
				// 10曲未満でも10で割る
				values[i] = sum / TOP;
				tops.add(top);
			}
			result.put(e.getKey(), values);
			ranking.put(e.getKey(), tops);
		}
		return new Snapshot(result, ranking);
	}

	/**
	 * スコア率。IIDXと同じく百分率の小数点以下2桁で切り捨てる(EXスコア / (ノーツ数 x 2))
	 */
	static double scoreRate(int exscore, int notes) {
		// 整数で計算する(浮動小数点では29.00%が28.99%になることがある)
		final long basisPoints = Math.min(10000L, Math.max(0L, exscore * 10000L / (notes * 2L)));
		return basisPoints / 10000.0;
	}

	/**
	 * 楽曲の識別子。同じフォルダの譜面(差分)は同じ楽曲として扱う
	 */
	private static String songKey(SongData song) {
		if (song.getFolder() != null && !song.getFolder().isEmpty()) {
			return song.getFolder();
		}
		final String path = song.getPath();
		final Path parent = path != null ? Path.of(path).getParent() : null;
		return parent != null ? parent.toString() : song.getSha256();
	}

	/**
	 * @return 譜面のノーツレーダー値。譜面を読めない場合はnull
	 */
	private static double[] chartRadar(SongData song) {
		final double[] cached = chartRadar.get(song.getSha256());
		if (cached != null) {
			return cached.length == NotesRadar.LENGTH ? cached : null;
		}
		double[] values = new double[0];
		try {
			final String path = song.getPath();
			final BMSModel model = path != null ? PlayerResource.decodeBMSModel(Path.of(path), 0) : null;
			if (model != null) {
				values = NotesRadar.analyze(model);
			}
		} catch (Throwable e) {
			Logger.getGlobal().warning("ノーツレーダー解析失敗 : " + song.getPath() + " : " + e.getMessage());
		}
		chartRadar.put(song.getSha256(), values);
		if (values.length == NotesRadar.LENGTH) {
			appendChartRadar(song.getSha256(), values);
		}
		return values.length == NotesRadar.LENGTH ? values : null;
	}

	private static Path cacheFile() {
		return DecodedImageCache.defaultDirectory().resolveSibling("notes-radar.txt");
	}

	private static void loadChartRadar() {
		if (chartRadarLoaded) {
			return;
		}
		chartRadarLoaded = true;
		final Path file = cacheFile();
		if (!Files.isRegularFile(file)) {
			return;
		}
		try {
			final List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
			if (lines.isEmpty() || !lines.get(0).equals(CACHE_VERSION)) {
				Files.delete(file);
				return;
			}
			for (String line : lines.subList(1, lines.size())) {
				final String[] cols = line.split(" ");
				if (cols.length != NotesRadar.LENGTH + 1) {
					continue;
				}
				final double[] values = new double[NotesRadar.LENGTH];
				for (int i = 0; i < values.length; i++) {
					values[i] = Double.parseDouble(cols[i + 1]);
				}
				chartRadar.put(cols[0], values);
			}
		} catch (IOException | RuntimeException e) {
			Logger.getGlobal().warning("ノーツレーダーキャッシュ読み込み失敗 : " + e.getMessage());
		}
	}

	private static void appendChartRadar(String sha256, double[] values) {
		final Path file = cacheFile();
		try {
			Files.createDirectories(file.getParent());
			final boolean create = !Files.exists(file);
			try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8, StandardOpenOption.CREATE,
					StandardOpenOption.APPEND)) {
				if (create) {
					writer.write(CACHE_VERSION);
					writer.newLine();
				}
				final StringBuilder sb = new StringBuilder(sha256);
				for (double v : values) {
					sb.append(' ').append(v);
				}
				writer.write(sb.toString());
				writer.newLine();
			}
		} catch (IOException e) {
			Logger.getGlobal().warning("ノーツレーダーキャッシュ書き込み失敗 : " + e.getMessage());
		}
	}
}
