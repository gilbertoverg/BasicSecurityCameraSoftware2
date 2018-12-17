package webcamServer;

import java.time.*;
import java.time.format.*;

public class Logger {
	private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
	private boolean debug = false;
	
	public synchronized void setDebug(boolean debug) {
		this.debug = debug;
	}
	
	public synchronized void printLogThrowable(Throwable e) {
		try {
			String trace = "[" + LocalDateTime.now().format(formatter) + "] Throwable: " + e + "\r\n";
			for (StackTraceElement e1 : e.getStackTrace()) trace += "\tat " + e1.toString() + "\r\n";
			System.err.print(trace);
		} catch (Exception ex) {
			
		}
	}
	
	public synchronized void printLogException(Exception e) {
		try {
			String trace = "[" + LocalDateTime.now().format(formatter) + "] Exception: " + e + "\r\n";
			for (StackTraceElement e1 : e.getStackTrace()) trace += "\tat " + e1.toString() + "\r\n";
			System.err.print(trace);
		} catch (Exception ex) {

		}
	}
	
	public synchronized void printLogError(Error e) {
		try {
			String trace = "[" + LocalDateTime.now().format(formatter) + "] Error: " + e + "\r\n";
			for (StackTraceElement e1 : e.getStackTrace()) trace += "\tat " + e1.toString() + "\r\n";
			System.err.print(trace);
		} catch (Exception ex) {
			
		}
	}
	
	public synchronized void printLog(boolean debugMessage, String txt) {
		try {
			if(debugMessage && !debug) return;
			String s = "[" + LocalDateTime.now().format(formatter) + "] " + txt;
			System.out.print(s);
		} catch (Exception ex) {
			
		}
	}
	
	public synchronized void printLogLn(boolean debugMessage, String txt) {
		try {
			if(debugMessage && !debug) return;
			String s = "[" + LocalDateTime.now().format(formatter) + "] " + txt + "\r\n";
			System.out.print(s);
		} catch (Exception ex) {
			
		}
	}
}
