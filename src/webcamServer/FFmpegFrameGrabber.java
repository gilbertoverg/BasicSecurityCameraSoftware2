package webcamServer;

import java.io.*;

public class FFmpegFrameGrabber {
	private final File ffmpeg;
	private final int jpegQuality;
	
	private final int JPEG_INITIAL_BUFFER_SIZE = 500000;
	
	public FFmpegFrameGrabber(File ffmpeg, int jpegQuality) {
		if(ffmpeg == null || !ffmpeg.exists()) throw new IllegalArgumentException("FFmpeg executable not found");
		if(jpegQuality < 2 || jpegQuality > 31) throw new IllegalArgumentException("Jpeg quality out of range");
		this.ffmpeg = ffmpeg;
		this.jpegQuality = jpegQuality;
	}

	public byte[] getFrameFromFile(File file, double time) {
		try {
			String[] cmdArray = new String[] { ffmpeg.getAbsolutePath(), "-hide_banner", "-ss", String.format("%.5f", time), "-i", file.getAbsolutePath(),
					"-vframes", "1", "-qscale:v", Integer.toString(jpegQuality), "-f", "mjpeg", "pipe:1" };
			Process process = Runtime.getRuntime().exec(cmdArray);
			
			Thread eThread = new Thread() {
				public void run() {
					try {
						String line = null;
						BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
						
						try {
							while((line = reader.readLine()) != null) {
								WebcamServer.logger.printLogLn(true, "FFmpeg frame grabber: " + line);
							}
						} catch (Exception e) {
							WebcamServer.logger.printLogException(e);
						}
						
						if(reader != null) reader.close();
					} catch (Exception e) {
						WebcamServer.logger.printLogException(e);
					}
				}
			};
			eThread.setName("FFmpeg frame grabber stderr reader");
			eThread.start();
			
			InputStream inputStream = process.getInputStream();
			ByteArrayOutputStream baos = new ByteArrayOutputStream(JPEG_INITIAL_BUFFER_SIZE);
			for(int i = Utils.readByteStream(inputStream); i != -1; i = Utils.readByteStream(inputStream)) baos.write(i);
			inputStream.close();
			
			if(baos.size() == 0) return null;
			return baos.toByteArray();
		} catch (Exception e) {
			WebcamServer.logger.printLogException(e);
		}
		
		return null;
	}
}
