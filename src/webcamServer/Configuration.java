package webcamServer;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class Configuration {
	private final String name, username, password;
	private final File ffmpeg, fileFolder;
	private final WebcamServer.Encoder fileEncoder;
	private final Integer fileQuality, fileWidth, fileHeight, fileFrameRate, fileSegmentDuration, maxFolders, timelineQuality, jpegQuality, jpegWidth, jpegHeight, jpegFrameRate, httpPort;
	private final Boolean debug, streamEnable, logConnections;
	private final List<String> inputArguments;

	public Configuration(File configFile) {
		String config = readFile(configFile);
		if(config == null) throw new IllegalArgumentException("Error reading: " + configFile.getName());
		
		name = getValue("Name", config);
		ffmpeg = stringToFile(getValue("FFmpeg executable", config));
		inputArguments = stringTokenize(getValue("FFmpeg input arguments", config));
		debug = stringToBool(getValue("Debug", config));
		fileFolder = stringToFile(getValue("Storage folder", config));
		fileEncoder = stringToEncoder(getValue("Storage video encoder", config));
		fileQuality = stringToInt(getValue("Storage video quality", config));
		fileWidth = stringToInt(getValue("Storage video width", config));
		fileHeight = stringToInt(getValue("Storage video height", config));
		fileFrameRate = stringToInt(getValue("Storage video frame rate", config));
		fileSegmentDuration = stringToInt(getValue("Storage video segment duration", config));
		maxFolders = stringToInt(getValue("Storage video max folders", config));
		timelineQuality = stringToInt(getValue("Storage video timeline quality", config));
		streamEnable = stringToBool(getValue("Stream enable", config));
		jpegQuality = stringToInt(getValue("Stream frame quality", config));
		jpegWidth = stringToInt(getValue("Stream frame width", config));
		jpegHeight = stringToInt(getValue("Stream frame height", config));
		jpegFrameRate = stringToInt(getValue("Stream frame rate", config));
		httpPort = stringToInt(getValue("Web server port", config));
		username = getValue("Web server username", config);
		password = getValue("Web server password", config);
		logConnections = stringToBool(getValue("Web server log connections", config));
		
		if(name == null || name.isEmpty()) throw new IllegalArgumentException("Name is missing from " + configFile.getName());
		if(ffmpeg == null) throw new IllegalArgumentException("FFmpeg executable is missing from " + configFile.getName());
		if(inputArguments == null) throw new IllegalArgumentException("FFmpeg input arguments are missing from " + configFile.getName());
		if(debug == null) throw new IllegalArgumentException("Debug is missing from " + configFile.getName());
		if(fileFolder != null) {
			if(fileEncoder == null) throw new IllegalArgumentException("Storage video encoder is missing from " + configFile.getName());
			if(fileEncoder != WebcamServer.Encoder.COPY && fileQuality == null) throw new IllegalArgumentException("Storage video quality is missing from " + configFile.getName());
			if(fileWidth == null) throw new IllegalArgumentException("Storage video width is missing from " + configFile.getName());
			if(fileHeight == null) throw new IllegalArgumentException("Storage video height is missing from " + configFile.getName());
			if(fileEncoder != WebcamServer.Encoder.COPY && fileFrameRate == null) throw new IllegalArgumentException("Storage video frame rate is missing from " + configFile.getName());
			if(fileSegmentDuration == null) throw new IllegalArgumentException("Storage video segment duration is missing from " + configFile.getName());
			if(maxFolders == null) throw new IllegalArgumentException("Storage video max folders is missing from " + configFile.getName());
			if(timelineQuality == null) throw new IllegalArgumentException("Storage video timeline quality is missing from " + configFile.getName());
		}
		if(streamEnable != null && streamEnable.booleanValue()) {
			if(jpegQuality == null) throw new IllegalArgumentException("Stream frame quality is missing from " + configFile.getName());
			if(jpegWidth == null) throw new IllegalArgumentException("Stream frame width is missing from " + configFile.getName());
			if(jpegHeight == null) throw new IllegalArgumentException("Stream frame height is missing from " + configFile.getName());
			if(jpegFrameRate == null) throw new IllegalArgumentException("Stream frame rate is missing from " + configFile.getName());
		}
		if(httpPort != null) {
			if(username == null && password != null) throw new IllegalArgumentException("Web server username is missing from " + configFile.getName());
			if(username != null && password == null) throw new IllegalArgumentException("Web server password is missing from " + configFile.getName());
			if(logConnections == null) throw new IllegalArgumentException("Web server log connections is missing from " + configFile.getName());
		}
		
		String conf = "Configuration:\r\n";
		conf += "Name: " + name + "\r\n";
		conf += "FFmpeg executable: " + ffmpeg.getAbsolutePath() + "\r\n";
		conf += "FFmpeg input arguments: " + Arrays.toString(inputArguments.toArray()) + "\r\n";
		conf += "Debug: " + debug.booleanValue() + "\r\n";
		conf += "Storage folder: " + (fileFolder == null ? "none" : fileFolder.getAbsolutePath()) + "\r\n";
		if(fileFolder != null) {
			conf += "Storage video encoder: " + fileEncoder.name() + "\r\n";
			if(fileEncoder != WebcamServer.Encoder.COPY) conf += "Storage video quality: " + fileQuality.intValue() + "\r\n";
			conf += "Storage video width: " + fileWidth.intValue() + "\r\n";
			conf += "Storage video height: " + fileHeight.intValue() + "\r\n";
			if(fileEncoder != WebcamServer.Encoder.COPY) conf += "Storage video frame rate: " + fileFrameRate.intValue() + "\r\n";
			conf += "Storage video segment duration: " + fileSegmentDuration.intValue() + "\r\n";
			conf += "Storage video max folders: " + maxFolders.intValue() + "\r\n";
			conf += "Storage video timeline quality: " + timelineQuality.intValue() + "\r\n";
		}
		conf += "Stream enable: " + (streamEnable != null && streamEnable.booleanValue()) + "\r\n";
		if(streamEnable != null && streamEnable.booleanValue()) {
			conf += "Stream frame quality: " + jpegQuality.intValue() + "\r\n";
			conf += "Stream frame width: " + jpegWidth.intValue() + "\r\n";
			conf += "Stream frame height: " + jpegHeight.intValue() + "\r\n";
			conf += "Stream frame rate: " + jpegFrameRate.intValue() + "\r\n";
		}
		conf += "Web server port: " + httpPort.intValue() + "\r\n";
		if(httpPort != null) {
			if(username != null && password != null) {
				conf += "Web server username: " + username + "\r\n";
				conf += "Web server password: " + getPasswordMasked() + "\r\n";
			}
			conf += "Web server log connections: " + logConnections.booleanValue() + "\r\n";
		}
		WebcamServer.logger.printLog(false, conf);
	}
	
	public String getName() {
		return name;
	}

	public File getFFmpeg() {
		return ffmpeg;
	}
	
	public List<String> getInputArguments() {
		return inputArguments;
	}

	public File getFileFolder() {
		return fileFolder;
	}

	public WebcamServer.Encoder getFileEncoder() {
		return fileEncoder;
	}

	public int getFileQuality() {
		return fileQuality == null ? 0 : fileQuality.intValue();
	}

	public int getFileWidth() {
		return fileWidth == null ? 0 : fileWidth.intValue();
	}

	public int getFileHeight() {
		return fileHeight == null ? 0 : fileHeight.intValue();
	}

	public int getFileFrameRate() {
		return fileFrameRate == null ? 0 : fileFrameRate.intValue();
	}

	public int getFileSegmentDuration() {
		return fileSegmentDuration == null ? 0 : fileSegmentDuration.intValue();
	}

	public int getMaxFolders() {
		return maxFolders == null ? 0 : maxFolders.intValue();
	}

	public int getTimelineQuality() {
		return timelineQuality == null ? 0 : timelineQuality.intValue();
	}

	public int getJpegQuality() {
		return jpegQuality == null ? 0 : jpegQuality.intValue();
	}

	public int getJpegWidth() {
		return jpegWidth == null ? 0 : jpegWidth.intValue();
	}

	public int getJpegHeight() {
		return jpegHeight == null ? 0 : jpegHeight.intValue();
	}

	public int getJpegFrameRate() {
		return jpegFrameRate == null ? 0 : jpegFrameRate.intValue();
	}

	public int getHttpPort() {
		return httpPort == null ? 0 : httpPort.intValue();
	}

	public String getAuthorizationHeader() {
		if(username == null || password == null) return null;
		return "Basic " + Base64.getEncoder().encodeToString((username + ":" + password).getBytes());
	}

	public String getPasswordMasked() {
		if(password == null) return null;
		String out = "";
		for(int i = 0; i < password.length(); i++) out += "*";
		return out;
	}

	public boolean getDebug() {
		return debug == null ? false : debug.booleanValue();
	}

	public boolean getStreamEnable() {
		return streamEnable == null ? false : streamEnable.booleanValue();
	}

	public boolean getLogConnections() {
		return logConnections == null ? false : logConnections.booleanValue();
	}

	private File stringToFile(String s) {
		if(s == null) return null;
		return new File(s);
	}
	
	private Integer stringToInt(String s) {
		try {
			int i = Integer.parseInt(s);
			return Integer.valueOf(i);
		} catch (Exception e) {
			
		}
		
		return null;
	}
	
	private Boolean stringToBool(String s) {
		if(s == null) return null;
		if(s.equalsIgnoreCase("true")) return Boolean.TRUE;
		if(s.equalsIgnoreCase("false")) return Boolean.FALSE;
		return null;
	}
	
	private WebcamServer.Encoder stringToEncoder(String s) {
		if(s == null) return null;
		if(s.equalsIgnoreCase("mpeg4")) return WebcamServer.Encoder.MPEG4;
		if(s.equalsIgnoreCase("h264")) return WebcamServer.Encoder.H264;
		if(s.equalsIgnoreCase("h264_qsv")) return WebcamServer.Encoder.H264_QSV;
		if(s.equalsIgnoreCase("h265")) return WebcamServer.Encoder.H265;
		if(s.equalsIgnoreCase("h265_qsv")) return WebcamServer.Encoder.H265_QSV;
		if(s.equalsIgnoreCase("copy")) return WebcamServer.Encoder.COPY;
		return null;
	}
	
	private List<String> stringTokenize(String line) {
		if(line == null) return null;
		
		String tmp = "";
		boolean doNotSplit = false;
		List<String> out = new ArrayList<>();
		
		for(int i = 0; i < line.length(); i++) {
			char c = line.charAt(i);
			
			if(c == '\\') {
				if(i < line.length() - 1 && line.charAt(i + 1) == '\"') {
					tmp += '\"';
					i++;
				}
				else tmp += '\\';
			}
			else if(c == '\"') {
				doNotSplit = !doNotSplit;
				if(!doNotSplit) {
					if(tmp.length() > 0) out.add(tmp);
					tmp = "";
				}
			}
			else if(c <= ' ') {
				if(doNotSplit) tmp += c;
				else {
					if(tmp.length() > 0) out.add(tmp);
					tmp = "";
				}
			}
			else tmp += c;
		}
		if(tmp.length() > 0) out.add(tmp);
		
		return out;
	}
	
	private String getValue(String key, String txt) {
		String value = null;
		
		try {
			Scanner scanner = new Scanner(txt);
			while(scanner.hasNextLine()) {
				String line = scanner.nextLine();
				
				int i = line.indexOf('#');
				if(i >= 0) line = line.substring(0, i);
				
				i = line.indexOf(':');
				if(i >= 0) {
					String keyLine = line.substring(0, i).trim();
					if(key.equals(keyLine)) {
						value = line.substring(i + 1).trim();
						if(value.isEmpty()) value = null;
					}
				}
			}
			scanner.close();
		} catch (Exception e) {
			
		}
		
		return value;
	}
	
	private String readFile(File file) {
		try {
			Path path = file.toPath();
			byte[] content = Files.readAllBytes(path);
			return new String(content);
		} catch (Exception e) {
			
		}
		
		return null;
	}
}
