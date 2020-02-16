package webcamServer;

import java.io.*;
import listeners.*;

public class SystemMonitor implements NewFileListener, JpegListener, StatListener {
	private final long jpegGenerationPeriod, fileGenerationPeriod;
	private final FFmpegWebcamReader ffmpegWebcamReader;
	
	private volatile Thread thread = null;
	private volatile boolean killThread = false;
	
	private volatile long lastTimeJpegGenerated, lastTimeFileGenerated, millisDisableLog, millisDisableJpegCheck, millisDisableFileCheck;
	private volatile Object statLock = new Object();
	
	public SystemMonitor(long jpegGenerationFrequency, long fileGenerationPeriod, FFmpegWebcamReader ffmpegWebcamReader) {
		if(jpegGenerationFrequency <= 0) this.jpegGenerationPeriod = 0;
		else this.jpegGenerationPeriod = 1000000000L / jpegGenerationFrequency;
		this.fileGenerationPeriod = fileGenerationPeriod * 1000000000L;
		this.ffmpegWebcamReader = ffmpegWebcamReader;
	}
	
	@Override
	public synchronized void newJpeg(byte[] jpeg) {
		synchronized (statLock) {
			lastTimeJpegGenerated = System.nanoTime();
		}
	}
	
	@Override
	public synchronized void newFile(File file) {
		synchronized (statLock) {
			lastTimeFileGenerated = System.nanoTime();
		}
	}
	
	@Override
	public synchronized void resetStats() {
		synchronized (statLock) {
			lastTimeJpegGenerated = System.nanoTime();
			lastTimeFileGenerated = System.nanoTime();
			millisDisableLog = 30000;
			millisDisableJpegCheck = 30000;
			millisDisableFileCheck = 30000 + fileGenerationPeriod / 1000000L * 2L;
		}
	}

	public synchronized void start() {
		if(isRunning()) return;
		
		try {
			killThread = false;
			thread = new Thread() {
				public void run() {
					WebcamServer.logger.printLogLn(false, "System monitor started");
					resetStats();
					long oldNanoTime = System.nanoTime();
					long oldMillis = System.currentTimeMillis();
					while(!killThread) {
						try {
							Thread.sleep(250);
						} catch (Exception e) {
							WebcamServer.logger.printLogException(e);
						}
						
						try {
							long nanoTime = System.nanoTime();
							long millis = System.currentTimeMillis();
							
							long nanoTimeDifference = (nanoTime - oldNanoTime - 250000000L) / 1000000L;
							oldNanoTime = nanoTime;
							
							long millisDifference = millis - oldMillis - 250;
							oldMillis = millis;
							
							if(nanoTimeDifference > 250L || nanoTimeDifference < -250L) WebcamServer.logger.printLogLn(false, "System hiccup detected (" + nanoTimeDifference + "ms difference)");
							
							synchronized (statLock) {
								if(millisDifference > 500L || millisDifference < -500L) {
									WebcamServer.logger.printLogLn(false, "Time change detected (" + millisDifference + "ms difference)");
									
									millisDisableLog += 30000 + Math.abs(millisDifference * 2);
									ffmpegWebcamReader.enableFFmpegLog(false);
									
									millisDisableFileCheck += 30000 + fileGenerationPeriod / 1000000L * 2L;
								}
								else {
									if(millisDisableLog > 0) {
										if(millisDisableLog > 250) millisDisableLog -= 250;
										else millisDisableLog = 0;
										if(millisDisableLog <= 0 && !ffmpegWebcamReader.isFFmpegLogEnabled()) {
											WebcamServer.logger.printLogLn(false, "FFmpeg log monitor enabled");
											ffmpegWebcamReader.enableFFmpegLog(true);
										}
									}
									
									if(millisDisableJpegCheck > 0) {
										if(millisDisableJpegCheck > 250) millisDisableJpegCheck -= 250;
										else millisDisableJpegCheck = 0;
										if(millisDisableJpegCheck <= 0 && jpegGenerationPeriod > 0) WebcamServer.logger.printLogLn(false, "FFmpeg jpeg monitor enabled");
									}
									if(millisDisableJpegCheck <= 0 && jpegGenerationPeriod > 0 && nanoTime - lastTimeJpegGenerated > jpegGenerationPeriod * 30) {
										WebcamServer.logger.printLogLn(false, "FFmpeg is not generating jpeg frames");
										ffmpegWebcamReader.restartFFmpeg();
										resetStats();
									}
									
									if(millisDisableFileCheck > 0) {
										if(millisDisableFileCheck > 250) millisDisableFileCheck -= 250;
										else millisDisableFileCheck = 0;
										if(millisDisableFileCheck <= 0 && fileGenerationPeriod > 0) WebcamServer.logger.printLogLn(false, "FFmpeg file monitor enabled");
									}
									if(millisDisableFileCheck <= 0 && fileGenerationPeriod > 0 && nanoTime - lastTimeFileGenerated > fileGenerationPeriod * 2) {
										WebcamServer.logger.printLogLn(false, "FFmpeg is not generating video files");
										ffmpegWebcamReader.restartFFmpeg();
										resetStats();
									}
								}
							}
						} catch (Exception e) {
							WebcamServer.logger.printLogException(e);
						}
					}
					
					WebcamServer.logger.printLogLn(false, "System monitor stopped");
				}
			};
			thread.setName("System monitor");
			thread.start();
		} catch (Exception e) {
			WebcamServer.logger.printLogException(e);
		}
	}
	
	public synchronized void stop() {
		killThread = true;
	}
	
	public synchronized boolean isRunning() {
		if(thread != null && thread.isAlive()) return true;
		return false;
	}
	
	public synchronized void waitStop() {
		while(isRunning()) {
			try {
				Thread.sleep(1);
			} catch (InterruptedException e) {

			}
		}
	}
}
