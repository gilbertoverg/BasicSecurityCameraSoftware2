package webcamServer;

public class FileInfo {
	private double durationSeconds = 0;
	private double fps = 0;
	private int width = 0;
	private int height = 0;
	private boolean empty = true;

	public FileInfo(double durationSeconds, double fps, int width, int height, boolean empty) {
		this.durationSeconds = durationSeconds;
		this.fps = fps;
		this.width = width;
		this.height = height;
		this.empty = empty;
	}
	
	public FileInfo() {
		
	}

	public synchronized double getDurationSeconds() {
		return durationSeconds;
	}

	public synchronized void setDurationSeconds(double durationSeconds) {
		this.durationSeconds = durationSeconds;
	}
	
	public synchronized double getFps() {
		return fps;
	}

	public synchronized void setFps(double fps) {
		this.fps = fps;
	}

	public synchronized int getWidth() {
		return width;
	}

	public synchronized void setWidth(int width) {
		this.width = width;
	}

	public synchronized int getHeight() {
		return height;
	}

	public synchronized void setHeight(int height) {
		this.height = height;
	}

	public synchronized boolean isEmpty() {
		return empty;
	}

	public synchronized void setEmpty(boolean empty) {
		this.empty = empty;
	}

	public synchronized String toString() {
		if(empty) return "[empty]";
		return "[duration:" + String.format("%.5f", durationSeconds) + ", fps:" + String.format("%.5f", fps) + ", width:" + width + ", height:" + height + "]";
	}
}
