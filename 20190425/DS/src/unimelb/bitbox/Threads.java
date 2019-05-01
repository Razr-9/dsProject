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
import java.util.ArrayList;
import java.util.Base64;
import java.lang.*;
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
						ArrayList eventSync = fileSystemManager.generateSyncEvents();
						sendSyncRequest(eventSync);
						while((Msg = In.readLine()) != null) {
							//JSON:
//							System.out.println(Msg);
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
					ArrayList eventSync = fileSystemManager.generateSyncEvents();
					sendSyncRequest(eventSync);
				
					while(!Document.parse((Msg = In.readLine())).get("command").equals("INVALID_PROTOCOL")) {
						//JSON:
//						System.out.println(Msg);
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

	@SuppressWarnings("unchecked")
	private void sendSyncRequest(ArrayList eventSync) throws IOException {
		for(int i=0;i<eventSync.size();i++) {
			String[] command = eventSync.get(i).toString().split("\\s+");
			if(command[0].toString().equals("FILE_CREATE")) {
				Document Doc = new Document();
				Doc.append("command", "FILE_CREATE_REQUEST");
				Doc.append("pathName", command[1]);
				JSONObject fileDescriptor = new JSONObject();
				fileDescriptor.put("md5", "");
				Doc.append("fileDescriptor", fileSystemEvent.fileDescriptor.toDoc());
				Out.write(Doc.toJson()+"\n");
				Out.flush();
			}
			
			if(command[0].toString().equals("DIRECTORY_CREATE")) {
				Document Doc = new Document();
				Doc.append("command", "DIRECTORY_CREATE_REQUEST");
				Doc.append("pathName", command[1]);
				Out.write(Doc.toJson()+"\n");
				Out.flush();
			}
			
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
//				System.out.println(Doc.toJson());
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
//				System.out.println(Doc.toJson());
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
//				String fd = fileDes.replaceAll("\\\\", "");
//				System.out.println(fd);
				
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
//				System.out.println(Doc.toJson());
				Out.write(Doc.toJson()+"\n");
				Out.flush();
				
				if(ready) {
					try {
						if(fileSystemManager.checkShortcut(pathName)){
							//copy local same content file
						}else {
							int blockSize = 1048576;
							double n = Math.floor(length/blockSize + 1);
							System.out.println(n);
							for(int m=0;m < n;m++) {
								Document Doc1 = new Document();
								Doc1.append("command", "FILE_BYTES_REQUEST");
								Doc1.append("fileDescriptor", fileDes);
								Doc1.append("pathName", pathName);
								Doc1.append("position", m * blockSize);
								
								if(m<n-1){// determine the number of request bytes
									Doc1.append("length",blockSize);
								}else {
									if(m == 0) {
										Doc1.append("length",length);
									}else {
										Doc1.append("length", length - blockSize * m);
									}
								}
								
								Out.write(Doc1.toJson()+"\n");
								Out.flush();
							}
						}
					} catch (NoSuchAlgorithmException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}

					
			}

			/* 
				FILE_MODIFY_REQUEST -> FILE_MODIFY_RESPONSE
			*/
			if(Document.parse(message).get("command").equals("FILE_MODIFY_REQUEST")) {
				String pathName = Document.parse(message).get("pathName").toString();
				String fileDes = ((Document) Document.parse(message).get("fileDescriptor")).toJson();
				String md5 = Document.parse(fileDes).get("md5").toString();
				// long lastModified = Document.parse(fileDes).getLong("lastModified");
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
								
								// Doc_modify.append("command", "FILE_BYTES_REQUEST");
								// Doc_modify.append("fileDescriptor", Document.parse(fileDes));
								// Doc_modify.append("pathName", pathName);

								// System.out.println(Doc_modify.toJson());
								// Out.write(Doc_modify.toJson() + "\n");
								// Out.flush();
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

				if(Doc.getBoolean("status")) {
					try {
						if(fileSystemManager.checkShortcut(pathName)){
							//copy local same content file
						}else {
							int blockSize = 1048576;
							double n = Math.floor(length/blockSize + 1);
							System.out.println(n);
							for(int m=0;m < n;m++) {
								Document Doc_modify = new Document();
								Doc_modify.append("command", "FILE_BYTES_REQUEST");
								Doc_modify.append("fileDescriptor", fileDes);
								Doc_modify.append("pathName", pathName);
								Doc_modify.append("position", m * blockSize);
								
								if(m<n-1){// determine the number of request bytes
									Doc_modify.append("length",blockSize);
								}else {
									if(m == 0) {
										Doc_modify.append("length",length);
									}else {
										Doc_modify.append("length", length - blockSize * m);
									}
								}
								
								Out.write(Doc_modify.toJson()+"\n");
								Out.flush();
							}
						}
					} catch (NoSuchAlgorithmException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}
			
			if(Document.parse(message).get("command").toString().equals("FILE_BYTES_REQUEST")) {
				String pathName = Document.parse(message).get("pathName").toString();
				String fileDes =  Document.parse(message).get("fileDescriptor").toString();
				String md5 = Document.parse(fileDes).get("md5").toString();
				long length = Document.parse(message).getLong("length");
				long position = Document.parse(message).getLong("position");
				Document Doc = new Document();
				Doc.append("command", "FILE_BYTES_RESPONSE");
				Doc.append("fileDescriptor", fileDes);
				Doc.append("pathName", pathName);

				try {
					if(fileSystemManager.fileNameExists(pathName, md5)) {
						ByteBuffer content = fileSystemManager.readFile(md5, position, length);
						System.out.println(content);
						if(content == null) { //no such file
							Doc.append("message", "unsuccessful read");
							Doc.append("status",false);
						}
						String src = Base64.getEncoder().encodeToString(content.array()).toString();
						
						Doc.append("position", position);
						Doc.append("length", length);
						Doc.append("content", src);
						Doc.append("message", "successful read");
						Doc.append("status", true);
					}else {// file name and content don't exist
						Doc.append("message", "unsuccessful read");
						Doc.append("status",false);
					}
					
					Out.write(Doc.toJson()+"\n");
					Out.flush();
					
				} catch (NoSuchAlgorithmException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			
			if(Document.parse(message).get("command").toString().equals("FILE_BYTES_RESPONSE")) {
				String pathName = Document.parse(message).get("pathName").toString();
				String fileDes =  Document.parse(message).get("fileDescriptor").toString();
				String md5 = Document.parse(fileDes).get("md5").toString();
				long length = Document.parse(message).getLong("length");
				long position = Document.parse(message).getLong("position");
				String src = Document.parse(message).get("content").toString();
				
				byte[] content = Base64.getDecoder().decode(src);
				System.out.println(new String(content, "utf-8"));
				
				ByteBuffer content1 = ByteBuffer.wrap(content);

				if(fileSystemManager.writeFile(pathName, content1, position)) {
					try {
						if(fileSystemManager.checkWriteComplete(pathName)) {
							System.out.println("Write already done");
						}else {
							System.out.println("Already transfered "+ (length + position) + " bytes");
						}
					} catch (NoSuchAlgorithmException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}else {
					System.out.println("there was no associated file loader for the given name.");
				}
			}
			// Delete file
			if(Document.parse(message).get("command").toString().equals("FILE_DELETE_REQUEST")) {
				String pathName = Document.parse(message).get("pathName").toString();
				String fileDes =  Document.parse(message).get("fileDescriptor").toString();
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
//				System.out.println(Doc.toJson());
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
		/**
		 * A new directory has been created. The parent directory must
		 * exist for this event to be emitted.
		 */
			if(fileSystemEvent.event.toString().equals("FILE_DELETE")) {
				Document Doc = new Document();
				Doc.append("command", "FILE_DELETE_REQUEST");
				Doc.append("fileDescriptor", fileSystemEvent.fileDescriptor.toDoc());
				Doc.append("pathName", fileSystemEvent.pathName);
//				System.out.println(Doc.toJson());
				Out.write(Doc.toJson()+"\n");
				Out.flush();
			}
			
		//DIRECTORY_CREATE,
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
