package bms.player.beatoraja;

/**
 * 解像度
 * 
 * @author exch
 */
public enum Resolution {
	SD(640, 480),
	SVGA(800, 600),
	XGA(1024, 768),
	HD(1280, 720),
	WXGA(1280, 800),
	QUADVGA(1280, 960),
	FWXGA(1366, 768),
	WXGAPLUS(1440, 900),
	MACBOOK_AIR_13(1470, 956),
	MACBOOK_PRO_14(1512, 982),
	SXGAPLUS(1400, 1050),
	HDPLUS(1600, 900),
	UXGA(1600, 1200),
	WSXGAPLUS(1680,1050),
	MACBOOK_AIR_15(1710, 1112),
	MACBOOK_PRO_16(1728, 1117),
	FULLHD(1920, 1080),
	WUXGA(1920, 1200),
	QXGA(2048, 1536),
	UWFHD(2560, 1080),
	WQHD(2560, 1440),
	WQXGA(2560, 1600),
	UWQHD(3440, 1440),
	ULTRAHD(3840, 2160);

	/**
	 * 幅
	 */
	public final int width;
	/**
	 * 高さ
	 */
	public final int height;

	private Resolution(int width, int height) {
		this.width = width;
		this.height = height;
	}

	@Override
	public String toString() {
		return name() + " (" + width + " x " + height + ")";
	}
}
