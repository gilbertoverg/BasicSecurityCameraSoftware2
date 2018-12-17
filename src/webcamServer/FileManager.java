package webcamServer;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import listeners.*;

public class FileManager implements JpegListener {
	private final String TMP_FILE_PATTERN = "TMP_????-??-??_??-??-??.mp4";
	private final String FILE_PATTERN = "????-??-??_??-??-??.mp4";
	private final String FOLDER_PATTERN = "????-??-??";
	
	private final File storageFolder;
	private final int maxFolders;
	private final boolean enableJpeg;
	
	private volatile Thread thread = null;
	private volatile boolean killThread = false;
	
	private volatile byte[] lastJpeg = null;

	public FileManager(File storageFolder, int maxFolders, boolean enableJpeg) {
		this.storageFolder = storageFolder;
		this.maxFolders = maxFolders;
		this.enableJpeg = enableJpeg;
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
	
	public String[] getFolderFiles(String folderName) {
		try {
			if(storageFolder == null) return null;
			if(!matchString(folderName, FOLDER_PATTERN)) return null;
			File folder = new File(storageFolder, folderName);
			List<File> files = listFiles(folder, FILE_PATTERN);
			if(files == null) return null;
			String[] fileNames = new String[files.size()];
			for(int i = 0; i < fileNames.length; i++) fileNames[i] = files.get(i).getName();
			return fileNames;
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
	
	public byte[] getLastJpeg() {
		if(!enableJpeg) return null;
		return lastJpeg;
	}
	
	@Override
	public void newJpeg(byte[] jpeg) {
		lastJpeg = jpeg;
	}
	
	private void manageTask(boolean finalize) {
		try {
			File tmp = new File(storageFolder, "tmp");
			
			List<File> tmpFiles = listFiles(tmp, TMP_FILE_PATTERN);
			if(tmpFiles != null) {
				while(tmpFiles.size() >= (finalize ? 1 : 2)) {
					File tmpFile = tmpFiles.remove(0);
					
					Thread.sleep(500);
					
					File newFolder = new File(storageFolder, tmpFile.getName().substring(4, 14));
					if(!newFolder.exists()) {
						WebcamServer.logger.printLogLn(false, "Creating folder: " + newFolder.getName());
						newFolder.mkdir();
					}
					
					File newFile = new File(newFolder, tmpFile.getName().substring(4));
					WebcamServer.logger.printLogLn(true, "Moving file: " + newFile.getName());
					Files.move(tmpFile.toPath(), newFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
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
