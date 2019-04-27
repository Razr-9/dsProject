package unimelb.bitbox;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.security.NoSuchAlgorithmException;

import unimelb.bitbox.util.Configuration;
import unimelb.bitbox.util.Document;
import unimelb.bitbox.util.FileSystemManager;
import org.json.simple.*;

import unimelb.bitbox.util.FileSystemManager.FileSystemEvent;

class Threads extends Thread{
	BufferedReader In;
	BufferedWriter Out;
	String Msg = null;
	Socket Socket;
	String InputStr = null;
	
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
				if(j < Integer.parseInt(Configuration.getConfigurationValue("maximumIncomingConnections"))) {
					if(Document.parse((Msg = In.readLine())).get("command").equals("HANDSHAKE_REQUEST")) {
						Out.write(new JSON().marshaling("HANDSHAKE_RESPONSE")+"\n");
						Out.flush();
						while((Msg = In.readLine()) != null) {
							//JSON:
							Respond(Msg);
						}
					}
				}
				else {
					Out.write(new JSON().marshaling("CONNECTION_REFUSED")+"\n");
					Out.flush();
				}
			}

			else if (ServerOrClient=="Client") {
				Out.write(new JSON().marshaling("HANDSHAKE_REQUEST")+"\n");
				Out.flush();
				if(Document.parse((Msg = In.readLine())).get("command").equals("HANDSHAKE_RESPONSE")) {
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
				System.out.println(Doc.toJson());
				Out.write(Doc.toJson()+"\n");
				Out.flush();
			}
			
			if(Document.parse(message).get("command").toString().equals("FILE_CREATE_REQUEST")) {
				String pathName = Document.parse(message).get("pathName").toString();
				String fileDes = ((Document) Document.parse(message).get("fileDescriptor")).toJson();
				String md5 = Document.parse(fileDes).get("md5").toString();
//				String pathName = fileSystemManager.loadingSuffix;
				long length = Document.parse(fileDes).getLong("fileSize");
			
				long lastModified = Document.parse(fileDes).getLong("lastModified");
				Document Doc = new Document();
				Doc.append("command", "FILE_CREATE_RESPONSE");
				Doc.append("fileDescriptor", fileDes);
				Doc.append("pathName", pathName);
				if(!fileSystemManager.fileNameExists(pathName)) {
					if(fileSystemManager.isSafePathName(pathName)) {
						try {
							if(fileSystemManager.createFileLoader(pathName, md5, length, lastModified)) {
//								if(fileSystemManager.checkShortcut(pathName)){
									Doc.append("message", "file loader ready");
									Doc.append("status",true);
//								}
						
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
//				System.out.println(fileSystemManager.fileNameExists(pathName,md5));
				System.out.println(Doc.toJson());
				Out.write(Doc.toJson()+"\n");
				Out.flush();
				
			}
			
			//DIRECTORY_CREATE_REQUEST
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public void Request(FileSystemEvent fileSystemEvent) {
		try {/**
		 * A new file has been created. The parent directory must
		 * exist for this event to be emitted.
		 */
		//FILE_CREATE,
			if(fileSystemEvent.event.toString().equals("FILE_CREATE")) {
				System.out.println(fileSystemEvent.event);
				Document Doc = new Document();
				Doc.append("command", "FILE_CREATE_REQUEST");
				Doc.append("pathName", fileSystemEvent.pathName);
				Doc.append("fileDescriptor", fileSystemEvent.fileDescriptor.toDoc());
				Out.write(Doc.toJson()+"\n");
//				Out.write(fileSystemEvent.event);
				Out.flush();
			}
		
		/**
		 * An existing file has been deleted.
		 */
		//FILE_DELETE,
		/**
		 * An existing file has been modified.
		 */
		//FILE_MODIFY,
		/**
		 * A new directory has been created. The parent directory must
		 * exist for this event to be emitted.
		 */
		//DIRECTORY_CREATE,
		/**
		 * An existing directory has been deleted. The directory must
		 * be empty for this event to be emitted, and its parent
		 * directory must exist.
		 */
		//DIRECTORY_DELETE
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
}
