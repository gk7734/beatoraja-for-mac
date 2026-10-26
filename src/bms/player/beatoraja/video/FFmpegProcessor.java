package bms.player.beatoraja.video;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber.Exception;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.GdxRuntimeException;

import bms.player.beatoraja.song.SongResource;
import bms.player.beatoraja.song.SongResources;

/**
 * ffmpegを使用した動画表示用クラス
 *
 * @author exch
 */
public class FFmpegProcessor implements VideoProcessor {
	private enum ProcessorStatus {
		TEXTURE_INACTIVE,
		TEXTURE_ACTIVE,
		DISPOSED,
	}

	/**
	 * 現在表示中のフレームのTexture
	 */
	private Texture showingtex;
	/**
	 * 動画のフレーム表示率(1/n)
	 */
	private int fpsd = 1;
	/**
	 * 動画再生用スレッド
	 */
	private MovieSeekThread movieseek;

	private long time;
	/**
	 * dispose()を呼び出した後にprocessorDisposedはtrueになる
	 */
	private ProcessorStatus processorStatus = ProcessorStatus.TEXTURE_INACTIVE;

	/**
	 * FFmpeg's natives, loaded once on a thread nobody interrupts. MovieSeekThread is interrupted for every command,
	 * and an interrupt while JavaCPP first unpacks the libraries (a new install, or jars that changed) fails their
	 * class initialization for the rest of the session, so no movie or BGA would play.
	 */
	private static final CompletableFuture<Void> NATIVES = CompletableFuture.runAsync(() -> {
		try {
			FFmpegFrameGrabber.tryLoad();
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	});

	public FFmpegProcessor(int fpsd) {
		this.fpsd = fpsd;
	}

	public void create(String filepath) {
		create(SongResources.fromPath(java.nio.file.Path.of(filepath)));
	}

	/** Starts decoding from a resource without extracting its containing archive. */
	public void create(SongResource resource) {
		movieseek = new MovieSeekThread(resource);
		movieseek.start();
	}

	@Override
	public Texture getFrame(long time) {
		this.time = time;
		if (processorStatus == ProcessorStatus.TEXTURE_ACTIVE) {
			return showingtex;
		} else {
			return null;
		}
	}
	
	public void play(long time, boolean loop) {
		if (processorStatus == ProcessorStatus.DISPOSED) return;
		this.time = time;
		movieseek.exec(loop ? Command.LOOP : Command.PLAY);
	}

	public void stop() {
		if (processorStatus == ProcessorStatus.DISPOSED) return;
		movieseek.exec(Command.STOP);
	}

	@Override
	public void dispose() {
		processorStatus = ProcessorStatus.DISPOSED;
		if (movieseek != null) {
			movieseek.exec(Command.HALT);
			movieseek = null;
		}

		if (showingtex != null) {
			showingtex.dispose();
		}
	}

	/**
	 * 動画再生用スレッド
	 *
	 * @author exch
	 */
	class MovieSeekThread extends Thread {
		/**
		 * FFmpegFrameGrabber::setVideoFrameNumber
		 * 1.4.1以前のJavaCVには存在しない
		 */
		private static final Method setVideoFrameNumber;
		static {
			Method method = null;
			try {
				method = FFmpegFrameGrabber.class.getMethod("setVideoFrameNumber", int.class);
			} catch (NoSuchMethodException | SecurityException ignored) {}
			setVideoFrameNumber = method;
		}

		/**
		 * ffmpegアクセサ
		 */
		private FFmpegFrameGrabber grabber;
		/**
		 * コマンドキュー
		 */
		private LinkedBlockingDeque<Command> commands = new LinkedBlockingDeque<>(4);

		private boolean eof = true;

		private Pixmap pixmap;
		private byte[] movieBytes;
		private final Object pixmapLock = new Object();
		/** At most one texture upload is queued; later frames reuse it instead of queueing redundant uploads. */
		private final AtomicBoolean uploadPending = new AtomicBoolean();
		private final Runnable upload = this::uploadPixmap;

		private final SongResource resource;
		
		private long offset;
		private long framecount;

		public MovieSeekThread(SongResource resource) {
			this.resource = resource;
		}

		public void run() {
			try {
				try (InputStream input = resource.openStream()) {
					movieBytes = input.readAllBytes();
				}
				openGrabber();
				Logger.getGlobal()
						.info("movie decode - fps : " + grabber.getFrameRate() + " format : " + grabber.getFormat()
								+ " size : " + grabber.getImageWidth() + " x " + grabber.getImageHeight()
								+ " length (frame / time) : " + grabber.getLengthInFrames() + " / "
								+ grabber.getLengthInTime() + " memory : " + movieBytes.length + " bytes");

				offset = grabber.getTimestamp();
				Frame frame = null;
				boolean halt = false;
				boolean loop = false;
				while (!halt) {
					final long microtime = time * 1000 + offset;
					if (eof) {
						if (processorStatus != ProcessorStatus.DISPOSED) {
							processorStatus = ProcessorStatus.TEXTURE_INACTIVE;
						}
						try {
							sleep(3600000);
						} catch (InterruptedException e) {

						}
					} else if (microtime >= grabber.getTimestamp()) {
						while (microtime >= grabber.getTimestamp() || framecount % fpsd != 0) {
							frame = grabber.grabImage();
							if (frame == null) {
								break;
							}
							framecount++;
							// System.out.println("time : " + grabber.getTimestamp() + " --- " + time);
						}
						if (frame == null) {
							eof = true;
							if (loop) {
								commands.offerLast(Command.LOOP);
							}
						} else if (frame.image != null && frame.image[0] != null) {
							try {
								synchronized (pixmapLock) {
									if (pixmap == null || pixmap.getWidth() != frame.imageWidth || pixmap.getHeight() != frame.imageHeight) {
										if (pixmap != null) {
											pixmap.dispose();
										}
										// RGBA matches Metal's native layout; RGB888 forces a driver-side conversion on every upload.
										pixmap = new Pixmap(frame.imageWidth, frame.imageHeight, Pixmap.Format.RGBA8888);
									}
									copyFrameToPixmap(frame, pixmap);
								}
								if (uploadPending.compareAndSet(false, true)) {
									Gdx.app.postRunnable(upload);
								}
								// System.out.println("movie pixmap created : " + time);
							} catch (Throwable e) {
								throw new GdxRuntimeException("Couldn't load pixmap from image data", e);
							}
						}
					} else {
						// Always yield at least 1ms: waiting on a sub-millisecond gap used to spin this thread.
						final long sleeptime = Math.max(1, (grabber.getTimestamp() - microtime) / 1000 - 1);
						try {
							sleep(sleeptime);
						} catch (InterruptedException e) {

						}
					}

					if (!commands.isEmpty()) {
						switch (commands.pollFirst()) {
							case PLAY -> {
								loop = false;
								restart();
							}
							case LOOP -> {
								loop = true;
								restart();
							}
							case STOP -> eof = true;
							case HALT -> halt = true;
						}
					}
				}
			} catch (Throwable e) {
				e.printStackTrace();
			} finally {
				try {
					synchronized (pixmapLock) {
						if (pixmap != null) {
							pixmap.dispose();
							pixmap = null;
						}
					}
					closeGrabber();
					Logger.getGlobal().info("動画リソースの開放 : " + resource.displayPath());
				} catch (Throwable e) {
					e.printStackTrace();
				}
			}
		}

		private void openGrabber() throws Exception {
			// join() waits without reacting to this thread's interrupts.
			NATIVES.join();
			grabber = new FFmpegFrameGrabber(new ByteArrayInputStream(movieBytes));
			// BGRA keeps the channel order the ffmpeg shader expects and lets frames be copied with one bulk put.
			grabber.setPixelFormat(avutil.AV_PIX_FMT_BGRA);
			grabber.start();
			while (grabber.getVideoBitrate() < 10) {
				final int videoStream = grabber.getVideoStream();
				try {
					if (videoStream < 5) {
						grabber.setVideoStream(videoStream + 1);
						grabber.restart();
					} else {
						grabber.setVideoStream(-1);
						grabber.restart();
						break;
					}
				} catch (Throwable e) {
					e.printStackTrace();
				}
			}
		}

		private void reopenGrabber() throws Exception {
			closeGrabber();
			openGrabber();
		}

		private void closeGrabber() throws Exception {
			if (grabber != null) {
				try {
					grabber.stop();
				} finally {
					grabber.close();
					grabber = null;
				}
			}
		}
		
		private void restart() throws Exception {
			if (setVideoFrameNumber != null) {
				try {
					setVideoFrameNumber.invoke(grabber, 0);
				} catch (IllegalAccessException | InvocationTargetException e) {
					reopenGrabber();
				}
			} else {
				try {
					grabber.restart();
					grabber.grabImage();
				} catch (Throwable e) {
					reopenGrabber();
				}
			}
			eof = false;
			offset = grabber.getTimestamp() - time * 1000;
			framecount = 1;
			// System.out.println("movie restart - starttime : " + start);
		}

		private void uploadPixmap() {
			uploadPending.set(false);
			synchronized (pixmapLock) {
				final Pixmap p = pixmap;
				// dispose()を呼び出した後にshowingtexを使えばEXCEPTION_ACCESS_VIOLATIONが発生
				if (p == null || processorStatus == ProcessorStatus.DISPOSED) {
					return;
				}
				preparePixmapForDraw(p);
				if (showingtex != null && showingtex.getWidth() == p.getWidth() && showingtex.getHeight() == p.getHeight()) {
					showingtex.draw(p, 0, 0);
				} else {
					if (showingtex != null) {
						showingtex.dispose();
					}
					showingtex = new Texture(p);
				}
				processorStatus = ProcessorStatus.TEXTURE_ACTIVE;
			}
		}

		private void copyFrameToPixmap(Frame frame, Pixmap pixmap) {
			final ByteBuffer source = ((ByteBuffer) frame.image[0]).duplicate();
			final ByteBuffer pixels = pixmap.getPixels();
			final int sourceChannels = frame.imageChannels > 0 ? frame.imageChannels : 4;
			final int sourceStride = frame.imageStride > 0 ? frame.imageStride : frame.imageWidth * sourceChannels;
			final int targetRowBytes = frame.imageWidth * 4;

			pixels.clear();
			if (sourceChannels == 4 && sourceStride == targetRowBytes) {
				source.position(0).limit(targetRowBytes * frame.imageHeight);
				pixels.put(source);
			} else if (sourceChannels == 4) {
				for (int y = 0; y < frame.imageHeight; y++) {
					source.limit(y * sourceStride + targetRowBytes).position(y * sourceStride);
					pixels.put(source);
				}
			} else {
				// Fallback for decoders that ignore the requested pixel format.
				for (int y = 0; y < frame.imageHeight; y++) {
					for (int x = 0; x < frame.imageWidth; x++) {
						final int sourceIndex = y * sourceStride + x * sourceChannels;
						pixels.put(source.get(sourceIndex)).put(source.get(sourceIndex + 1)).put(source.get(sourceIndex + 2)).put((byte) -1);
					}
				}
			}
			pixels.flip();
		}

		private void preparePixmapForDraw(Pixmap pixmap) {
			ByteBuffer pixels = pixmap.getPixels();
			pixels.position(0);
			pixels.limit(pixmap.getWidth() * pixmap.getHeight() * 4);
		}

		public void exec(Command com) {
			commands.offerLast(com);
			interrupt();
		}
	}

	enum Command {
		PLAY, LOOP, STOP, HALT;
	}
	
	public interface TimerObserver {
		
		public long getMicroTime();
	}
}
