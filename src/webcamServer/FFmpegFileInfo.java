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
							Double durationSeconds = parseDurationSeconds(line.substring(9));
							if(durationSeconds != null && durationSeconds.doubleValue() > 0.001) {
								fileInfo.setDurationSeconds(durationSeconds.doubleValue());
								fileInfo.setEmpty(false);
							}
						}
						if(line.startsWith("Stream") && line.contains("Video:")) {
							double fps = parseFps(line);
							fileInfo.setFps(fps);
							int width = parseWidth(line);
							fileInfo.setWidth(width);
							int height = parseHeight(line);
							fileInfo.setHeight(height);
						}
					} catch (Exception e) {
						WebcamServer.logger.printLogException(e);
					}
				}
			} catch (Exception e) {
				WebcamServer.logger.printLogException(e);
			}
			
			reader.close();
			
			if(fileInfo.isEmpty() || (fileInfo.getDurationSeconds() > 0 && fileInfo.getFps() > 0 && fileInfo.getWidth() > 0 && fileInfo.getHeight() > 0)) return fileInfo;
		} catch (Exception e) {
			WebcamServer.logger.printLogException(e);
		}

		return null;
	}
	
	private Double parseDurationSeconds(String line) {
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
					return Double.valueOf(hours * 3600.0 + minutes * 60.0 + seconds);
				}
			}
		}
		return null;
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
	
	private int parseWidth(String line) {
		for(int i = 0; i < line.length(); i++) {
			if(line.charAt(i) == ' ') {
				int width = 0;
				for(i++; i < line.length() && line.charAt(i) >= '0' && line.charAt(i) <= '9'; i++) {
					width = width * 10 + line.charAt(i) - '0';
				}
				if(i < line.length() && line.charAt(i) == 'x') {
					int height = 0;
					for(i++; i < line.length() && line.charAt(i) >= '0' && line.charAt(i) <= '9'; i++) {
						height = height * 10 + line.charAt(i) - '0';
					}
					if(i < line.length() && (line.charAt(i) < '0' || line.charAt(i) > '9') && width > 0 && height > 0) return width;
				}
			}
		}
		return 0;
	}
	
	private int parseHeight(String line) {
		for(int i = 0; i < line.length(); i++) {
			if(line.charAt(i) == ' ') {
				int width = 0;
				for(i++; i < line.length() && line.charAt(i) >= '0' && line.charAt(i) <= '9'; i++) {
					width = width * 10 + line.charAt(i) - '0';
				}
				if(i < line.length() && line.charAt(i) == 'x') {
					int height = 0;
					for(i++; i < line.length() && line.charAt(i) >= '0' && line.charAt(i) <= '9'; i++) {
						height = height * 10 + line.charAt(i) - '0';
					}
					if(i < line.length() && (line.charAt(i) < '0' || line.charAt(i) > '9') && width > 0 && height > 0) return height;
				}
			}
		}
		return 0;
	}
}
