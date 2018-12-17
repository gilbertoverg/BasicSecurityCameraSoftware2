package webcamServer;

import java.io.*;
import java.util.*;
import org.nanohttpd.protocols.http.*;
import org.nanohttpd.protocols.http.response.*;

public class HttpServer extends NanoHTTPD {
	private final FileManager fileManager;
	private final boolean logConnections, jpegStream;
	private final String name;
	private final int jpegWidth, jpegHeight, jpegFrameRate, fileWidth, fileHeight;
	
	public HttpServer(FileManager fileManager, int httpPort, boolean logConnections, String name,
			boolean jpegStream, int jpegWidth, int jpegHeight, int jpegFrameRate,
			int fileWidth, int fileHeight) {
		super(httpPort);
		if(httpPort < 1 || httpPort > 65535) throw new IllegalArgumentException("Port out of range");
		if(fileManager == null) throw new IllegalArgumentException("File manager is not available");
		
		this.fileManager = fileManager;
		this.logConnections = logConnections;
		this.name = name;
		this.jpegStream = jpegStream;
		this.jpegWidth = jpegWidth;
		this.jpegHeight = jpegHeight;
		this.jpegFrameRate = jpegFrameRate;
		this.fileWidth = fileWidth;
		this.fileHeight = fileHeight;
	}
	
	@Override
	public synchronized void start() {
		try {
			WebcamServer.logger.printLogLn(false, "Starting web server");
			super.start();
			WebcamServer.logger.printLogLn(false, "Web server started");
		} catch (Exception e) {
			WebcamServer.logger.printLogException(e);
		}
	}
	
	@Override
	public synchronized void stop() {
		try {
			WebcamServer.logger.printLogLn(false, "Stopping web server");
			super.stop();
			WebcamServer.logger.printLogLn(false, "Web server stopped");
		} catch (Exception e) {
			WebcamServer.logger.printLogException(e);
		}
	}
	
	public synchronized boolean isRunning() {
		return super.isAlive();
	}
	
	public synchronized void waitStop() {
		while(isRunning()) {
			try {
				Thread.sleep(1);
			} catch (InterruptedException e) {

			}
		}
	}
	
	@Override
    public Response serve(IHTTPSession session) {
		if(logConnections) WebcamServer.logger.printLogLn(false, "HTTP request from: " + session.getRemoteIpAddress() + ", uri: " + session.getUri());
		else WebcamServer.logger.printLogLn(true, "HTTP request from: " + session.getRemoteIpAddress() + ", uri: " + session.getUri());
		
		try {
			if(session.getUri().equals("/data/frame")) {
				byte[] jpeg = fileManager.getLastJpeg();
				if(jpeg == null) return Response.newFixedLengthResponse(Status.SERVICE_UNAVAILABLE, NanoHTTPD.MIME_PLAINTEXT, "SERVICE UNAVAILABLE");
				ByteArrayInputStream bais = new ByteArrayInputStream(jpeg);
				Response response = Response.newFixedLengthResponse(Status.OK, "image/jpeg", bais, bais.available());
				response.addHeader("Cache-Control", "no-cache, no-store, must-revalidate");
				response.addHeader("Pragma", "no-cache");
				response.addHeader("Expires", "0");
				return response;
			}
			if(session.getUri().equals("/data/folderList")) {
				String[] folders = fileManager.getFolders();
				if(folders == null) return Response.newFixedLengthResponse(Status.SERVICE_UNAVAILABLE, NanoHTTPD.MIME_PLAINTEXT, "SERVICE UNAVAILABLE");
				String json = "{\"folders\":[";
				for(int i = 0; i < folders.length; i++) {
					json += "\"" + folders[i] + "\"";
					if(i < folders.length - 1) json += ",";
				}
				json += "]}";
				return Response.newFixedLengthResponse(Status.OK, NanoHTTPD.MIME_PLAINTEXT, json);
			}
			if(session.getUri().equals("/data/fileList")) {
				List<String> folders = session.getParameters().get("folder");
				if(folders == null || folders.size() != 1) return Response.newFixedLengthResponse(Status.BAD_REQUEST, NanoHTTPD.MIME_PLAINTEXT, "BAD REQUEST");
				String folder = folders.get(0);
				String[] files = fileManager.getFolderFiles(folder);
				if(files == null) return Response.newFixedLengthResponse(Status.SERVICE_UNAVAILABLE, NanoHTTPD.MIME_PLAINTEXT, "SERVICE UNAVAILABLE");
				String json = "{\"files\":[";
				for(int i = 0; i < files.length; i++) {
					json += "\"" + files[i] + "\"";
					if(i < files.length - 1) json += ",";
				}
				json += "]}";
				return Response.newFixedLengthResponse(Status.OK, NanoHTTPD.MIME_PLAINTEXT, json);
			}
			if(session.getUri().equals("/data/file")) {
				List<String> folders = session.getParameters().get("folder");
				if(folders == null || folders.size() != 1) return Response.newFixedLengthResponse(Status.BAD_REQUEST, NanoHTTPD.MIME_PLAINTEXT, "BAD REQUEST");
				List<String> files = session.getParameters().get("file");
				if(files == null || files.size() != 1) return Response.newFixedLengthResponse(Status.BAD_REQUEST, NanoHTTPD.MIME_PLAINTEXT, "BAD REQUEST");
				String folder = folders.get(0);
				String file = files.get(0);
				File f = fileManager.getFileInFolder(file, folder);
				if(f == null) return Response.newFixedLengthResponse(Status.SERVICE_UNAVAILABLE, NanoHTTPD.MIME_PLAINTEXT, "SERVICE UNAVAILABLE");
				return serveFile(session, f, "video/mp4");
			}
			if(session.getUri().equals("/data/config")) {
				String json = "{\"title\":\"" + name +
						"\",\"liveWidth\":" + Integer.toString(jpegWidth) +
						",\"liveHeight\":" + Integer.toString(jpegHeight) +
						",\"liveFrameRate\":" + Integer.toString(jpegFrameRate) + 
						",\"historyWidth\":" + Integer.toString(fileWidth) +
						",\"historyHeight\":" + Integer.toString(fileHeight) + "}";
				return Response.newFixedLengthResponse(Status.OK, NanoHTTPD.MIME_PLAINTEXT, json);
			}
			if(session.getUri().equals("/")) {
				if(jpegStream) {
					InputStream in = WebcamServer.class.getResourceAsStream("/www/live.html");
					return Response.newFixedLengthResponse(Status.OK, NanoHTTPD.MIME_HTML, in, in.available());
				}
				else {
					InputStream in = WebcamServer.class.getResourceAsStream("/www/history.html");
					return Response.newFixedLengthResponse(Status.OK, NanoHTTPD.MIME_HTML, in, in.available());
				}
			}
			if(session.getUri().indexOf('/', 1) < 0 && session.getUri().indexOf('\\') < 0 && session.getUri().indexOf("..") < 0) {
				InputStream in = WebcamServer.class.getResourceAsStream("/www" + session.getUri());
				if(in == null) return Response.newFixedLengthResponse(Status.NOT_FOUND, NanoHTTPD.MIME_PLAINTEXT, "NOT FOUND");
				else return Response.newFixedLengthResponse(Status.OK, NanoHTTPD.MIME_HTML, in, in.available());
			}
			return Response.newFixedLengthResponse(Status.NOT_FOUND, NanoHTTPD.MIME_PLAINTEXT, "NOT FOUND");
		} catch (Exception e) {
			WebcamServer.logger.printLogException(e);
		}
		
		return Response.newFixedLengthResponse(Status.INTERNAL_ERROR, NanoHTTPD.MIME_PLAINTEXT, "Internal error");
	}
	
	private Response serveFile(IHTTPSession session, File file, String mime) {
		try {
			Response res;
			
			String etag = Integer.toHexString((file.getAbsolutePath() + file.lastModified() + file.length()).hashCode());
			
			long startFrom = 0;
            long endAt = -1;
            String range = session.getHeaders().get("range");
            if(range != null) {
                if(range.startsWith("bytes=")) {
                    range = range.substring(6);
                    int minus = range.indexOf('-');
                    try {
                        if(minus > 0) {
                            startFrom = Long.parseLong(range.substring(0, minus));
                            endAt = Long.parseLong(range.substring(minus + 1));
                        }
                    } catch (NumberFormatException e) {
                    	
                    }
                }
            }
            
            String ifRange = session.getHeaders().get("if-range");
            boolean headerIfRangeMissingOrMatching = ifRange == null || etag.equals(ifRange);
            
            String ifNoneMatch = session.getHeaders().get("if-none-match");
            boolean headerIfNoneMatchPresentAndMatching = ifNoneMatch != null && ("*".equals(ifNoneMatch) || ifNoneMatch.equals(etag));
            
            long fileLen = file.length();
            
            if(headerIfRangeMissingOrMatching && range != null && startFrom >= 0 && startFrom < fileLen) {
                if(headerIfNoneMatchPresentAndMatching) {
                    res = Response.newFixedLengthResponse(Status.NOT_MODIFIED, mime, "");
                    res.addHeader("ETag", etag);
                }
                else {
                    if(endAt < 0) endAt = fileLen - 1;
                    long newLen = endAt - startFrom + 1;
                    if(newLen < 0) newLen = 0;

                    FileInputStream fis = new FileInputStream(file);
                    fis.skip(startFrom);

                    res = Response.newFixedLengthResponse(Status.PARTIAL_CONTENT, mime, fis, newLen);
                    res.addHeader("Accept-Ranges", "bytes");
                    res.addHeader("Content-Length", Long.toString(newLen));
                    res.addHeader("Content-Range", "bytes " + startFrom + "-" + endAt + "/" + fileLen);
                    res.addHeader("ETag", etag);
                }
            }
            else {
                if(headerIfRangeMissingOrMatching && range != null && startFrom >= fileLen) {
                    res = Response.newFixedLengthResponse(Status.RANGE_NOT_SATISFIABLE, NanoHTTPD.MIME_PLAINTEXT, "");
                    res.addHeader("Content-Range", "bytes */" + fileLen);
                    res.addHeader("ETag", etag);
                }
                else if(range == null && headerIfNoneMatchPresentAndMatching) {
                    res = Response.newFixedLengthResponse(Status.NOT_MODIFIED, mime, "");
                    res.addHeader("ETag", etag);
                }
                else if(!headerIfRangeMissingOrMatching && headerIfNoneMatchPresentAndMatching) {
                    res = Response.newFixedLengthResponse(Status.NOT_MODIFIED, mime, "");
                    res.addHeader("ETag", etag);
                }
                else {
                	res = Response.newFixedLengthResponse(Status.OK, mime, new FileInputStream(file), (int)file.length());
                    res.addHeader("Accept-Ranges", "bytes");
                    res.addHeader("Content-Length", Long.toString(fileLen));
                    res.addHeader("ETag", etag);
                }
            }
            
            return res;
		} catch (Exception e) {
			WebcamServer.logger.printLogException(e);
		}
		
		return Response.newFixedLengthResponse(Status.INTERNAL_ERROR, NanoHTTPD.MIME_PLAINTEXT, "Internal error");
	}
}
