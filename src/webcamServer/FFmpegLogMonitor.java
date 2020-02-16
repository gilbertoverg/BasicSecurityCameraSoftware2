package webcamServer;

import listeners.*;

public class FFmpegLogMonitor {
	private final long timeout, dupCounterThreshold;
	private final double dupThreshold;
	
	private volatile long lastLogTime;
	private volatile long lastFrame, lastDup, dupCounter;
	private volatile String currentFile = null;
	
	private volatile NewTmpFileListener newTmpFileListener = null;
	private volatile MotionDetectionListener motionDetectionListener = null;

	public FFmpegLogMonitor(long timeoutMilliseconds, double dupThreshold, long dupCounterThreshold) {
		this.timeout = timeoutMilliseconds * 1000000L;
		this.dupThreshold = dupThreshold;
		this.dupCounterThreshold = dupCounterThreshold;
		
		reset();
	}
	
	public synchronized void setNewTmpFileListener(NewTmpFileListener newTmpFileListener) {
		this.newTmpFileListener = newTmpFileListener;
	}
	
	public synchronized void setMotionDetectionListener(MotionDetectionListener motionDetectionListener) {
		this.motionDetectionListener = motionDetectionListener;
	}
	
	public synchronized void reset() {
		lastLogTime = System.nanoTime();
		lastFrame = 0;
		lastDup = 0;
		dupCounter = 0;
		currentFile = null;
	}
	
	public synchronized void update(String line) {
		try {
			if(line == null) return;
			
			int frameIndex = line.indexOf("frame=");
			if(frameIndex >= 0) {
				long frame = readNumber(line, frameIndex + 6);
				if(frame - lastFrame > 0) lastLogTime = System.nanoTime();
				int dupIndex = line.indexOf("dup=");
				if(dupIndex >= 0) {
					long dup = readNumber(line, dupIndex + 4);
					if(frame - lastFrame > 0) {
						double dupFramePercent = (double)(dup - lastDup) / (double)(frame - lastFrame);
						if(dupFramePercent > dupThreshold) dupCounter++;
						else dupCounter = 0;
					}
					lastDup = dup;
				}
				lastFrame = frame;
			}
			
			if(line.contains("Opening") && line.contains("for writing")) {
				int ind = line.lastIndexOf("TMP_");
				if(ind > 0) {
					currentFile = line.substring(ind, line.indexOf(".mp4", ind) + 4);
					WebcamServer.logger.printLogLn(true, "Now writing: " + currentFile);
					if(newTmpFileListener != null) newTmpFileListener.newTmpFile(currentFile);
				}
			}
			
			int motionDetectionIndex = line.indexOf("scene_score=");
			if(motionDetectionIndex >= 0) {
				double motion = readNumberReal(line, motionDetectionIndex + 12);
				WebcamServer.logger.printLogLn(true, "Current motion level: " + motion);
				if(motionDetectionListener != null) motionDetectionListener.newMotionLevel(motion);
			}
		} catch (Exception e) {
			WebcamServer.logger.printLogException(e);
		}
	}
	
	public synchronized String getCurrentFile() {
		return currentFile;
	}
	
	public synchronized boolean isAlive() {
		return System.nanoTime() - lastLogTime < timeout;
	}
	
	public synchronized boolean isStarted() {
		return lastFrame > 0;
	}
	
	public synchronized boolean isDuplicatingFrames() {
		return dupCounter > dupCounterThreshold;
	}
	
	private long readNumber(String line, int begin) {
		try {
			while(begin < line.length() && (line.charAt(begin) < '0' || line.charAt(begin) > '9')) begin++;
			int end = begin;
			while(end < line.length() && line.charAt(end) >= '0' && line.charAt(end) <= '9') end++;
			return Long.parseLong(line.substring(begin, end));
		} catch (Exception e) {
			WebcamServer.logger.printLogException(e);
		}
		
		return 0;
	}
	
	private double readNumberReal(String line, int begin) {
		try {
			while(begin < line.length() && (line.charAt(begin) != '.' && (line.charAt(begin) < '0' || line.charAt(begin) > '9'))) begin++;
			int end = begin;
			while(end < line.length() && (line.charAt(end) == '.' || (line.charAt(end) >= '0' && line.charAt(end) <= '9'))) end++;
			return Double.parseDouble(line.substring(begin, end));
		} catch (Exception e) {
			WebcamServer.logger.printLogException(e);
		}
		
		return 0;
	}
}
