package webcamServer;

import java.io.*;
import java.util.*;

public class WebcamServer {
	public static enum Encoder { MPEG4, H264, H265 };
	public static String VERSION = "2.2.0";
	public static Logger logger = new Logger();
	
	private static Configuration configuration = null;
	private static FileManager fileManager = null;
	private static FFmpegWebcamReader ffmpegWebcamReader = null;
	private static HttpServer httpServer = null;
	
	public static void main(String[] args) {
		logger.printLogLn(false, "Starting WebcamServer " + VERSION);
		
		Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
			public void uncaughtException(Thread t, Throwable e) {
				logger.printLogThrowable(e);
			}
		});
		
		Locale.setDefault(Locale.US);
		
		try {
			File config = new File("Config.txt");
			String arg = getArg("config", args);
			if(arg != null && arg.length() > 0) config = new File(arg);
			
			logger.printLogLn(false, "Reading config: " + config.getName());
			configuration = new Configuration(config);
		} catch (Exception e) {
			logger.printLogLn(false, "Configuration error: " + e.getMessage());
		}
		
		if(configuration != null) {
			try {
				logger.setDebug(configuration.getDebug());
				
				logger.printLogLn(false, "Initializing file manager");
				fileManager = new FileManager(configuration.getFFmpeg(), configuration.getFileFolder(), configuration.getMaxFolders(), configuration.getTimelineQuality(), configuration.getStreamEnable());
				
				logger.printLogLn(false, "Initializing webcam");
				ffmpegWebcamReader = new FFmpegWebcamReader(configuration.getFFmpeg(), configuration.getInputArguments(),
						configuration.getFileFolder(), configuration.getFileEncoder(), configuration.getFileQuality(), configuration.getFileWidth(), configuration.getFileHeight(), configuration.getFileFrameRate(), configuration.getFileSegmentDuration(),
						configuration.getStreamEnable(), configuration.getJpegQuality(), configuration.getJpegWidth(), configuration.getJpegHeight(), configuration.getJpegFrameRate());
				ffmpegWebcamReader.setJpegListener(fileManager);
				
				if(configuration.getHttpPort() > 0) {
					logger.printLogLn(false, "Initializing web server");
					httpServer = new HttpServer(fileManager, configuration.getHttpPort(), configuration.getLogConnections(), configuration.getName(), configuration.getAuthorizationHeader(),
							configuration.getStreamEnable(), configuration.getJpegWidth(), configuration.getJpegHeight(), configuration.getJpegFrameRate(),
							configuration.getFileWidth(), configuration.getFileHeight());
				}
			
				Runtime.getRuntime().addShutdownHook(new Thread() {
					public void run() {
						logger.printLogLn(false, "Closing WebcamServer");

						try {
							if(httpServer != null) {
								httpServer.stop();
								httpServer.waitStop();
							}
						} catch (Exception e) {
							logger.printLogException(e);
						}

						try {
							ffmpegWebcamReader.stop();
							ffmpegWebcamReader.waitStop();
						} catch (Exception e) {
							logger.printLogException(e);
						}

						try {
							fileManager.stop();
							fileManager.waitStop();
						} catch (Exception e) {
							logger.printLogException(e);
						}

						logger.printLogLn(false, "WebcamServer closed");
					}
				});

				fileManager.start();
				ffmpegWebcamReader.start();
				if(httpServer != null) httpServer.start();

				handleConsole();
			} catch (Exception e) {
				logger.printLogException(e);
			}
		}
		
		System.exit(0);
	}
	
	private static void handleConsole() {
		Scanner console = new Scanner(System.in);

		try {
			while(console.hasNextLine()) {
				String line = console.nextLine();
				StringTokenizer tokenizer = new StringTokenizer(line);

				if(tokenizer.hasMoreTokens()) {
					String token = tokenizer.nextToken();

					if(token.equalsIgnoreCase("quit")) {
						logger.printLogLn(false, "Quitting");
						break;
					}
					else if(token.equalsIgnoreCase("reindex")) {
						logger.printLogLn(false, "Reindex activated");
						fileManager.activateReIndex();
					}
					else logger.printLogLn(false, "Command not valid");
				}
			}
		} catch (Exception e) {
			logger.printLogException(e);
		}

		console.close();
	}
	
	private static String getArg(String key, String[] args) {
		boolean returnArg = false;
		for(String arg : args) {
			if(arg.equals("-" + key)) returnArg = true;
			else if(returnArg) return arg;
		}
		return null;
	}
}
