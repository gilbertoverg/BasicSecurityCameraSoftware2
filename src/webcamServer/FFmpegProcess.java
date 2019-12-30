package webcamServer;

import java.io.*;
import listeners.*;

public class FFmpegProcess {
	private final String[] cmdArray;
	private volatile Process process = null;
	private volatile Thread iThread = null, eThread = null;
	private volatile OutputStream outputStream = null;
	private volatile int stopAttempts = 0;
	
	private final int JPEG_BUFFER_SIZE = 5000000;
	private volatile JpegListener jpegListener = null;
	
	private final int LOG_BUFFER_SIZE = 1000;
	private volatile LogListener logListener = null;

	public FFmpegProcess(String[] cmdArray) {
		this.cmdArray = cmdArray;
	}
	
	public synchronized void setJpegListener(JpegListener jpegListener) {
		this.jpegListener = jpegListener;
	}
	
	public synchronized void setLogListener(LogListener logListener) {
		this.logListener = logListener;
	}
	
	public synchronized void start() {
		if(isRunning()) return;
		
		try {
			process = Runtime.getRuntime().exec(cmdArray);
			WebcamServer.logger.printLogLn(true, "FFmpeg process started");
			
			stopAttempts = 0;
			outputStream = process.getOutputStream();
			
			InputStream inputStream = process.getInputStream();
			iThread = new Thread() {
				public void run() {
					try {
						byte[] jpegBuffer = new byte[JPEG_BUFFER_SIZE];
						int jpegBufferCount = 0;
						
						for(int i = Utils.readByteStream(inputStream), iPrev = -1; i != -1; iPrev = i, i = Utils.readByteStream(inputStream)) {
							if(jpegBufferCount >= jpegBuffer.length) {
								WebcamServer.logger.printLogLn(false, "FFmpeg jpeg buffer overflow");
								jpegBufferCount = 0;
							}
							
							jpegBuffer[jpegBufferCount++] = (byte)i;
							if(iPrev == 0xFF && i == 0xD9) {
								if(jpegBuffer[0] == (byte)0xFF && jpegBuffer[1] == (byte)0xD8) {
									byte[] jpeg = new byte[jpegBufferCount];
									System.arraycopy(jpegBuffer, 0, jpeg, 0, jpegBufferCount);
									WebcamServer.logger.printLogLn(true, "FFmpeg new jpeg");
									try {
										if(jpegListener != null) jpegListener.newJpeg(jpeg);
									} catch (Exception e) {
										WebcamServer.logger.printLogException(e);
									}
								}
								jpegBufferCount = 0;
							}
						}
					} catch (Exception e) {
						WebcamServer.logger.printLogException(e);
					}
					
					try {
						inputStream.close();
					} catch (Exception e) {
						WebcamServer.logger.printLogException(e);
					}
					
					try {
						if(jpegListener != null) jpegListener.newJpeg(null);
					} catch (Exception e) {
						WebcamServer.logger.printLogException(e);
					}
					
					WebcamServer.logger.printLogLn(true, "FFmpeg stdout closed");
				}
			};
			iThread.setName("FFmpeg stdout reader");
			iThread.start();
			
			InputStream errorStream = process.getErrorStream();
			eThread = new Thread() {
				public void run() {
					try {
						byte[] logBuffer = new byte[LOG_BUFFER_SIZE];
						int logBufferCount = 0;
						
						for(int i = Utils.readByteStream(errorStream); i != -1; i = Utils.readByteStream(errorStream)) {
							if(logBufferCount >= logBuffer.length) {
								WebcamServer.logger.printLogLn(false, "FFmpeg log buffer overflow");
								logBufferCount = 0;
							}
							
							if(i == '\r' || i == '\n') {
								if(logBufferCount > 0) {
									String line = new String(logBuffer, 0, logBufferCount);
									WebcamServer.logger.printLogLn(true, "FFmpeg: " + line);
									try {
										if(logListener != null) logListener.newLogLine(line);
									} catch (Exception e) {
										WebcamServer.logger.printLogException(e);
									}
								}
								logBufferCount = 0;
							}
							else logBuffer[logBufferCount++] = (byte)i;
						}
					} catch (Exception e) {
						WebcamServer.logger.printLogException(e);
					}
					
					try {
						errorStream.close();
					} catch (Exception e) {
						WebcamServer.logger.printLogException(e);
					}
					
					try {
						if(logListener != null) logListener.newLogLine(null);
					} catch (Exception e) {
						WebcamServer.logger.printLogException(e);
					}
					
					WebcamServer.logger.printLogLn(true, "FFmpeg stderr closed");
				}
			};
			eThread.setName("FFmpeg stderr reader");
			eThread.start();
		} catch (Exception e) {
			WebcamServer.logger.printLogException(e);
		}
	}
	
	public synchronized void stop() {
		if(!isRunning()) return;
		
		boolean destroy = true;
		if(stopAttempts < 3) {
			stopAttempts++;
			
			try {
				outputStream.write('q');
				outputStream.flush();
				destroy = false;
			} catch (Exception e) {
				
			}
		}
		
		if(destroy) {
			WebcamServer.logger.printLogLn(true, "FFmpeg forcibly terminating");
			
			try {
				process.destroy();
			} catch (Exception e) {
				WebcamServer.logger.printLogException(e);
			}
		}
	}
	
	public synchronized boolean isRunning() {
		if(process != null && process.isAlive()) return true;
		if(iThread != null && iThread.isAlive()) return true;
		if(eThread != null && eThread.isAlive()) return true;
		return false;
	}
	
	public synchronized void waitStop() {
		long start = System.nanoTime();
		
		while(isRunning()) {
			if(System.nanoTime() - start > 2000000000L) {
				stop();
				start = System.nanoTime();
				
			}
			
			try {
				Thread.sleep(1);
			} catch (InterruptedException e) {

			}
		}
	}
}
