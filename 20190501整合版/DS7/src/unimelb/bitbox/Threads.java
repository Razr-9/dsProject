package unimelb.bitbox;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.math.BigInteger;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Timer;
import java.util.TimerTask;

import unimelb.bitbox.util.Configuration;
import unimelb.bitbox.util.Document;
import unimelb.bitbox.util.FileSystemManager;
import unimelb.bitbox.util.FileSystemManager.FileSystemEvent;

class Threads extends Thread{
	BufferedReader In;
	BufferedWriter Out;
	String Msg = null;
	Socket Socket;
	String InputStr = null;
	int syncInterval = Integer.parseInt(Configuration.getConfigurationValue("syncInterval"));
	int blockSize = Integer.parseInt(Configuration.getConfigurationValue("blockSize"));
	
	String ServerOrClient;
	FileSystemManager fileSystemManager;
	FileSystemEvent fileSystemEvent;
	int j;
	
	public Threads(Socket Socket, int j, String ServerOrClient, FileSystemManager fileSystemManager) {
		try {
		//Get the input/output streams for reading/writing data from/to the socket
			In = new BufferedReader(new InputStreamReader(Socket.getInputStream(), "UTF-8"));
			Out = new BufferedWriter(new OutputStreamWriter(Socket.getOutputStream(), "UTF-8"));
			this.ServerOrClient = ServerOrClient;
			this.Socket = Socket;
			this.fileSystemManager = fileSystemManager;
			this.j = j;
		} catch (SocketException ex) {
			ex.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} 
		start();
	}
	
	public void run() {
		//Read the message from the client and reply
		//Notice that no other connection can be accepted and processed until the last line of 
		//code of this loop is executed, incoming connections have to wait until the current
		//one is processed unless...we use threads!
		try {
			if(ServerOrClient=="Server") {
				int max = Integer.parseInt(Configuration.getConfigurationValue("maximumIncomingConnections"));
				if(j < max) {
					if(Document.parse((Msg = In.readLine())).get("command").equals("HANDSHAKE_REQUEST")) {
						//HANDSHAKE_RESPONSE
						Document Doc = new Document();
						Document hostPort = new Document();
						Doc.append("command","HANDSHAKE_RESPONSE");
						hostPort.append("port", Integer.parseInt(Configuration.getConfigurationValue("port")));
						hostPort.append("host", Configuration.getConfigurationValue("advertisedName"));
						Doc.append("hostPort",hostPort);
						Out.write(Doc.toJson()+"\n");
						Out.flush();
						
						//Synchronous
						Timer timer = new Timer();
						timer.schedule(new TimerTask() {
						        public void run() {
						        	System.out.println("Synchronizing...");
						        	ArrayList<FileSystemEvent> eventSync = fileSystemManager.generateSyncEvents();
									try {
										sendSyncRequest(eventSync);
									} catch (IOException e) {
										e.printStackTrace();
									}
									System.out.println("Synchronization finished");
						        }
						}, 0 , syncInterval*1000);
						
						//Respond
						while((Msg = In.readLine()) != null) {
							//JSON:
							Respond(Msg);
						}
					}
				}
				else {
					//CONNECTION_REFUSED
					Document Doc = new Document();
					ArrayList<Document> peers = new ArrayList<Document>();
					Doc.append("command","CONNECTION_REFUSED");
					Doc.append("message","connection limit reached");
					for(int i =0;i<max;i++) {
						if(ServerMain.th[i]!=null && ServerMain.th[i].isAlive()){
							Document peer = new Document();
							peer.append("host",ServerMain.th[i].Socket.getInetAddress().getHostName());
							peer.append("port",ServerMain.th[i].Socket.getPort());
							peers.add(peer);
						}
					}
					Doc.append("peers",peers);
					Out.write(Doc.toJson()+"\n");
					Out.flush();
				}
			}
			else if (ServerOrClient=="Client") {
				//HANDSHAKE_REQUEST
				Document Doc = new Document();
				Document hostPort = new Document();
				Doc.append("command","HANDSHAKE_REQUEST");
				hostPort.append("port", Integer.parseInt(Configuration.getConfigurationValue("port")));
				hostPort.append("host", Configuration.getConfigurationValue("advertisedName"));
				Doc.append("hostPort",hostPort);
				Out.write(Doc.toJson()+"\n");
				Out.flush();
				
				//Respond
				if(Document.parse((Msg = In.readLine())).get("command").equals("HANDSHAKE_RESPONSE")) {
					//Asynchronous
					Timer timer = new Timer();
					timer.schedule(new TimerTask() {
					        public void run() {
					        	System.out.println("Synchronizing...");
					        	ArrayList<FileSystemEvent> eventSync = fileSystemManager.generateSyncEvents();
								try {
									sendSyncRequest(eventSync);
								} catch (IOException e) {
									e.printStackTrace();
								}
								System.out.println("Synchronization finished");
					        }
					}, 0 , syncInterval*1000);
					
					while(!Document.parse((Msg = In.readLine())).get("command").equals("INVALID_PROTOCOL")) {
						//JSON:
						Respond(Msg);
					}
				}
			}
			Socket.close();
		} catch (SocketException ex) {
			ex.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void sendSyncRequest(ArrayList<FileSystemEvent> eventSync) throws IOException {
		for(int i=0;i<eventSync.size();i++) {
			String event = eventSync.get(i).event.toString();
			if(event.equals("FILE_CREATE")) {
				File file = new File(Configuration.getConfigurationValue("path"));
				find(file,eventSync.get(i).pathName);
			}
			if(event.equals("DIRECTORY_CREATE")) {
				Document Doc = new Document();
				Doc.append("command", "DIRECTORY_CREATE_REQUEST");
				Doc.append("pathName", eventSync.get(i).pathName);
				Out.write(Doc.toJson()+"\n");
				Out.flush();
			}
		}
	}

	private void find(File file,String pathName) throws IOException {
//		System.out.println(pathName);
		Document fileDes = new Document();
		Document Doc = new Document();
		Doc.append("command", "FILE_CREATE_REQUEST");
		Doc.append("pathName", pathName);
		File[] fl = file.listFiles();
		for(int j =0;j<fl.length;j++) {
			if(fl[j].isDirectory()){
				find(fl[j],pathName);
			}
			String[] a= fl[j].getPath().toString().split("\\\\",2);
			if(fl[j].isFile()) {
				if(a[1].toString().equals(pathName)) {
					fileDes.append("fileSize",fl[j].length());
					fileDes.append("lastModified",fl[j].lastModified());
					String md5 = getMd5(fl[j].getPath());
					fileDes.append("md5", md5);
					Doc.append("fileDescriptor", fileDes);
					System.out.println(Doc.toJson());
					Out.write(Doc.toJson()+"\n");
					Out.flush();
				}
			}
	}
}
	public static String getMd5(String path) {
		BigInteger bi = null;
		try {
			byte[] buffer = new byte[8192];
			int len = 0;
			MessageDigest md = MessageDigest.getInstance("MD5");
			File f = new File(path);
			FileInputStream fis = new FileInputStream(f);
			while ((len = fis.read(buffer)) != -1) {
				md.update(buffer, 0, len);
			}
			    fis.close();
			    byte[] b = md.digest();
			    bi = new BigInteger(1, b);
			} catch (NoSuchAlgorithmException e) {
			    e.printStackTrace();
			} catch (IOException e) {
			    e.printStackTrace();
		}
		return bi.toString(16);
	}
	
	
	public void Respond(String message){
		try {
			//DIRECTORY_CREATE_REQUEST -> DIRECTORY_CREATE_RESPONSE
			if(Document.parse(message).get("command").equals("DIRECTORY_CREATE_REQUEST")) {
				String pathName = Document.parse(message).get("pathName").toString();
				Document Doc = new Document();
				Doc.append("command", "DIRECTORY_CREATE_RESPONSE");
				Doc.append("pathName", pathName);
				if(!fileSystemManager.dirNameExists(pathName)){
					if(fileSystemManager.isSafePathName(pathName)) {
						if(fileSystemManager.makeDirectory(pathName)) {
							Doc.append("message", "directory created");
							Doc.append("status", true);
						}
						else {
							Doc.append("message", "there was a problem creating the directory");
							Doc.append("status", false);
						}
					}
					else {
						Doc.append("message", "unsafe pathname given");
						Doc.append("status", false);
					}
				}
				else {
					Doc.append("message", "pathname already exists");
					Doc.append("status", false);
				}
				Out.write(Doc.toJson()+"\n");
				Out.flush();

			}
			
			//DIRECTORY_DELETE_REQUEST -> DIRECTORY_DELETE_RESPONSE
			if(Document.parse(message).get("command").equals("DIRECTORY_DELETE_REQUEST")) {
				String pathName = Document.parse(message).get("pathName").toString();
				Document Doc = new Document();
				Doc.append("command", "DIRECTORY_DELETE_RESPONSE");
				Doc.append("pathName", pathName);
				if(fileSystemManager.dirNameExists(pathName)){
					if(fileSystemManager.isSafePathName(pathName)) {
						if(fileSystemManager.deleteDirectory(pathName)) {
							Doc.append("message", "directory deleted");
							Doc.append("status", true);
						}
						else {
							Doc.append("message", "there was a problem creating the directory");
							Doc.append("status", false);
						}
					}
					else {
						Doc.append("message", "unsafe pathname given");
						Doc.append("status", false);
					}
				}
				else {
					Doc.append("message", "pathname does not exists");
					Doc.append("status", false);
				}
				Out.write(Doc.toJson()+"\n");
				Out.flush();
			}
			
			if(Document.parse(message).get("command").equals("DIRECTORY_CREATE_RESPONSE")) {
				return;
			}
			if(Document.parse(message).get("command").equals("DIRECTORY_DELETE_RESPONSE")) {
				return;
			}
			if(Document.parse(message).get("command").equals("FILE_DELETE_RESPONSE")) {
				return;
			}
			if(Document.parse(message).get("command").equals("FILE_CREATE_RESPONSE")) {
				return;
			}
			
			if(Document.parse(message).get("command").toString().equals("FILE_CREATE_REQUEST")) {
				boolean ready = false;
				String pathName = Document.parse(message).get("pathName").toString();
				String fileDes = ((Document) Document.parse(message).get("fileDescriptor")).toJson();
				String md5 = Document.parse(fileDes).get("md5").toString();
				long fileSize = Document.parse(fileDes).getLong("fileSize");
				long lastModified = Document.parse(fileDes).getLong("lastModified");
				Document Doc = new Document();
				Doc.append("command", "FILE_CREATE_RESPONSE");
				Doc.append("fileDescriptor", Document.parse(fileDes));
				Doc.append("pathName", pathName);
				if(!fileSystemManager.fileNameExists(pathName)) {
					if(fileSystemManager.isSafePathName(pathName)) {
						try {
							if(fileSystemManager.createFileLoader(pathName, md5, fileSize, lastModified)) {
									Doc.append("message", "file loader ready");
									Doc.append("status",true);
									ready = true;
							}else {// unsuccessfully create
								Doc.append("message", "there was a problem creating the file");
								Doc.append("status",false);
							}
						} catch (NoSuchAlgorithmException e) {
							e.printStackTrace();
						}
					}else {//unsave pathname
						Doc.append("message", "unsafe pathname given");
						Doc.append("status",false);
					}
					
				}else {//filename exists
					Doc.append("message", "pathname already exists");
					Doc.append("status",false);
				}
				Out.write(Doc.toJson()+"\n");
				Out.flush();
				
				if(ready) {
					Document Doc1 = new Document();
					Doc1.append("command", "FILE_BYTES_REQUEST");
					Doc1.append("fileDescriptor", Document.parse(fileDes));
					
					Doc1.append("pathName", pathName);
					Doc1.append("position", (long) 0);
					if(fileSize<blockSize){// determine the number of request bytes
						Doc1.append("length",fileSize);
					}else {
						Doc1.append("length", blockSize);
					}
					Out.write(Doc1.toJson()+"\n");
					Out.flush();
				}
			}

			/* 
				FILE_MODIFY_REQUEST -> FILE_MODIFY_RESPONSE
			*/
			if(Document.parse(message).get("command").equals("FILE_MODIFY_REQUEST")) {
				String pathName = Document.parse(message).get("pathName").toString();
				String fileDes = ((Document) Document.parse(message).get("fileDescriptor")).toJson();
				String md5 = Document.parse(fileDes).get("md5").toString();
				long lastModified = Document.parse(fileDes).getLong("lastModified");
				long fileSize = Document.parse(fileDes).getLong("fileSize");

				Document Doc = new Document();

				Doc.append("command", "FILE_MODIFY_RESPONSE");
				Doc.append("pathName", pathName);
				Doc.append("fileDescriptor", Document.parse(fileDes));
				if(!fileSystemManager.fileNameExists(pathName, md5)){
					if(fileSystemManager.isSafePathName(pathName)) {
						try {
							if(fileSystemManager.modifyFileLoader(pathName, md5, lastModified)){
								Doc.append("message", "file loader ready");
								Doc.append("status", true);
							}else{
								Doc.append("message", "File loading fail");
								Doc.append("status", false);
							}
						} catch (IOException e) {
							e.printStackTrace();
						}
					}else{ // unsafe pathname
						Doc.append("message", "unsafe pathname");
						Doc.append("status", false);
					}
				}else{ // file does not exist
					Doc.append("message", "pathname does not exist");
					Doc.append("status", false);
				}
				Out.write(Doc.toJson() + "\n");
				Out.flush();

				if(Doc.getBoolean("status")) {
					Document Doc1 = new Document();
					Doc1.append("command", "FILE_BYTES_REQUEST");
					Doc1.append("fileDescriptor", Document.parse(fileDes));
					Doc1.append("pathName", pathName);
					Doc1.append("position", (long) 0);
					if(fileSize<blockSize){// determine the number of request bytes
						Doc1.append("length",fileSize);
					}else {
						Doc1.append("length", blockSize);
					}
					System.out.println("Send");
					Out.write(Doc1.toJson()+"\n");
					Out.flush();
				}
			}
			
			if(Document.parse(message).get("command").toString().equals("FILE_BYTES_REQUEST")) {
				String pathName = Document.parse(message).get("pathName").toString();
				String fileDes = ((Document) Document.parse(message).get("fileDescriptor")).toJson();
				String md5 = Document.parse(fileDes).get("md5").toString();
				long length = Document.parse(message).getLong("length");
				long position = Document.parse(message).getLong("position");
				Document Doc = new Document();
				Doc.append("command", "FILE_BYTES_RESPONSE");
				Doc.append("fileDescriptor", Document.parse(fileDes));
				Doc.append("pathName", pathName);

				try {
					if(fileSystemManager.fileNameExists(pathName, md5)) {
						ByteBuffer content = fileSystemManager.readFile(md5, position, length);
						if(content != null) {
							String src = Base64.getEncoder().encodeToString(content.array());
							Doc.append("position", position);
							Doc.append("length", length);
							Doc.append("content", src);
							Doc.append("message", "successful read");
							Doc.append("status", true);
						}
						else {
							Doc.append("message", "unsuccessful read");
							Doc.append("status",false);
						}
					}else {// file name and content don't exist
						Doc.append("message", "unsuccessful read");
						Doc.append("status",false);
					}
					Out.write(Doc.toJson()+"\n");
					Out.flush();
				} catch (NoSuchAlgorithmException e) {
					e.printStackTrace();
				}
			}
			
			if(Document.parse(message).get("command").toString().equals("FILE_BYTES_RESPONSE")) {
				String pathName = Document.parse(message).get("pathName").toString();
				String fileDes = ((Document) Document.parse(message).get("fileDescriptor")).toJson();
				long length = Document.parse(message).getLong("length");
				long position = Document.parse(message).getLong("position");
				long fileSize = Document.parse(fileDes).getLong("fileSize");
				String src = Document.parse(message).get("content").toString();
				
				byte[] content = Base64.getDecoder().decode(src);
				
				ByteBuffer content1 = ByteBuffer.wrap(content);

				if(fileSystemManager.writeFile(pathName, content1, position)) {
					try {
						if(fileSystemManager.checkWriteComplete(pathName)) {
							System.out.println("Write already done");
						}else {
							System.out.println("Already transfered"+ (length + position) + "bytes"+" this time:"+src);
							Document Doc1 = new Document();
							Doc1.append("command", "FILE_BYTES_REQUEST");
							Doc1.append("fileDescriptor", Document.parse(fileDes));
							Doc1.append("pathName", pathName);
							Doc1.append("position", position+length);
							if(fileSize-position-length<blockSize){// determine the number of request bytes
								Doc1.append("length",fileSize-position-length);
							}else {
								Doc1.append("length", blockSize);
							}
							Out.write(Doc1.toJson()+"\n");
							Out.flush();
						}
					} catch (NoSuchAlgorithmException e) {
						e.printStackTrace();
					}
				}else {
					System.out.println("there was no associated file loader for the given name.");
				}
			}
			
			// Delete file
			if(Document.parse(message).get("command").toString().equals("FILE_DELETE_REQUEST")) {
				String pathName = Document.parse(message).get("pathName").toString();
				String fileDes = ((Document) Document.parse(message).get("fileDescriptor")).toJson();
				String md5 = Document.parse(fileDes).get("md5").toString();
				long lastModified = Document.parse(fileDes).getLong("lastModified");
				Document Doc = new Document();
				Doc.append("command", "FILE_DELETE_RESPONSE");
				Doc.append("pathName", pathName);
				if(fileSystemManager.fileNameExists(pathName)){
					if(fileSystemManager.isSafePathName(pathName)) {
						if(fileSystemManager.deleteFile(pathName, lastModified, md5)) {
							Doc.append("message", "file deleted");
							Doc.append("status", true);
						}
						else {
							Doc.append("message", "there was a problem deleting the file");
							Doc.append("status", false);
						}
					}
					else {
						Doc.append("message", "unsafe pathname given");
						Doc.append("status", false);
					}
				}
				else {
					Doc.append("message", "pathname does not exists");
					Doc.append("status", false);
				}
				Out.write(Doc.toJson()+"\n");
				Out.flush();
			}
			
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public void Request(FileSystemEvent fileSystemEvent) {
		try {
	    /**
		 * A new file has been created. The parent directory must
		 * exist for this event to be emitted.
		 */
		//FILE_CREATE,
			if(fileSystemEvent.event.toString().equals("FILE_CREATE")) {
				Document Doc = new Document();
				Doc.append("command", "FILE_CREATE_REQUEST");
				Doc.append("pathName", fileSystemEvent.pathName);
				Doc.append("fileDescriptor", fileSystemEvent.fileDescriptor.toDoc());
				Out.write(Doc.toJson()+"\n");
				Out.flush();
			}
		/**
		 * An existing file has been modified.
		 */
		//File_MODIFY,
			if (fileSystemEvent.event.toString().equals("FILE_MODIFY")) {
				Document Doc = new Document();
				Doc.append("command", "FILE_MODIFY_REQUEST");
				Doc.append("pathName", fileSystemEvent.pathName);
				Doc.append("fileDescriptor", fileSystemEvent.fileDescriptor.toDoc());
				Out.write(Doc.toJson() + "\n");
				Out.flush();
			}
		/**
		 * An existing file has been deleted.
		 */
		//FILE_DELETE,
			if(fileSystemEvent.event.toString().equals("FILE_DELETE")) {
				Document Doc = new Document();
				Doc.append("command", "FILE_DELETE_REQUEST");
				Doc.append("fileDescriptor", fileSystemEvent.fileDescriptor.toDoc());
				Doc.append("pathName", fileSystemEvent.pathName);
				Out.write(Doc.toJson()+"\n");
				Out.flush();
			}
		/**
		 * A new directory has been created. The parent directory must
		 * exist for this event to be emitted.
		 */	
		//DIRECTORY_CREATE,
			if(fileSystemEvent.event.toString().equals("DIRECTORY_CREATE")) {
				Document Doc = new Document();
				Doc.append("command", "DIRECTORY_CREATE_REQUEST");
				Doc.append("pathName", fileSystemEvent.pathName);
				Out.write(Doc.toJson()+"\n");
				Out.flush();
			}
		/**
		 * An existing directory has been deleted. The directory must
		 * be empty for this event to be emitted, and its parent
		 * directory must exist.
		 */
		//DIRECTORY_DELETE,
			if(fileSystemEvent.event.toString().equals("DIRECTORY_DELETE")) {
				Document Doc = new Document();
				Doc.append("command", "DIRECTORY_DELETE_REQUEST");
				Doc.append("pathName", fileSystemEvent.pathName);
				Out.write(Doc.toJson()+"\n");
				Out.flush();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
}
