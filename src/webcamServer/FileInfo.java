package webcamServer;

public class FileInfo {
	private double durationSeconds = 0;
	private double fps = 0;
	private int width = 0;
	private int height = 0;

	public FileInfo(double durationSeconds, double fps, int width, int height) {
		this.durationSeconds = durationSeconds;
		this.fps = fps;
		this.width = width;
		this.height = height;
	}
	
	public FileInfo() {
		
	}

	public double getDurationSeconds() {
		return durationSeconds;
	}

	public void setDurationSeconds(double durationSeconds) {
		this.durationSeconds = durationSeconds;
	}
	
	public double getFps() {
		return fps;
	}

	public void setFps(double fps) {
		this.fps = fps;
	}

	public int getWidth() {
		return width;
	}

	public void setWidth(int width) {
		this.width = width;
	}

	public int getHeight() {
		return height;
	}

	public void setHeight(int height) {
		this.height = height;
	}

	public String toString() {
		return "[duration:" + String.format("%.5f", durationSeconds) + ", fps:" + String.format("%.5f", fps) + ", width:" + width + ", height:" + height + "]";
	}
}
