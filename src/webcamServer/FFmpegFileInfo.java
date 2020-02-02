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

			Double durationSeconds = null, fps = null;
			Integer width = null, height = null;

			try {
				String line = null;
				while((line = reader.readLine()) != null) {
					try {
						WebcamServer.logger.printLogLn(true, "FFmpeg file info: " + line);

						line = line.trim();
						if(line.startsWith("Duration:")) durationSeconds = parseDurationSeconds(line.substring(9));
						if(line.startsWith("Stream") && line.contains("Video:")) {
							fps = parseFps(line);
							width = parseWidth(line);
							height = parseHeight(line);
						}
					} catch (Exception e) {
						WebcamServer.logger.printLogException(e);
					}
				}
			} catch (Exception e) {
				WebcamServer.logger.printLogException(e);
			}
			
			reader.close();
			
			if(durationSeconds != null && durationSeconds.doubleValue() < 0.001) {
				return new FileInfo(durationSeconds.doubleValue(),
						fps != null ? fps.doubleValue() : 0.0,
						width != null ? width.intValue() : 0,
						height != null ? height.intValue() : 0,
						true);
			}
			if(durationSeconds != null && fps != null && width != null && height != null && fps.doubleValue() > 0 && width.intValue() > 0 && height.intValue() > 0) {
				return new FileInfo(durationSeconds.doubleValue(), fps.doubleValue(), width.intValue(), height.intValue(), durationSeconds.doubleValue() < 0.001);
			}
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
	
	private Double parseFps(String line) {
		int end = line.indexOf("fps") - 1;
		while(end >= 0 && Character.isWhitespace(line.charAt(end))) end--;
		if(end >= 0) {
			int start = end;
			while(start >= 0 && ((line.charAt(start) >= '0' && line.charAt(start) <= '9') || line.charAt(start) == '.')) start--;
			if(start < end) return Double.valueOf(Double.parseDouble(line.substring(start + 1, end + 1)));
		}
		return null;
	}
	
	private Integer parseWidth(String line) {
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
					if(i < line.length() && (line.charAt(i) < '0' || line.charAt(i) > '9') && width > 0 && height > 0) return Integer.valueOf(width);
				}
			}
		}
		return null;
	}
	
	private Integer parseHeight(String line) {
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
					if(i < line.length() && (line.charAt(i) < '0' || line.charAt(i) > '9') && width > 0 && height > 0) return Integer.valueOf(height);
				}
			}
		}
		return null;
	}
}
