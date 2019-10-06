package webcamServer;

import java.io.*;
import listeners.*;

public class SystemMonitor implements NewFileListener {
	private final long fileGenerationPeriod;
	private final FFmpegWebcamReader ffmpegWebcamReader;
	
	private volatile Thread thread = null;
	private volatile boolean killThread = false;
	
	private volatile long lastTimeFileGenerated, millisDisabledFileGenerationCheck;
	
	public SystemMonitor(long fileGenerationPeriod, FFmpegWebcamReader ffmpegWebcamReader) {
		this.fileGenerationPeriod = fileGenerationPeriod * 1000000000L;
		this.ffmpegWebcamReader = ffmpegWebcamReader;
	}
	
	@Override
	public synchronized void newFile(File file) {
		lastTimeFileGenerated = System.nanoTime();
	}

	public synchronized void start() {
		if(isRunning()) return;
		
		try {
			lastTimeFileGenerated = System.nanoTime();
			millisDisabledFileGenerationCheck = 0;
			
			killThread = false;
			thread = new Thread() {
				public void run() {
					WebcamServer.logger.printLogLn(false, "System monitor started");
					
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
							
							if(millisDifference > 500L || millisDifference < -500L) {
								WebcamServer.logger.printLogLn(false, "Time change detected (" + millisDifference + "ms difference)");
								if(fileGenerationPeriod > 0) {
									millisDisabledFileGenerationCheck = Math.abs(millisDifference * 2);
									WebcamServer.logger.printLogLn(false, "File generation check disabled");
								}
							}
							else if(millisDisabledFileGenerationCheck > 0) {
								if(millisDisabledFileGenerationCheck > 250) millisDisabledFileGenerationCheck -= 250;
								else millisDisabledFileGenerationCheck = 0;

								if(millisDisabledFileGenerationCheck == 0) WebcamServer.logger.printLogLn(false, "File generation check re-enabled");
							}
							
							if(fileGenerationPeriod > 0 && millisDisabledFileGenerationCheck == 0) {
								if(nanoTime - lastTimeFileGenerated > fileGenerationPeriod * 2) {
									WebcamServer.logger.printLogLn(false, "FFmpeg is not generating video files");
									ffmpegWebcamReader.restartFFmpeg();
									lastTimeFileGenerated = nanoTime;
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
