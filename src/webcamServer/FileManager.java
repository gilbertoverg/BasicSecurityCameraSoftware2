package webcamServer;

import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.time.format.*;
import java.time.temporal.*;
import java.util.*;
import listeners.*;

public class FileManager implements JpegListener, NewTmpFileListener {
	private final String TMP_FILE_PATTERN = "TMP_????-??-??_??-??-??.mp4";
	private final String FILE_PATTERN = "????-??-??_??-??-??.mp4";
	private final String FOLDER_PATTERN = "????-??-??";
	private final String FILE_LIST_NAME = "FileList.txt";
	
	private final FFmpegFileInfo ffmpegFileInfo;
	private final FFmpegFrameGrabber ffmpegFrameGrabber;
	
	private final File storageFolder;
	private final int maxFolders;
	private final boolean enableJpeg;
	
	private volatile Thread thread = null;
	private volatile boolean killThread = false, reIndex = false;
	
	private volatile File tmpFileForceFirst = null;
	private volatile Object tmpFileForceFirstLock = new Object();
	
	private volatile byte[] lastJpeg = null;
	private volatile String currentTmpFile = null;
	
	private volatile NewFileListener newFileListener = null;

	public FileManager(File ffmpeg, File storageFolder, int maxFolders, int timelineQuality, boolean enableJpeg) {
		this.ffmpegFileInfo = new FFmpegFileInfo(ffmpeg);
		if(storageFolder != null) this.ffmpegFrameGrabber = new FFmpegFrameGrabber(ffmpeg, timelineQuality);
		else this.ffmpegFrameGrabber = null;
		this.storageFolder = storageFolder;
		this.maxFolders = maxFolders;
		this.enableJpeg = enableJpeg;
	}
	
	public synchronized void setNewFileListener(NewFileListener newFileListener) {
		this.newFileListener = newFileListener;
	}
	
	public void timeChanged() {
		synchronized (tmpFileForceFirstLock) {
			tmpFileForceFirst = null;
			if(storageFolder == null) return;
			try {
				LocalDateTime now = LocalDateTime.now();
				File tmp = new File(storageFolder, "tmp");
				List<File> tmpFiles = listFiles(tmp, TMP_FILE_PATTERN);
				if(tmpFiles != null) {
					DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
					long maxDistance = -1;
					for(File f : tmpFiles) {
						LocalDateTime fileTime = LocalDateTime.from(formatter.parse(f.getName().subSequence(4, 23)));
						long distance = Math.abs(ChronoUnit.SECONDS.between(now, fileTime));
						if(distance > maxDistance) {
							maxDistance = distance;
							tmpFileForceFirst = f;
						}
					}
				}
			} catch (Exception e) {
				WebcamServer.logger.printLogException(e);
			}
		}
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
			
			killThread = false;
			thread = new Thread() {
				public void run() {
					try {
						WebcamServer.logger.printLogLn(false, "File manager started");

						while(!killThread) {
							manageTask(false);

							for(int i = 0; i < 100 && !killThread; i++) {
								Thread.sleep(10);
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
		currentTmpFile = file;
	}
	
	public void activateReIndex() {
		reIndex = true;
	}
	
	private void manageTask(boolean finalize) {
		try {
			synchronized (tmpFileForceFirstLock) {
				File tmp = new File(storageFolder, "tmp");
				List<File> tmpFiles = listFiles(tmp, TMP_FILE_PATTERN);
				if(tmpFiles != null) {
					if(tmpFileForceFirst != null) {
						tmpFiles.add(0, tmpFileForceFirst);
						for(int i = 1; i < tmpFiles.size(); i++) {
							if(tmpFiles.get(i).getName().equals(tmpFileForceFirst.getName())) {
								tmpFiles.remove(i);
								break;
							}
						}
					}
					while(tmpFiles.size() >= (finalize ? 1 : 2)) {
						File tmpFile = tmpFiles.remove(0);
						
						Thread.sleep(500);
						
						String currentTmpFile = this.currentTmpFile;
						if(!finalize && currentTmpFile != null && currentTmpFile.equals(tmpFile.getName())) {
							WebcamServer.logger.printLogLn(true, "File " + tmpFile.getName() + " is locked");
							return;
						}
						
						tmpFileForceFirst = null;
						
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
						
						updateFileList(newFile, newFolder);
						
						NewFileListener nfl = newFileListener;
						if(nfl != null) nfl.newFile(newFile);
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
						for(File file : files) updateFileList(file, folder);
					}
				}
			}
			
			WebcamServer.logger.printLogLn(false, "Index update completed");
		} catch (Exception e) {
			WebcamServer.logger.printLogException(e);
		}
	}
	
	private void updateFileList(File file, File folder) {
		try {
			FileInfo fileInfo = ffmpegFileInfo.getFileInfo(file);
			if(fileInfo == null) WebcamServer.logger.printLogLn(false, "Unable to get video info for " + file.getName());
			else {
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
