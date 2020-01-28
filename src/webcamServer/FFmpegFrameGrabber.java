package webcamServer;

import java.io.*;
import java.util.*;

public class FFmpegFrameGrabber {
	private final File ffmpeg;
	private final WebcamServer.Decoder decoder;
	private final int jpegQuality;
	
	private final int JPEG_INITIAL_BUFFER_SIZE = 5000000;
	
	public FFmpegFrameGrabber(File ffmpeg, WebcamServer.Decoder decoder, int jpegQuality) {
		if(ffmpeg == null || !ffmpeg.exists()) throw new IllegalArgumentException("FFmpeg executable not found");
		if(jpegQuality < 2 || jpegQuality > 31) throw new IllegalArgumentException("Jpeg quality out of range");
		this.ffmpeg = ffmpeg;
		this.decoder = decoder;
		this.jpegQuality = jpegQuality;
	}

	public byte[] getFrameFromFile(File file, double time) {
		try {
			List<String> cmdList = new ArrayList<>();
			
			cmdList.add(ffmpeg.getAbsolutePath());
			cmdList.add("-hide_banner");
			
			if(decoder == WebcamServer.Decoder.H264_QSV) {
				cmdList.add("-c:v");
				cmdList.add("h264_qsv");
			}
			else if(decoder == WebcamServer.Decoder.H265_QSV) {
				cmdList.add("-c:v");
				cmdList.add("h265_qsv");
			}
			
			cmdList.add("-ss");
			cmdList.add(String.format("%.5f", time));
			
			cmdList.add("-i");
			cmdList.add(file.getAbsolutePath());
			
			cmdList.add("-vframes");
			cmdList.add("1");
			
			cmdList.add("-qscale:v");
			cmdList.add(Integer.toString(jpegQuality));
			
			cmdList.add("-f");
			cmdList.add("mjpeg");
			
			cmdList.add("pipe:1");
			
			String[] cmdArray = new String[cmdList.size()];
			cmdArray = cmdList.toArray(cmdArray);
			
			WebcamServer.logger.printLogLn(true, "FFmpeg command: " + Arrays.toString(cmdArray));
			
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
