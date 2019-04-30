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
import java.util.Base64;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import unimelb.bitbox.util.Configuration;
import unimelb.bitbox.util.Document;
import unimelb.bitbox.util.FileSystemManager;
import unimelb.bitbox.util.FileSystemManager.FileSystemEvent;

class Threads extends Thread{
	BufferedReader In;
	BufferedWriter Out;
	String Msg = null;
	Socket Socket;
	
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
				Out.write(Doc.toJson()+"\n");
				Out.flush();
			}
			if(Document.parse(message).get("command").equals("DIRECTORY_CREATE_RESPONSE")) {
				return;
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
			if(Document.parse(message).get("command").equals("DIRECTORY_DELETE_RESPONSE")) {
				return;
			}
			
			if(Document.parse(message).get("command").toString().equals("FILE_CREATE_REQUEST")) {
				try {
				JSONParser parser = new JSONParser();
				boolean ready = false;
				String pathName = Document.parse(message).get("pathName").toString();
				JSONObject JSON;
				JSON = (JSONObject) parser.parse(message);
				JSONObject fileDes = (JSONObject) JSON.get("fileDescriptor");
				String md5 = fileDes.get("md5").toString();
				long lastModified = (long) fileDes.get("lastModified");
				long fileSize = (long) fileDes.get("fileSize");
				Document Doc = new Document();
				Doc.append("command", "FILE_CREATE_RESPONSE");
				Doc.append("fileDescriptor", Document.parse(fileDes.toJSONString()));
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
					int blockSize = 1048576;
					double n = Math.floor(fileSize/blockSize + 1);
					for(int m=0;m < n;m++) {
						Document Doc1 = new Document();
						Doc1.append("command", "FILE_BYTES_REQUEST");
						Doc1.append("fileDescriptor", Document.parse(fileDes.toJSONString()));
						Doc1.append("pathName", pathName);
						Doc1.append("position", (long) m * blockSize);
						if(m<n-1){// determine the number of request bytes
							Doc1.append("length",blockSize);
						}else {
							if(m == 0) {
								Doc1.append("length", fileSize);
							}else {
								Doc1.append("length", fileSize - blockSize * m);
							}
						}
						Out.write(Doc1.toJson()+"\n");
						Out.flush();
						if(Document.parse((Msg = In.readLine())).get("command").equals("FILE_BYTES_RESPONSE")) {
							Respond(Msg);
						}
					}
				}
				} catch (ParseException e1) {
					e1.printStackTrace();
				}
			}
			if(Document.parse(message).get("command").equals("FILE_CREATE_RESPONSE")) {
				return;
			}
			
			if(Document.parse(message).get("command").toString().equals("FILE_BYTES_REQUEST")) {
				try {
				JSONParser parser = new JSONParser();
				String pathName = Document.parse(message).get("pathName").toString();
				JSONObject JSON;
				JSON = (JSONObject) parser.parse(message);
				JSONObject fileDes = (JSONObject) JSON.get("fileDescriptor");
				String md5 = fileDes.get("md5").toString();
				long length = (long) JSON.get("length");
				long position = (long) JSON.get("position");
				Document Doc = new Document();
				Doc.append("command", "FILE_BYTES_RESPONSE");
				Doc.append("fileDescriptor", Document.parse(fileDes.toJSONString()));
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
			} catch (ParseException e1) {
				e1.printStackTrace();
			}
			}
			
			if(Document.parse(message).get("command").toString().equals("FILE_BYTES_RESPONSE")) {
				try {
				JSONParser parser = new JSONParser();
				String pathName = Document.parse(message).get("pathName").toString();
				JSONObject JSON;
				JSON = (JSONObject) parser.parse(message);
				JSONObject fileDes = (JSONObject) JSON.get("fileDescriptor");
				long length = (long) JSON.get("length");
				long position = (long) JSON.get("position");
				String src = Document.parse(message).get("content").toString();
				byte[] bytes = Base64.getDecoder().decode(src);
				ByteBuffer content = ByteBuffer.allocate((int) length);
				content.put(bytes);
				content.position(0);
				if(fileSystemManager.writeFile(pathName, content, position)) {
					try {
						if(fileSystemManager.checkWriteComplete(pathName)) {
							System.out.println("Write already done");
						}else {
							System.out.println("Already transfered"+ (length + position) + "bytes"+" this time:"+src);
						}
					} catch (NoSuchAlgorithmException e) {
						e.printStackTrace();
					}
				}else {
					System.out.println("there was no associated file loader for the given name.");
				}
			} catch (ParseException e1) {
				e1.printStackTrace();
			}
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
