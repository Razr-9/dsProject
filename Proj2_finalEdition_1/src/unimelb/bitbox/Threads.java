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
import java.util.Timer;
import java.util.TimerTask;

import unimelb.bitbox.util.Configuration;
import unimelb.bitbox.util.Document;
import unimelb.bitbox.util.FileSystemManager;
import unimelb.bitbox.util.FileSystemManager.FileSystemEvent;
import unimelb.bitbox.util.HostPort;

class Threads extends Thread {
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
			// Get the input/output streams for reading/writing data from/to the socket
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

	@SuppressWarnings("unchecked")
	public void run() {
		// Read the message from the client and reply
		// Notice that no other connection can be accepted and processed until the last
		// line of
		// code of this loop is executed, incoming connections have to wait until the
		// current
		// one is processed unless...we use threads!
		try {
			if (ServerOrClient == "Server") {
				System.out.println("Incomming connection request...");
				int max = Integer.parseInt(Configuration.getConfigurationValue("maximumIncomingConnections"));
				if (j < max) {
					System.out.println("Connection established:");
					System.out.println("Connection ID: "+(j+1));
					System.out.println("Peer host name: "+Socket.getInetAddress().getHostName());
					System.out.println("Peer address: "+Socket.getInetAddress().getHostAddress());
					System.out.println("Peer port number: "+Socket.getPort());
					if (Document.parse((Msg = In.readLine())).get("command").equals("HANDSHAKE_REQUEST")) {
						System.out.println("Handshake request received.");
						// HANDSHAKE_RESPONSE
						Document Doc = new Document();
						Document hostPort = new Document();
						Doc.append("command", "HANDSHAKE_RESPONSE");
						hostPort.append("port", Integer.parseInt(Configuration.getConfigurationValue("port")));
						hostPort.append("host", Configuration.getConfigurationValue("advertisedName"));
						Doc.append("hostPort", hostPort);
						Out.write(Doc.toJson() + "\n");
						Out.flush();
						System.out.println("Handshake response sent.");

						// Synchronous
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
						}, 0, syncInterval * 1000);

						// Respond
						try {
						while ((Msg = In.readLine()) != null) {
							if(Document.parse(Msg).get("command").equals("HANDSHAKE_REQUEST")) {
								Document Doc1 = new Document();
								Doc1.append("command", "INVALID_PROTOCOL");
								Doc1.append("message", "handshaking has already been completed, handshake request should not be sent again");
								Out.write(Doc1.toJson() + "\n");
								Out.flush();
								timer.cancel();
								Socket.close();
								System.out.println("Connection terminated: duplicate handshake requests received.");
								break;
							} else {
								Respond(Msg);
							}
						}
						}catch (SocketException e) {
							System.out.println("Disconnected.");
						}
					}
					else {
						Document Doc = new Document();
						Doc.append("command", "INVALID_PROTOCOL");
						Doc.append("message", "message must be a handshake request");
						Out.write(Doc.toJson() + "\n");
						Out.flush();
						Socket.close();
						System.out.println("Connection terminated: none handshake request received.");
					}
				} else {
					// CONNECTION_REFUSED
					Document Doc = new Document();
					ArrayList<Document> peers = new ArrayList<Document>();
					Doc.append("command", "CONNECTION_REFUSED");
					Doc.append("message", "connection limit reached");
					for (int i = 0; i < ServerMain.thClient.length; i++) {
						if (ServerMain.thClient[i] != null && ServerMain.thClient[i].isAlive()) {
							Document peer = new Document();
							peer.append("host", ServerMain.thClient[i].Socket.getInetAddress().getHostName());
							peer.append("port", ServerMain.thClient[i].Socket.getPort());
							peers.add(peer);
						}
					}
					Doc.append("peers", peers);
					Out.write(Doc.toJson() + "\n");
					Out.flush();
					Socket.close();
					System.out.println("Connection refuesed: exceed maximum number of incomming connections.");
				}
			} else if (ServerOrClient == "Client") {
				// HANDSHAKE_REQUEST
				Document Doc = new Document();
				Document hostPort = new Document();
				Doc.append("command", "HANDSHAKE_REQUEST");
				hostPort.append("port", Integer.parseInt(Configuration.getConfigurationValue("port")));
				hostPort.append("host", Configuration.getConfigurationValue("advertisedName"));
				Doc.append("hostPort", hostPort);
				Out.write(Doc.toJson() + "\n");
				Out.flush();
				System.out.println("Handshake request sent, waiting for response...");

				// Respond
				Msg = In.readLine();
				if (Document.parse(Msg).get("command").equals("HANDSHAKE_RESPONSE")) {
					System.out.println("Handshake response received, connection to peer "+j+" established:");
					System.out.println("Peer host name: "+Socket.getInetAddress().getHostName());
					System.out.println("Peer address: "+Socket.getInetAddress().getHostAddress());
					System.out.println("Peer port number: "+Socket.getPort());
					// Asynchronous
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
					}, 0, syncInterval * 1000);
					try {
					while ((Msg = In.readLine()) != null) {
						if(Document.parse(Msg).get("command").equals("INVALID_PROTOCOL")) {
							System.out.println("Connection terminated: "+Document.parse(Msg).get("message").toString());
							Socket.close();
							timer.cancel();
							break;
						} else {
							Respond(Msg);
						}
					}
					}catch (SocketException e) {
						System.out.println("Disconnected.");
						Socket.close();
						timer.cancel();
					}
				}
				else if(Document.parse(Msg).get("command").equals("CONNECTION_REFUSED")) {
					System.out.println("Connection refused: peer "+j+" exceeds maximum number of incomming connections.");
					ArrayList<Document> docs = (ArrayList<Document>) Document.parse(Msg).get("peers");
					if(!docs.isEmpty()) {
						for(int i = 0; i<docs.size();i++) {
							String host = (String) docs.get(i).get("host");
							Number num = (Number) docs.get(i).get("port");
							int port = num.intValue();
							Client.BFS.add(new HostPort(host,port));
						}
						Client.bfsCount+=docs.size();
						System.out.println(Client.bfsCount+" alternative peer(s) available.");
					}
					else{
						System.out.println("No alternative peers available.");
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
		for (int i = 0; i < eventSync.size(); i++) {
			String event = eventSync.get(i).event.toString();
			if (event.equals("FILE_CREATE")) {
				Document Doc = new Document();
				Doc.append("command", "FILE_CREATE_REQUEST");
				Doc.append("pathName", eventSync.get(i).pathName);
				Doc.append("fileDescriptor", eventSync.get(i).fileDescriptor.toDoc());
				Out.write(Doc.toJson() + "\n");
				Out.flush();
			}
			if (event.equals("DIRECTORY_CREATE")) {
				Document Doc = new Document();
				Doc.append("command", "DIRECTORY_CREATE_REQUEST");
				Doc.append("pathName", eventSync.get(i).pathName);
				Out.write(Doc.toJson() + "\n");
				Out.flush();
			}
		}
	}

	public void Respond(String message) {
		try {
			// DIRECTORY_CREATE_REQUEST -> DIRECTORY_CREATE_RESPONSE
			if (Document.parse(message).get("command").equals("DIRECTORY_CREATE_REQUEST")) {
				String pathName = Document.parse(message).get("pathName").toString();
				
				System.out.println("Directory create request received:");
				System.out.println("	pathname: "+pathName);
				
				Document Doc = new Document();
				Doc.append("command", "DIRECTORY_CREATE_RESPONSE");
				Doc.append("pathName", pathName);
				if (!fileSystemManager.dirNameExists(pathName)) {
					if (fileSystemManager.isSafePathName(pathName)) {
						if (fileSystemManager.makeDirectory(pathName)) {
							Doc.append("message", "directory created");
							Doc.append("status", true);
						} else {
							Doc.append("message", "there was a problem creating the directory");
							Doc.append("status", false);
						}
					} else {
						Doc.append("message", "unsafe pathname given");
						Doc.append("status", false);
					}
				} else {
					Doc.append("message", "pathname already exists");
					Doc.append("status", false);
				}
				Out.write(Doc.toJson() + "\n");
				Out.flush();
				
				System.out.println("Directory create rsponse sent:");
				System.out.println("	pathname: "+pathName);
				System.out.println("	message: "+Doc.get("message"));

			}

			// DIRECTORY_DELETE_REQUEST -> DIRECTORY_DELETE_RESPONSE
			if (Document.parse(message).get("command").equals("DIRECTORY_DELETE_REQUEST")) {
				String pathName = Document.parse(message).get("pathName").toString();
				
				System.out.println("Directory delete request received:");
				System.out.println("	pathname: "+pathName);
				
				Document Doc = new Document();
				Doc.append("command", "DIRECTORY_DELETE_RESPONSE");
				Doc.append("pathName", pathName);
				if (fileSystemManager.dirNameExists(pathName)) {
					if (fileSystemManager.isSafePathName(pathName)) {
						if (fileSystemManager.deleteDirectory(pathName)) {
							Doc.append("message", "directory deleted");
							Doc.append("status", true);
						} else {
							Doc.append("message", "there was a problem creating the directory");
							Doc.append("status", false);
						}
					} else {
						Doc.append("message", "unsafe pathname given");
						Doc.append("status", false);
					}
				} else {
					Doc.append("message", "pathname does not exists");
					Doc.append("status", false);
				}
				Out.write(Doc.toJson() + "\n");
				Out.flush();
				
				System.out.println("Directory delete rsponse sent:");
				System.out.println("	pathname: "+pathName);
				System.out.println("	message: "+Doc.get("message"));
				
			}

			if (Document.parse(message).get("command").equals("DIRECTORY_CREATE_RESPONSE")) {
				System.out.println("Directory create response received:");
				System.out.println("	pathname: "+Document.parse(message).get("pathName").toString());
				System.out.println("	message: "+Document.parse(message).get("message").toString());
				return;
			}
			if (Document.parse(message).get("command").equals("DIRECTORY_DELETE_RESPONSE")) {
				System.out.println("Directory delete response received:");
				System.out.println("	pathname: "+Document.parse(message).get("pathName").toString());
				System.out.println("	message: "+Document.parse(message).get("message").toString());
				return;
			}
			if (Document.parse(message).get("command").equals("FILE_DELETE_RESPONSE")) {
				System.out.println("File delete response received:");
				System.out.println("	pathname: "+Document.parse(message).get("pathName").toString());
				System.out.println("	message: "+Document.parse(message).get("message").toString());
				return;
			}
			if (Document.parse(message).get("command").equals("FILE_CREATE_RESPONSE")) {
				System.out.println("File create response received:");
				System.out.println("	pathname: "+Document.parse(message).get("pathName").toString());
				System.out.println("	message: "+Document.parse(message).get("message").toString());
				return;
			}

			if (Document.parse(message).get("command").toString().equals("FILE_CREATE_REQUEST")) {
				boolean ready = false;
				String pathName = Document.parse(message).get("pathName").toString();
				String fileDes = ((Document) Document.parse(message).get("fileDescriptor")).toJson();
				String md5 = Document.parse(fileDes).get("md5").toString();
				long fileSize = Document.parse(fileDes).getLong("fileSize");
				long lastModified = Document.parse(fileDes).getLong("lastModified");
				
				System.out.println("File create request received:");
				System.out.println("	pathname: "+pathName);
				
				Document Doc = new Document();
				Doc.append("command", "FILE_CREATE_RESPONSE");
				Doc.append("fileDescriptor", Document.parse(fileDes));
				Doc.append("pathName", pathName);
				
				if (!fileSystemManager.fileNameExists(pathName)) {
					if (fileSystemManager.isSafePathName(pathName)) {
						try {
							if (fileSystemManager.createFileLoader(pathName, md5, fileSize, lastModified)) {
								Doc.append("message", "file loader ready");
								Doc.append("status", true);
								ready = true;
							} else {// unsuccessfully create
								Doc.append("message", "there was a problem creating the file");
								Doc.append("status", false);
							}
						} catch (NoSuchAlgorithmException e) {
							e.printStackTrace();
						}
					} else {// unsave pathname
						Doc.append("message", "unsafe pathname given");
						Doc.append("status", false);
					}

				} else {// filename exists
					if(fileSystemManager.fileNameExists(pathName, md5)) {
						Doc.append("message", "pathname already exists");
						Doc.append("status", false);
					}else {
						ready = fileSystemManager.modifyFileLoader(pathName, md5, lastModified);
					}
				}
				Out.write(Doc.toJson() + "\n");
				Out.flush();
				
				System.out.println("File create response sent:");
				System.out.println("	pathname: "+pathName);
				System.out.println("	message: "+Doc.get("message"));

				if (ready) {
					Document Doc1 = new Document();
					Doc1.append("command", "FILE_BYTES_REQUEST");
					Doc1.append("fileDescriptor", Document.parse(fileDes));

					Doc1.append("pathName", pathName);
					Doc1.append("position", (long) 0);
					if (fileSize < blockSize) {// determine the number of request bytes
						Doc1.append("length", fileSize);
					} else {
						Doc1.append("length", blockSize);
					}
					Out.write(Doc1.toJson() + "\n");
					Out.flush();
					
					System.out.println("File bytes request sent:");
					System.out.println("	pathname: "+pathName);
					System.out.println("	chunk: 1");
				}
			}

			/*
			 * FILE_MODIFY_REQUEST -> FILE_MODIFY_RESPONSE
			 */
			if (Document.parse(message).get("command").equals("FILE_MODIFY_REQUEST")) {
				String pathName = Document.parse(message).get("pathName").toString();
				String fileDes = ((Document) Document.parse(message).get("fileDescriptor")).toJson();
				String md5 = Document.parse(fileDes).get("md5").toString();
				long lastModified = Document.parse(fileDes).getLong("lastModified");
				long fileSize = Document.parse(fileDes).getLong("fileSize");

				System.out.println("File modify request received:");
				System.out.println("	pathname: "+pathName);
				
				Document Doc = new Document();
				Doc.append("command", "FILE_MODIFY_RESPONSE");
				Doc.append("pathName", pathName);
				Doc.append("fileDescriptor", Document.parse(fileDes));
				if (!fileSystemManager.fileNameExists(pathName, md5)) {
					if (fileSystemManager.isSafePathName(pathName)) {
						try {
							if (fileSystemManager.modifyFileLoader(pathName, md5, lastModified)) {
								Doc.append("message", "file loader ready");
								Doc.append("status", true);
							} else {
								Doc.append("message", "there was a problem modifying the file");
								Doc.append("status", false);
							}
						} catch (IOException e) {
							e.printStackTrace();
						}
					} else { // unsafe pathname
						Doc.append("message", "unsafe pathname given");
						Doc.append("status", false);
					}
				} else { // file does not exist
					Doc.append("message", "pathname does not exist");
					Doc.append("status", false);
				}
				Out.write(Doc.toJson() + "\n");
				Out.flush();
				
				System.out.println("File modify response sent:");
				System.out.println("	pathname: "+pathName);
				System.out.println("	message: "+Doc.get("message"));

				if (Doc.getBoolean("status")) {
					Document Doc1 = new Document();
					Doc1.append("command", "FILE_BYTES_REQUEST");
					Doc1.append("fileDescriptor", Document.parse(fileDes));
					Doc1.append("pathName", pathName);
					Doc1.append("position", (long) 0);
					if (fileSize < blockSize) {// determine the number of request bytes
						Doc1.append("length", fileSize);
					} else {
						Doc1.append("length", blockSize);
					}
					System.out.println("Send");
					Out.write(Doc1.toJson() + "\n");
					Out.flush();
					
					System.out.println("File bytes request sent:");
					System.out.println("	pathname: "+pathName);
					System.out.println("	chunk: 1");
					
				}
			}

			if (Document.parse(message).get("command").toString().equals("FILE_BYTES_REQUEST")) {
				String pathName = Document.parse(message).get("pathName").toString();
				String fileDes = ((Document) Document.parse(message).get("fileDescriptor")).toJson();
				String md5 = Document.parse(fileDes).get("md5").toString();
				long length = Document.parse(message).getLong("length");
				long position = Document.parse(message).getLong("position");
				
				System.out.println("File bytes request received:");
				System.out.println("	pathname: "+pathName);
				System.out.println("	chunk: "+(position/blockSize+1));
				
				Document Doc = new Document();
				Doc.append("command", "FILE_BYTES_RESPONSE");
				Doc.append("fileDescriptor", Document.parse(fileDes));
				Doc.append("pathName", pathName);

				try {
					if (fileSystemManager.fileNameExists(pathName, md5)) {
						ByteBuffer content = fileSystemManager.readFile(md5, position, length);
						if (content != null) {
							String src = Base64.getEncoder().encodeToString(content.array());
							Doc.append("position", position);
							Doc.append("length", length);
							Doc.append("content", src);
							Doc.append("message", "successful read");
							Doc.append("status", true);
						} else {
							Doc.append("message", "unsuccessful read");
							Doc.append("status", false);
						}
					} else {// file name and content don't exist
						Doc.append("message", "unsuccessful read");
						Doc.append("status", false);
					}
					Out.write(Doc.toJson() + "\n");
					Out.flush();
					
					System.out.println("File bytes response sent:");
					System.out.println("	pathname: "+pathName);
					System.out.println("	chunk: "+(position/blockSize+1));
					System.out.println(
							"Already transfered " + (length + position) + " bytes.");
					
				} catch (NoSuchAlgorithmException e) {
					e.printStackTrace();
				}
			}

			if (Document.parse(message).get("command").toString().equals("FILE_BYTES_RESPONSE")) {
				String pathName = Document.parse(message).get("pathName").toString();
				String fileDes = ((Document) Document.parse(message).get("fileDescriptor")).toJson();
				long length = Document.parse(message).getLong("length");
				long position = Document.parse(message).getLong("position");
				long fileSize = Document.parse(fileDes).getLong("fileSize");
				String src = Document.parse(message).get("content").toString();

				System.out.println("File bytes response received:");
				System.out.println("	pathname: "+pathName);
				System.out.println("	chunk: "+(position/blockSize+1));
				
				byte[] content = Base64.getDecoder().decode(src);

				ByteBuffer content1 = ByteBuffer.wrap(content);

				if (fileSystemManager.writeFile(pathName, content1, position)) {
					try {
						if (fileSystemManager.checkWriteComplete(pathName)) {
							System.out.println("Write already done");
						} else {
							System.out.println(
									"Already transfered " + (length + position) + " bytes.");
							Document Doc1 = new Document();
							Doc1.append("command", "FILE_BYTES_REQUEST");
							Doc1.append("fileDescriptor", Document.parse(fileDes));
							Doc1.append("pathName", pathName);
							Doc1.append("position", position + length);
							if (fileSize - position - length < blockSize) {// determine the number of request bytes
								Doc1.append("length", fileSize - position - length);
							} else {
								Doc1.append("length", blockSize);
							}
							Out.write(Doc1.toJson() + "\n");
							Out.flush();
							
							System.out.println("File bytes request sent:");
							System.out.println("	pathname: "+pathName);
							System.out.println("	chunk: "+(position/blockSize+2));
							
						}
					} catch (NoSuchAlgorithmException e) {
						e.printStackTrace();
					}
				} else {
					System.out.println("there was no associated file loader for the given name.");
				}
			}

			// Delete file
			if (Document.parse(message).get("command").toString().equals("FILE_DELETE_REQUEST")) {
				String pathName = Document.parse(message).get("pathName").toString();
				String fileDes = ((Document) Document.parse(message).get("fileDescriptor")).toJson();
				String md5 = Document.parse(fileDes).get("md5").toString();
				long lastModified = Document.parse(fileDes).getLong("lastModified");
				
				System.out.println("File delete request received:");
				System.out.println("	pathname: "+pathName);
				
				Document Doc = new Document();
				Doc.append("command", "FILE_DELETE_RESPONSE");
				Doc.append("pathName", pathName);
				if (fileSystemManager.fileNameExists(pathName)) {
					if (fileSystemManager.isSafePathName(pathName)) {
						if (fileSystemManager.deleteFile(pathName, lastModified, md5)) {
							Doc.append("message", "file deleted");
							Doc.append("status", true);
						} else {
							Doc.append("message", "there was a problem deleting the file");
							Doc.append("status", false);
						}
					} else {
						Doc.append("message", "unsafe pathname given");
						Doc.append("status", false);
					}
				} else {
					Doc.append("message", "pathname does not exists");
					Doc.append("status", false);
				}
				Out.write(Doc.toJson() + "\n");
				Out.flush();
				
				System.out.println("File delete response send:");
				System.out.println("	pathname: "+pathName);
				System.out.println("	message: "+Doc.get("message"));
				
			}

		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void Request(FileSystemEvent fileSystemEvent) {
		try {
			/**
			 * A new file has been created. The parent directory must exist for this event
			 * to be emitted.
			 */
			// FILE_CREATE,
			if (fileSystemEvent.event.toString().equals("FILE_CREATE")) {
				Document Doc = new Document();
				Doc.append("command", "FILE_CREATE_REQUEST");
				Doc.append("pathName", fileSystemEvent.pathName);
				Doc.append("fileDescriptor", fileSystemEvent.fileDescriptor.toDoc());
				Out.write(Doc.toJson() + "\n");
				Out.flush();
				System.out.println("File create request sent:");
				System.out.println("	pathname: "+fileSystemEvent.pathName);
			}
			/**
			 * An existing file has been modified.
			 */
			// File_MODIFY,
			if (fileSystemEvent.event.toString().equals("FILE_MODIFY")) {
				Document Doc = new Document();
				Doc.append("command", "FILE_MODIFY_REQUEST");
				Doc.append("pathName", fileSystemEvent.pathName);
				Doc.append("fileDescriptor", fileSystemEvent.fileDescriptor.toDoc());
				Out.write(Doc.toJson() + "\n");
				Out.flush();
				System.out.println("File modify request sent:");
				System.out.println("	pathname: "+fileSystemEvent.pathName);
			}
			/**
			 * An existing file has been deleted.
			 */
			// FILE_DELETE,
			if (fileSystemEvent.event.toString().equals("FILE_DELETE")) {
				Document Doc = new Document();
				Doc.append("command", "FILE_DELETE_REQUEST");
				Doc.append("fileDescriptor", fileSystemEvent.fileDescriptor.toDoc());
				Doc.append("pathName", fileSystemEvent.pathName);
				Out.write(Doc.toJson() + "\n");
				Out.flush();
				System.out.println("File delete request sent:");
				System.out.println("	pathname: "+fileSystemEvent.pathName);
			}
			/**
			 * A new directory has been created. The parent directory must exist for this
			 * event to be emitted.
			 */
			// DIRECTORY_CREATE,
			if (fileSystemEvent.event.toString().equals("DIRECTORY_CREATE")) {
				Document Doc = new Document();
				Doc.append("command", "DIRECTORY_CREATE_REQUEST");
				Doc.append("pathName", fileSystemEvent.pathName);
				Out.write(Doc.toJson() + "\n");
				Out.flush();
				System.out.println("Directory create request sent:");
				System.out.println("	pathname: "+fileSystemEvent.pathName);
			}
			/**
			 * An existing directory has been deleted. The directory must be empty for this
			 * event to be emitted, and its parent directory must exist.
			 */
			// DIRECTORY_DELETE,
			if (fileSystemEvent.event.toString().equals("DIRECTORY_DELETE")) {
				Document Doc = new Document();
				Doc.append("command", "DIRECTORY_DELETE_REQUEST");
				Doc.append("pathName", fileSystemEvent.pathName);
				Out.write(Doc.toJson() + "\n");
				Out.flush();
				System.out.println("Directory delete request sent:");
				System.out.println("	pathname: "+fileSystemEvent.pathName);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}
