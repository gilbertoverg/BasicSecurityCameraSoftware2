package webcamServer;

import java.io.*;

public class FFmpegFileInfo {
	private final File ffmpeg;

	public FFmpegFileInfo(File ffmpeg) {
		if(ffmpeg == null || !ffmpeg.exists()) throw new IllegalArgumentException("FFmpeg executable not found");
		this.ffmpeg = ffmpeg;
	}
	
	public FileInfo getFileInfo(File file) {
		try {
			String[] cmdArray = new String[] { ffmpeg.getAbsolutePath(), "-hide_banner", "-i", file.getAbsolutePath() };
			Process process = Runtime.getRuntime().exec(cmdArray);
			BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()));

			FileInfo fileInfo = new FileInfo();

			try {
				String line = null;
				while((line = reader.readLine()) != null) {
					try {
						WebcamServer.logger.printLogLn(true, "FFmpeg file info: " + line);

						line = line.trim();
						if(line.startsWith("Duration:")) {
							double durationSeconds = parseDurationSeconds(line.substring(9));
							fileInfo.setDurationSeconds(durationSeconds);
						}
						if(line.startsWith("Stream") && line.contains("Video:")) {
							double fps = parseFps(line);
							fileInfo.setFps(fps);
						}
					} catch (Exception e) {
						WebcamServer.logger.printLogException(e);
					}
				}
			} catch (Exception e) {
				WebcamServer.logger.printLogException(e);
			}
			
			reader.close();
			
			if(fileInfo.getDurationSeconds() > 0 && fileInfo.getFps() > 0) return fileInfo;
		} catch (Exception e) {
			WebcamServer.logger.printLogException(e);
		}

		return null;
	}
	
	private double parseDurationSeconds(String line) {
		int ind = line.indexOf(':');
		if(ind > 0) {
			double hours = Double.parseDouble(line.substring(0, ind));
			line = line.substring(ind + 1);
			
			ind = line.indexOf(':');
			if(ind > 0) {
				double minutes = Double.parseDouble(line.substring(0, ind));
				line = line.substring(ind + 1);
				
				ind = 0;
				while(ind < line.length() && ((line.charAt(ind) >= '0' && line.charAt(ind) <= '9') || line.charAt(ind) == '.')) ind++;
				if(ind > 0) {
					double seconds = Double.parseDouble(line.substring(0, ind));
					
					return hours * 3600.0 + minutes * 60.0 + seconds;
				}
			}
		}
		
		return 0;
	}
	
	private double parseFps(String line) {
		int end = line.indexOf("fps") - 1;
		while(end >= 0 && Character.isWhitespace(line.charAt(end))) end--;
		if(end >= 0) {
			int start = end;
			while(start >= 0 && ((line.charAt(start) >= '0' && line.charAt(start) <= '9') || line.charAt(start) == '.')) start--;
			
			if(start < end) return Double.parseDouble(line.substring(start + 1, end + 1));
		}
		
		return 0;
	}
}
