package webcamServer;

public class FileInfo {
	private double durationSeconds = 0;
	private double fps = 0;

	public FileInfo(double durationSeconds, double fps) {
		this.durationSeconds = durationSeconds;
		this.fps = fps;
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

	public String toString() {
		return "[duration:" + String.format("%.5f", durationSeconds) + ", fps:" + String.format("%.5f", fps) + "]";
	}
}
