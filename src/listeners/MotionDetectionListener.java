package listeners;

public interface MotionDetectionListener {
	void newMotionLevel(double motion);
	void resetMotion();
}
