package bms.player.beatoraja.skin;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

import bms.player.beatoraja.MainState;
import bms.player.beatoraja.skin.Skin.SkinObjectRenderer;
import bms.player.beatoraja.song.NotesRadar;
import bms.player.beatoraja.song.NotesRadarAccessor;
import bms.player.beatoraja.song.PlayerNotesRadar;
import bms.player.beatoraja.song.SongData;

/**
 * ノーツレーダーのグラフ。対象譜面の6項目(NOTES, PEAK, SCRATCH, SOF-LAN, CHARGE, CHORD)を頂点とする多角形を、
 * IIDXと同じく値100を六角形の枠、それを超える値は枠の外にはみ出す形で描画する(枠はスキンの画像)。
 * 塗り色は譜面の難易度ごとに指定できる(IIDXはHYPERが黄、ANOTHERが赤)。
 * 解析中は何も描画しない。プレイヤーのノーツレーダー(PLAYER NOTES RADAR)も同じ形で描画できる
 */
public class SkinNotesRadarGraph extends SkinObject {

	private static final int SIZE = 320;
	private static final int CENTER = SIZE / 2;
	/** 値EDGE_VALUEの点の中心からの距離。これを超える値(最大200)は枠の外まではみ出す */
	private static final double RADIUS = 78.0;
	private static final double EDGE_VALUE = 100.0;

	private final Color fill;
	/** 譜面の難易度(0-5)ごとの塗り色。空の場合はfillのみ */
	private final Color[] difficultyFills;
	/** trueの場合はプレイヤーのノーツレーダー */
	private final boolean player;

	private MainState state;
	private Pixmap pixmap;
	private Texture texture;
	private TextureRegion image;
	/** 描画済みの譜面のキー。目盛りのみの場合は空文字 */
	private String drawn;

	public SkinNotesRadarGraph(String color) {
		this(color, null, false);
	}

	/**
	 * @param color 塗り色
	 * @param difficultyColors 譜面の難易度(0: 不明, 1: BEGINNER ... 5: LEGGENDARIA)ごとの塗り色。IIDXと同じく
	 *            難易度で色を変える場合に指定する。不足分と不正な値はcolorを使う
	 * @param player trueの場合はプレイヤーのノーツレーダー
	 */
	public SkinNotesRadarGraph(String color, String[] difficultyColors, boolean player) {
		this.player = player;
		fill = toFill(color, Color.valueOf("e8402a"));
		difficultyFills = new Color[difficultyColors != null ? Math.min(difficultyColors.length, 6) : 0];
		for (int i = 0; i < difficultyFills.length; i++) {
			difficultyFills[i] = toFill(difficultyColors[i], fill);
		}
	}

	private static Color toFill(String color, Color fallback) {
		if (color == null || !color.matches("[0-9a-fA-F]{6}([0-9a-fA-F]{2})?")) {
			return fallback;
		}
		final Color tint = Color.valueOf(color);
		return new Color(tint.r, tint.g, tint.b, 0.28f);
	}

	@Override
	public void prepare(long time, MainState state) {
		this.state = state;
		super.prepare(time, state);
	}

	@Override
	public void draw(SkinObjectRenderer sprite) {
		final NotesRadarAccessor.Result radar = player ? PlayerNotesRadar.get(state) : NotesRadarAccessor.get(state);
		final Color color = fillColor();
		final String want = radar.isReady() ? radar.key() + "#" + color : "";
		if (texture == null) {
			pixmap = new Pixmap(SIZE, SIZE, Pixmap.Format.RGBA8888);
			texture = new Texture(pixmap);
			texture.setFilter(TextureFilter.Linear, TextureFilter.Linear);
			image = new TextureRegion(texture);
			drawn = null;
		}
		if (!want.equals(drawn)) {
			paint(radar.isReady() ? radar.values() : null, color);
			texture.draw(pixmap, 0, 0);
			drawn = want;
		}
		draw(sprite, image);
	}

	/** 対象譜面の難易度の色 */
	private Color fillColor() {
		if (player || difficultyFills.length == 0 || state.resource == null) {
			return fill;
		}
		final SongData song = state.resource.getSongdata();
		final int difficulty = song != null ? song.getDifficulty() : 0;
		return difficulty >= 0 && difficulty < difficultyFills.length ? difficultyFills[difficulty] : fill;
	}

	private void paint(double[] values, Color color) {
		pixmap.setBlending(Pixmap.Blending.None);
		pixmap.setColor(0);
		pixmap.fill();
		if (values == null) {
			return;
		}
		// 隣り合う三角形は辺で重なるため、合成せず同じ色で上書きする(重なりが濃くならない)
		final int[] xs = new int[6];
		final int[] ys = new int[6];
		for (int i = 0; i < 6; i++) {
			final double d = Math.max(0.0, Math.min(NotesRadar.MAX_VALUE / EDGE_VALUE, values[i] / EDGE_VALUE));
			xs[i] = x(i, d);
			ys[i] = y(i, d);
		}
		pixmap.setColor(color);
		for (int i = 0; i < 6; i++) {
			final int j = (i + 1) % 6;
			pixmap.fillTriangle(CENTER, CENTER, xs[i], ys[i], xs[j], ys[j]);
		}
	}

	/** 項目nの軸上、中心からの割合dの点 (項目0が真上、時計回り) */
	private static int x(int n, double d) {
		return (int) Math.floor(CENTER + Math.sin(n * Math.PI / 3) * RADIUS * d + 0.5);
	}

	private static int y(int n, double d) {
		return (int) Math.floor(CENTER - Math.cos(n * Math.PI / 3) * RADIUS * d + 0.5);
	}

	@Override
	public void dispose() {
		if (texture != null) {
			texture.dispose();
			texture = null;
		}
		if (pixmap != null) {
			pixmap.dispose();
			pixmap = null;
		}
	}
}
