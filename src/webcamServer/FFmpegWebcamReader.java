package webcamServer;

import java.io.*;
import java.util.*;
import listeners.*;

public class FFmpegWebcamReader implements JpegListener, LogListener, NewTmpFileListener, MotionDetectionListener {
	private final FFmpegLogMonitor ffmpegLogMonitor;
	private final FFmpegProcess ffmpegProcess;
	
	private volatile Thread thread = null;
	private volatile boolean killThread = false, restartFFmpeg = false, enableFFmpegLog = true;
	
	private volatile List<JpegListener> jpegListeners = new ArrayList<>();
	private volatile List<StatListener> statListeners = new ArrayList<>();
	private volatile List<NewTmpFileListener> newTmpFileListeners = new ArrayList<>();
	private volatile List<MotionDetectionListener> motionDetectionListeners = new ArrayList<>();

	public FFmpegWebcamReader(File ffmpeg, List<String> inputArguments, boolean motionDetection,
								File fileFolder, WebcamServer.Encoder encoder, int fileQuality, int fileWidth, int fileHeight, int fileFrameRate, int fileSegmentDuration,
								boolean jpegStream, int jpegQuality, int jpegWidth, int jpegHeight, int jpegFrameRate) {
		if(ffmpeg == null || !ffmpeg.exists()) throw new IllegalArgumentException("FFmpeg executable not found");
		if(inputArguments == null || inputArguments.size() < 2) throw new IllegalArgumentException("Input arguments are empty");
		int ind = -1;
		for(int i = 0; i < inputArguments.size(); i++) {
			if(inputArguments.get(i).equals("-i")) ind = i;
		}
		if(ind < 0 || ind == inputArguments.size() - 1 || inputArguments.get(ind + 1).startsWith("-")) throw new IllegalArgumentException("Input arguments are not valid");
		if(fileFolder != null) {
			if(encoder == null) throw new IllegalArgumentException("Unknown encoder");
			if(encoder != WebcamServer. Encoder.COPY) {
				if(encoder == WebcamServer.Encoder.MPEG4) {
					if(fileQuality < 1 || fileQuality > 31) throw new IllegalArgumentException("File quality out of range");
				}
				else if(encoder == WebcamServer.Encoder.H264 || encoder == WebcamServer.Encoder.H264_QSV) {
					if(fileQuality < 1 || fileQuality > 51) throw new IllegalArgumentException("File quality out of range");
				}
				else if(encoder == WebcamServer. Encoder.H265 || encoder == WebcamServer. Encoder.H265_QSV) {
					if(fileQuality < 1 || fileQuality > 51) throw new IllegalArgumentException("File quality out of range");
				}
				else throw new IllegalArgumentException("Unknown encoder");
				if(fileWidth < 64 || fileWidth > 10000) throw new IllegalArgumentException("File width out of range");
				if(fileHeight < 64 || fileHeight > 10000) throw new IllegalArgumentException("File height out of range");
				if(fileFrameRate < 1 || fileFrameRate > 60) throw new IllegalArgumentException("File frame rate out of range");
			}
			if(fileSegmentDuration < 5 || fileSegmentDuration > 3600) throw new IllegalArgumentException("File segment duration out of range");
		}
		if(jpegStream) {
			if(jpegQuality != 0 && (jpegQuality < 2 || jpegQuality > 31)) throw new IllegalArgumentException("Jpeg quality out of range");
			if(jpegWidth < 64 || jpegWidth > 10000) throw new IllegalArgumentException("Jpeg width out of range");
			if(jpegHeight < 64 || jpegHeight > 10000) throw new IllegalArgumentException("Jpeg height out of range");
			if(jpegFrameRate < 1 || jpegFrameRate > 60) throw new IllegalArgumentException("Jpeg frame rate out of range");
		}
		if(fileFolder == null && !jpegStream) throw new IllegalArgumentException("No output specified");
		
		List<String> cmdList = new ArrayList<>();
		cmdList.add(ffmpeg.getAbsolutePath());
		cmdList.add("-hide_banner");
		
		cmdList.add("-loglevel");
		cmdList.add("+repeat");
		
		cmdList.addAll(inputArguments);
		
		if(fileFolder != null) {
			if(encoder == WebcamServer.Encoder.MPEG4) {
				cmdList.add("-c:v");
				cmdList.add("mpeg4");
				
				cmdList.add("-qscale:v");
				cmdList.add(Integer.toString(fileQuality));
			}
			else if(encoder == WebcamServer.Encoder.H264) {
				cmdList.add("-c:v");
				cmdList.add("libx264");
				
				cmdList.add("-x264-params");
				cmdList.add("keyint=" + Integer.toString(fileFrameRate * mcd(20, fileSegmentDuration)) + ":scenecut=0");
				
				cmdList.add("-preset");
				cmdList.add("faster");
				
				cmdList.add("-crf");
				cmdList.add(Integer.toString(fileQuality));
				
				cmdList.add("-pix_fmt");
				cmdList.add("yuv420p");
			}
			else if(encoder == WebcamServer.Encoder.H264_QSV) {
				cmdList.add("-c:v");
				cmdList.add("h264_qsv");
				
				cmdList.add("-g");
				cmdList.add(Integer.toString(fileFrameRate * mcd(20, fileSegmentDuration)));
				
				cmdList.add("-preset");
				cmdList.add("faster");
				
				cmdList.add("-global_quality");
				cmdList.add(Integer.toString(fileQuality));
				
				cmdList.add("-pix_fmt");
				cmdList.add("yuv420p");
			}
			else if(encoder == WebcamServer.Encoder.H265) {
				cmdList.add("-c:v");
				cmdList.add("libx265");
				
				cmdList.add("-tag:v");
				cmdList.add("hvc1");
				
				cmdList.add("-x265-params");
				cmdList.add("keyint=" + Integer.toString(fileFrameRate * mcd(20, fileSegmentDuration)) + ":scenecut=0");
				
				cmdList.add("-preset");
				cmdList.add("faster");
				
				cmdList.add("-crf");
				cmdList.add(Integer.toString(fileQuality));
				
				cmdList.add("-pix_fmt");
				cmdList.add("yuv420p");
			}
			else if(encoder == WebcamServer.Encoder.H265_QSV) {
				cmdList.add("-c:v");
				cmdList.add("hevc_qsv");
				
				cmdList.add("-load_plugin");
				cmdList.add("hevc_hw");
				
				cmdList.add("-tag:v");
				cmdList.add("hvc1");
				
				cmdList.add("-g");
				cmdList.add(Integer.toString(fileFrameRate * mcd(20, fileSegmentDuration)));
				
				cmdList.add("-preset");
				cmdList.add("faster");
				
				cmdList.add("-global_quality");
				cmdList.add(Integer.toString(fileQuality));
			}
			else if(encoder == WebcamServer.Encoder.COPY) {
				cmdList.add("-c:v");
				cmdList.add("copy");
			}
			
			if(encoder != WebcamServer.Encoder.COPY) {
				cmdList.add("-s");
				cmdList.add(fileWidth + "x" + fileHeight);

				cmdList.add("-r");
				cmdList.add(Integer.toString(fileFrameRate));
			}
			
			cmdList.add("-movflags");
			cmdList.add("+faststart");
			
			cmdList.add("-f");
			cmdList.add("segment");
			
			cmdList.add("-segment_time");
			cmdList.add(Integer.toString(fileSegmentDuration));
			
			cmdList.add("-reset_timestamps");
			cmdList.add("1");
			
			cmdList.add("-segment_format");
			cmdList.add("mp4");
			
			cmdList.add("-strftime");
			cmdList.add("1");
			
			cmdList.add("-y");
			cmdList.add(fileFolder.getAbsolutePath() + File.separator + "tmp" + File.separator + "TMP_%Y-%m-%d_%H-%M-%S.mp4");
		}
		
		if(jpegStream) {
			if(jpegQuality == 0) {
				cmdList.add("-c:v");
				cmdList.add("copy");
			}
			else {
				cmdList.add("-c:v");
				cmdList.add("mjpeg");
				
				cmdList.add("-qscale:v");
				cmdList.add(Integer.toString(jpegQuality));
				
				cmdList.add("-s");
				cmdList.add(jpegWidth + "x" + jpegHeight);
				
				cmdList.add("-r");
				cmdList.add(Integer.toString(jpegFrameRate));
			}
			
			cmdList.add("-f");
			cmdList.add("mjpeg");
			
			cmdList.add("pipe:1");
		}
		
		if(motionDetection) {
			cmdList.add("-vf");
			cmdList.add("select='gte(t\\,selected_n)',select='gte(scene\\,0)',metadata=print");
			
			cmdList.add("-f");
			cmdList.add("null");
			
			cmdList.add("-");
		}
		
		String[] cmdArray = new String[cmdList.size()];
		cmdArray = cmdList.toArray(cmdArray);
		
		WebcamServer.logger.printLogLn(true, "FFmpeg command: " + Arrays.toString(cmdArray));
		
		ffmpegLogMonitor = new FFmpegLogMonitor(30000, 0.75, 15);
		ffmpegLogMonitor.setNewTmpFileListener(this);
		ffmpegLogMonitor.setMotionDetectionListener(this);
		
		ffmpegProcess = new FFmpegProcess(cmdArray);
		ffmpegProcess.setJpegListener(this);
		ffmpegProcess.setLogListener(this);
	}
	
	public synchronized void addJpegListener(JpegListener jpegListener) {
		if(jpegListener != null) jpegListeners.add(jpegListener);
	}
	
	public synchronized void addStatListener(StatListener statListener) {
		if(statListener != null) statListeners.add(statListener);
	}
	
	public synchronized void addNewTmpFileListener(NewTmpFileListener newTmpFileListener) {
		if(newTmpFileListener != null) newTmpFileListeners.add(newTmpFileListener);
	}
	
	public synchronized void addMotionDetectionListener(MotionDetectionListener motionDetectionListener) {
		if(motionDetectionListener != null) motionDetectionListeners.add(motionDetectionListener);
	}
	
	public synchronized void restartFFmpeg() {
		restartFFmpeg = true;
	}
	
	public synchronized void enableFFmpegLog(boolean enable) {
		enableFFmpegLog = enable;
	}
	
	public synchronized boolean isFFmpegLogEnabled() {
		return enableFFmpegLog;
	}
	
	public synchronized void start() {
		if(isRunning()) return;
		
		try {
			killThread = false;
			restartFFmpeg = false;
			thread = new Thread() {
				public void run() {
					while(!killThread) {
						try {
							WebcamServer.logger.printLogLn(false, "Opening webcam");
							ffmpegLogMonitor.reset();
							for(StatListener sl : statListeners) sl.resetStats();
							for(MotionDetectionListener mdl : motionDetectionListeners) mdl.resetMotion();
							restartFFmpeg = false;
							enableFFmpegLog = true;
							ffmpegProcess.start();

							boolean started = false;
							while(!killThread && ffmpegProcess.isRunning()) {
								if(enableFFmpegLog) {
									if(ffmpegLogMonitor.isStarted() && !started) {
										WebcamServer.logger.printLogLn(false, "Webcam opened");
										started = true;
									}
									if(!ffmpegLogMonitor.isAlive()) {
										WebcamServer.logger.printLogLn(false, "FFmpeg is not responding");
										break;
									}
									if(ffmpegLogMonitor.isDuplicatingFrames()) {
										WebcamServer.logger.printLogLn(false, "FFmpeg is repeating same frame");
										break;
									}
								}
								else ffmpegLogMonitor.reset();
								if(restartFFmpeg) {
									WebcamServer.logger.printLogLn(false, "FFmpeg restart requested");
									restartFFmpeg = false;
									break;
								}
								Thread.sleep(1);
							}
						} catch (Exception e) {
							WebcamServer.logger.printLogException(e);
						}

						try {
							WebcamServer.logger.printLogLn(false, "Stopping webcam");
							ffmpegProcess.stop();
							ffmpegProcess.waitStop();
							WebcamServer.logger.printLogLn(false, "Webcam stopped");
						} catch (Exception e) {
							WebcamServer.logger.printLogException(e);
						}

						try {
							if(!killThread) Thread.sleep(1000);
						} catch (InterruptedException e) {

						}
					}
				}
			};
			thread.setName("FFmpeg controller");
			thread.start();
		} catch (Exception e) {
			WebcamServer.logger.printLogException(e);
		}
	}
	
	public synchronized void stop() {
		killThread = true;
	}
	
	public synchronized boolean isRunning() {
		if(thread != null && thread.isAlive()) return true;
		if(ffmpegProcess.isRunning()) return true;
		return false;
	}
	
	public synchronized void waitStop() {
		while(isRunning()) {
			try {
				Thread.sleep(1);
			} catch (InterruptedException e) {

			}
		}
	}
	
	private int mcd(int i, int j) {
		if(i < 1) return 0;
		if(j < 1) return 0;
		int a = i;
		if(j > i) {
			i = j;
			j = a;
		}
		while((a = i % j) != 0) {
			i = j;
			j = a;
		}
		return j;
	}
	
	@Override
	public void newJpeg(byte[] jpeg) {
		for(JpegListener jl : jpegListeners) jl.newJpeg(jpeg);
	}

	@Override
	public void newLogLine(String line) {
		ffmpegLogMonitor.update(line);
	}

	@Override
	public void newTmpFile(String file) {
		for(NewTmpFileListener ntfl : newTmpFileListeners) ntfl.newTmpFile(file);
	}
	
	@Override
	public void newMotionLevel(double motion) {
		for(MotionDetectionListener mdl : motionDetectionListeners) mdl.newMotionLevel(motion);
	}

	@Override
	public void resetMotion() {
		for(MotionDetectionListener mdl : motionDetectionListeners) mdl.resetMotion();
	}
}
