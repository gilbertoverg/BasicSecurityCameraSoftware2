package webcamServer;

import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.time.format.*;
import java.time.temporal.*;
import java.util.*;
import listeners.*;

public class FileManager implements JpegListener, NewTmpFileListener, MotionDetectionListener {
	private final String FILE_PATTERN = "????-??-??_??-??-??.mp4";
	private final String FOLDER_PATTERN = "????-??-??";
	private final String FILE_LIST_NAME = "FileList.txt";
	private final String FILE_MOTION_LIST_NAME = "FileMotionList.txt";
	
	private final FFmpegFileInfo ffmpegFileInfo;
	private final FFmpegFrameGrabber ffmpegFrameGrabber;
	
	private final File storageFolder;
	private final int maxFolders;
	private final boolean enableJpeg;
	private final boolean enableMotionDetection;
	
	private volatile Thread thread = null;
	private volatile boolean killThread = false, reIndex = false;
	
	private volatile Queue<String> tmpFileQueue = null;
	private volatile Queue<List<Double>> tmpFileMotionQueue = null;
	private volatile Object tmpFileQueueLock = new Object();
	
	private volatile byte[] lastJpeg = null;
	
	private volatile double currentMotionLevel = 0;
	private volatile List<Double> motionLevelHistory = null;
	
	private volatile NewFileListener newFileListener = null;

	public FileManager(File ffmpeg, File storageFolder, WebcamServer.Decoder fileDecoder, int maxFolders, int timelineQuality, boolean enableJpeg, boolean enableMotionDetection) {
		this.ffmpegFileInfo = new FFmpegFileInfo(ffmpeg);
		if(storageFolder != null) this.ffmpegFrameGrabber = new FFmpegFrameGrabber(ffmpeg, fileDecoder, timelineQuality);
		else this.ffmpegFrameGrabber = null;
		this.storageFolder = storageFolder;
		this.maxFolders = maxFolders;
		this.enableJpeg = enableJpeg;
		this.enableMotionDetection = enableMotionDetection;
	}
	
	public synchronized void setNewFileListener(NewFileListener newFileListener) {
		this.newFileListener = newFileListener;
	}
	
	public synchronized void start() {
		if(isRunning()) return;
		if(storageFolder == null) return;
		
		try {
			WebcamServer.logger.printLogLn(false, "Starting file manager");
			
			if(!storageFolder.exists()) {
				WebcamServer.logger.printLogLn(false, "Creating storage folder");
				storageFolder.mkdir();
			}
			
			File tmp = new File(storageFolder, "tmp");
			if(!tmp.exists()) {
				WebcamServer.logger.printLogLn(false, "Creating temporary folder");
				tmp.mkdir();
			}
			
			WebcamServer.logger.printLogLn(true, "Cleaning temporary folder");
			deleteFolderContent(tmp);
			
			synchronized (tmpFileQueueLock) {
				tmpFileQueue = new LinkedList<>();
				tmpFileMotionQueue = new LinkedList<>();
				motionLevelHistory = null;
			}
			
			killThread = false;
			thread = new Thread() {
				public void run() {
					try {
						WebcamServer.logger.printLogLn(false, "File manager started");

						while(!killThread) {
							manageTask(false);

							for(int i = 0; i < 10 && !killThread; i++) {
								Thread.sleep(100);
							}
						}

						WebcamServer.logger.printLogLn(false, "Stopping file manager");
						manageTask(true);
						WebcamServer.logger.printLogLn(false, "File manager stopped");
					} catch (Exception e) {
						WebcamServer.logger.printLogException(e);
					}
				}
			};
			thread.setName("File manager");
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
	
	public String[] getFolders() {
		try {
			if(storageFolder == null) return null;
			List<File> folders = listFiles(storageFolder, FOLDER_PATTERN);
			if(folders == null) return null;
			String[] folderNames = new String[folders.size()];
			for(int i = 0; i < folderNames.length; i++) folderNames[i] = folders.get(i).getName();
			return folderNames;
		} catch (Exception e) {
			WebcamServer.logger.printLogException(e);
		}
		
		return null;
	}
	
	public String getFiles(String folderName) {
		try {
			if(storageFolder == null) return null;
			if(!matchString(folderName, FOLDER_PATTERN)) return null;
			File folder = new File(storageFolder, folderName);
			File file = new File(folder, FILE_LIST_NAME);
			if(!file.exists()) return null;
			return new String(Files.readAllBytes(file.toPath()));
		} catch (Exception e) {
			WebcamServer.logger.printLogException(e);
		}
		
		return null;
	}
	
	public String getFileMotionList(String folderName) {
		try {
			if(storageFolder == null) return null;
			if(!matchString(folderName, FOLDER_PATTERN)) return null;
			File folder = new File(storageFolder, folderName);
			File file = new File(folder, FILE_MOTION_LIST_NAME);
			if(!file.exists()) return null;
			return new String(Files.readAllBytes(file.toPath()));
		} catch (Exception e) {
			WebcamServer.logger.printLogException(e);
		}
		
		return null;
	}
	
	public File getFileInFolder(String fileName, String folderName) {
		try {
			if(storageFolder == null) return null;
			if(!matchString(fileName, FILE_PATTERN)) return null;
			if(!matchString(folderName, FOLDER_PATTERN)) return null;
			File folder = new File(storageFolder, folderName);
			File file = new File(folder, fileName);
			if(!file.exists()) return null;
			return file;
		} catch (Exception e) {
			WebcamServer.logger.printLogException(e);
		}
		
		return null;
	}
	
	public byte[] getFrameFromFile(String fileName, String folderName, double time) {
		try {
			if(storageFolder == null) return null;
			if(!matchString(fileName, FILE_PATTERN)) return null;
			if(!matchString(folderName, FOLDER_PATTERN)) return null;
			if(time < 0.0) return null;
			File folder = new File(storageFolder, folderName);
			File file = new File(folder, fileName);
			if(!file.exists()) return null;
			return ffmpegFrameGrabber.getFrameFromFile(file, time);
		} catch (Exception e) {
			WebcamServer.logger.printLogException(e);
		}

		return null;
	}
	
	public double getCurrentMotionLevel() {
		return currentMotionLevel;
	}
	
	public byte[] getLastJpeg() {
		if(!enableJpeg) return null;
		return lastJpeg;
	}
	
	@Override
	public void newJpeg(byte[] jpeg) {
		lastJpeg = jpeg;
	}
	
	@Override
	public void newTmpFile(String file) {
		if(file != null) {
			synchronized (tmpFileQueueLock) {
				tmpFileQueue.add(file);
				if(motionLevelHistory != null) tmpFileMotionQueue.add(motionLevelHistory);
				motionLevelHistory = new ArrayList<>();
			}
		}
	}
	
	@Override
	public void newMotionLevel(double motion) {
		currentMotionLevel = motion;
		synchronized (tmpFileQueueLock) {
			if(motionLevelHistory != null) motionLevelHistory.add(motion);
		}
	}
	
	@Override
	public void resetMotion() {
		currentMotionLevel = 0;
	}
	
	public void activateReIndex() {
		reIndex = true;
	}
	
	private void manageTask(boolean finalize) {
		try {
			File tmpFile = null;
			File tmpFolder = new File(storageFolder, "tmp");
			List<Double> tmpMotionList = null;
			synchronized (tmpFileQueueLock) {
				if(tmpFileQueue.size() >= (finalize ? 1 : 2)) tmpFile = new File(tmpFolder, tmpFileQueue.peek());
				if(tmpFileMotionQueue.isEmpty()) tmpMotionList = motionLevelHistory;
				else tmpMotionList = tmpFileMotionQueue.peek();
			}
			if(tmpFile != null) {
				Thread.sleep(500);
				
				if(tmpFile.exists()) {
					File newFolder = new File(storageFolder, tmpFile.getName().substring(4, 14));
					if(!newFolder.exists()) {
						WebcamServer.logger.printLogLn(false, "Creating folder: " + newFolder.getName());
						newFolder.mkdir();
					}
					
					File newFile = new File(newFolder, tmpFile.getName().substring(4));
					
					if(newFile.exists()) {
						WebcamServer.logger.printLogLn(false, "File already exists: " + newFile.getName());
						newFile = findNewFileName(newFolder, newFile.getName());
						WebcamServer.logger.printLogLn(false, "Found new name: " + newFile.getName());
					}
					
					WebcamServer.logger.printLogLn(true, "Moving file: " + newFile.getName());
					Files.move(tmpFile.toPath(), newFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
					
					synchronized (tmpFileQueueLock) {
						tmpFileQueue.remove();
						if(!tmpFileMotionQueue.isEmpty()) tmpFileMotionQueue.remove();
					}
					
					if(updateFileList(newFile, newFolder, true)) {
						if(enableMotionDetection) updateMotionList(newFile, newFolder, tmpMotionList);
						if(newFileListener != null) newFileListener.newFile(newFile);
					}
				}
				else {
					synchronized (tmpFileQueueLock) {
						tmpFileQueue.remove();
						if(!tmpFileMotionQueue.isEmpty()) tmpFileMotionQueue.remove();
					}
				}
			}
			
			if(maxFolders > 0) {
				List<File> folders = listFiles(storageFolder, FOLDER_PATTERN);
				if(folders != null) {
					while(folders.size() > maxFolders) {
						File folder = folders.remove(0);
						WebcamServer.logger.printLogLn(false, "Deleting folder: " + folder.getName());
						deleteFolderAndContent(folder);
					}
				}
			}
			
			if(reIndex) {
				reIndex = false;
				reindex();
			}
		} catch (Exception e) {
			WebcamServer.logger.printLogException(e);
		}
	}
	
	private File findNewFileName(File folder, String fileName) {
		fileName = fileName.substring(0, fileName.length() - 4);
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
		LocalDateTime dateTime = LocalDateTime.from(formatter.parse(fileName));
		File output = null;
		do {
			fileName = dateTime.format(formatter) + ".mp4";
			output = new File(folder, fileName);
			dateTime = dateTime.plus(1, ChronoUnit.SECONDS);
		} while(output.exists());
		return output;
	}
	
	private void reindex() {
		try {
			List<File> folders = listFiles(storageFolder, FOLDER_PATTERN);
			if(folders != null) {
				for(File folder : folders) {
					WebcamServer.logger.printLogLn(false, "Updating index for folder: " + folder.getName());
					
					File fileList = new File(folder, FILE_LIST_NAME);
					if(fileList.exists()) fileList.delete();
					
					List<File> files = listFiles(folder, FILE_PATTERN);
					if(files != null) {
						for(File file : files) updateFileList(file, folder, false);
					}
				}
			}
			
			WebcamServer.logger.printLogLn(false, "Index update completed");
		} catch (Exception e) {
			WebcamServer.logger.printLogException(e);
		}
	}
	
	private void updateMotionList(File file, File folder, List<Double> motionList) {
		try {
			File fileList = new File(folder, FILE_MOTION_LIST_NAME);
			RandomAccessFile raf = new RandomAccessFile(fileList, "rw");
			
			try {
				if(raf.length() < 3) raf.writeBytes("{\r\n");
				else {
					raf.seek(raf.length() - 3);
					if(raf.readByte() == '\r' && raf.readByte() == '\n' && raf.readByte() == '}') {
						raf.seek(raf.length() - 3);
						raf.writeBytes(",\r\n");
					}
					else {
						WebcamServer.logger.printLogLn(false, "Video motion list for directory " + folder.getName() + " is corrupted");
						long i = raf.length() - 3;
						while(i >= 0) {
							raf.seek(i);
							byte b1 = raf.readByte();
							byte b2 = raf.readByte();
							byte b3 = raf.readByte();
							if(b1 == ',' && b2 == '\r' && b3 == '\n') break;
							if(b1 == ']' && b2 == '\r' && b3 == '\n') {
								i++;
								break;
							}
							i--;
						}
						if(i < 10) {
							raf.seek(0);
							raf.setLength(0);
							raf.writeBytes("{\r\n");
						}
						else {
							raf.setLength(i);
							raf.writeBytes(",\r\n");
						}
					}
				}
				raf.writeBytes("\t\"" + file.getName() + "\":[");
				if(motionList != null) {
					for(int i = 0; i < motionList.size(); i++) {
						raf.writeBytes(String.format("%.6f", motionList.get(i)));
						if(i < motionList.size() - 1) raf.writeBytes(",");
					}
				}
				raf.writeBytes("]\r\n}");
			} catch (Exception e) {
				WebcamServer.logger.printLogException(e);
			}
			
			if(raf != null) raf.close();
		} catch (Exception e) {
			WebcamServer.logger.printLogException(e);
		}
	}
	
	private boolean updateFileList(File file, File folder, boolean deleteIfEmpty) {
		boolean retVal = false;
		try {
			FileInfo fileInfo = ffmpegFileInfo.getFileInfo(file);
			if(fileInfo == null) WebcamServer.logger.printLogLn(false, "Unable to get video info for " + file.getName());
			else if(fileInfo.isEmpty()) {
				WebcamServer.logger.printLogLn(false, "Video " + file.getName() + " is empty, discarding it");
				if(deleteIfEmpty) file.delete();
			}
			else {
				retVal = true;
				WebcamServer.logger.printLogLn(true, "Video info for " + file.getName() + ": " + fileInfo.toString());
				
				File fileList = new File(folder, FILE_LIST_NAME);
				
				RandomAccessFile raf = new RandomAccessFile(fileList, "rw");
				try {
					if(raf.length() < 4) raf.writeBytes("{\"files\":[\r\n");
					else {
						raf.seek(raf.length() - 4);
						if(raf.readByte() == '\r' && raf.readByte() == '\n' && raf.readByte() == ']' && raf.readByte() == '}') {
							raf.seek(raf.length() - 4);
							raf.writeBytes(",\r\n");
						}
						else {
							WebcamServer.logger.printLogLn(false, "Video list for directory " + folder.getName() + " is corrupted");
							long i = raf.length() - 3;
							while(i >= 0) {
								raf.seek(i);
								byte b1 = raf.readByte();
								byte b2 = raf.readByte();
								byte b3 = raf.readByte();
								if(b1 == ',' && b2 == '\r' && b3 == '\n') break;
								if(b1 == '}' && b2 == '\r' && b3 == '\n') {
									i++;
									break;
								}
								i--;
							}
							if(i <= 12) {
								raf.seek(0);
								raf.setLength(0);
								raf.writeBytes("{\"files\":[\r\n");
							}
							else {
								raf.setLength(i);
								raf.writeBytes(",\r\n");
							}
						}
					}
					raf.writeBytes("\t{\"name\":\"" + file.getName() +
							"\",\"duration\":" + String.format("%.5f", fileInfo.getDurationSeconds()) +
							",\"fps\":" + String.format("%.5f", fileInfo.getFps()) + 
							",\"width\":" + fileInfo.getWidth() +
							",\"height\":" + fileInfo.getHeight() +
							"}\r\n]}");
				} catch (Exception e) {
					WebcamServer.logger.printLogException(e);
				}
				
				if(raf != null) raf.close();
			}
		} catch (Exception e) {
			WebcamServer.logger.printLogException(e);
		}
		
		return retVal;
	}
 	
	private void deleteFolderAndContent(File file) {
		try {
			if(file.isDirectory()) {
				for(File f : file.listFiles()) deleteFolderAndContent(f);
			}
			file.delete();
		} catch (Exception e) {
			WebcamServer.logger.printLogException(e);
		}
	}
	
	private void deleteFolderContent(File folder) {
		try {
			if(folder.isDirectory()) {
				for(File file : folder.listFiles()) deleteFolderAndContent(file);
			}
		} catch (Exception e) {
			WebcamServer.logger.printLogException(e);
		}
	}
	
	private boolean matchString(String string, String pattern) {
		if(string == null || pattern == null) return false;
		if(string.length() != pattern.length()) return false;
		for(int i = 0; i < string.length(); i++) {
			if(pattern.charAt(i) == '?') {
				if(string.charAt(i) < '0') return false;
				if(string.charAt(i) > '9') return false;
			}
			else if(pattern.charAt(i) != string.charAt(i)) return false;
		}
		return true;
	}
	
	private List<File> listFiles(File folder, String pattern) {
		try {
			List<File> list = new ArrayList<>();
			File[] files = folder.listFiles();
			if(files != null) {
				for(File file : files) {
					if(matchString(file.getName(), pattern)) list.add(file);
				}
			}
			Collections.sort(list);
			return list;
		} catch (Exception e) {
			WebcamServer.logger.printLogException(e);
		}
		
		return null;
	}
}
