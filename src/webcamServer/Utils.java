package webcamServer;

import java.io.*;
import java.nio.file.*;

public class Utils {
	public static int readByteStream(InputStream is) {
		try {
			return is.read();
		} catch (IOException e) {
			
		} catch (Exception e) {
			WebcamServer.logger.printLogException(e);
		}
		
		return -1;
	}
	
	public static String readFile(File file) {
		try {
			Path path = file.toPath();
			byte[] content = Files.readAllBytes(path);
			return new String(content);
		} catch (Exception e) {
			
		}
		
		return null;
	}
}
