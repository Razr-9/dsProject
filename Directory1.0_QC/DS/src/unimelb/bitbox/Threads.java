package unimelb.bitbox;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.net.SocketException;
import java.security.NoSuchAlgorithmException;

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
	
	String ServerOrClient;
	FileSystemManager fileSystemManager;
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
						System.out.println(Msg);
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

	public void Respond(String message) {
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
			/* 
				FILE_MODIFY_REQUEST -> FILE_MODIFY_RESPONSE
			*/
			if(Document.parse(message).get("command").equals("FILE_MODIFY_REQUEST")) {
				String pathName = Document.parse(message).get("pathName").toString();
				String fileDes = ((Document) Document.parse(message).get("fileDescriptor")).toJson();
				String md5 = Document.parse(fileDes).get("md5").toString();
				long lastModified = Document.parse(fileDes).getLong("lastModified");
				// long fileSize = Document.parse(fileDes).getLong("fileSize");

				Document Doc = new Document();
			
				Doc.append("command", "FILE_MODIFY_RESPONSE");
				Doc.append("pathName", pathName);
				Doc.append("fileDescriptor", fileDes);
				if(fileSystemManager.fileNameExists(pathName, md5)){
					if(fileSystemManager.isSafePathName(pathName)) {
						try {
							if(fileSystemManager.modifyFileLoader(pathName, md5, lastModified)){
								Doc.append("message", "file loader ready");
								Doc.append("status", true);
								// write file (not complete)
								if (fileSystemManager.writeFile(pathName, src, position)) {
									
								}
							}else{
								Doc.append("message", "File loading fail");
								Doc.append("status", false);
							}
						} catch (IOException e) {
							//TODO: handle exception
							e.printStackTrace();
						}
					}else{ // unsafe pathname
						Doc.append("message", "unsafe pathname");
						Doc.append("status", false);
					}
				}else{ // file does not exist
					Doc.append("message", "pathname does not exist");
					Doc.append("message", false);
				}
				System.out.println(Doc.toJson());
				Out.write(Doc.toJson() + "\n");
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
				System.out.println(Doc.toJson());
				Out.write(Doc.toJson()+"\n");
				Out.flush();
			}
			
			
			if(Document.parse(message).get("command").equals("DIRECTORY_CREATE_RESPONSE")) {
				return;
			}
			if(Document.parse(message).get("command").equals("DIRECTORY_DELETE_RESPONSE")) {
				return;
			}
			
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
				Document Doc = new Document();
				Doc.append("command", "FILE_CREATE_REQUEST");
				Doc.append("pathName", fileSystemEvent.pathName);
				Doc.append("fileDescriptor", fileSystemEvent.fileDescriptor.toDoc());
				Out.write(Doc.toJson()+"\n");
				Out.flush();
			}
		/* 
		File_Modify block
		*/
			if (fileSystemEvent.event.toString().equals("FILE_MODIFY")) {
				Document Doc = new Document();
				Doc.append("command", "FILE_MODIFY_REQUEST");
				Doc.append("pathname", fileSystemEvent.pathName);
				Doc.append("fileDescriptor", fileSystemEvent.fileDescriptor.toDoc());
				Out.write(Doc.toJson() + "\n");
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
		//DIRECTORY_DELETE
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
