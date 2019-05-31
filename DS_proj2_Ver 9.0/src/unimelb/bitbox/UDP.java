package unimelb.bitbox;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;

import unimelb.bitbox.util.Configuration;
import unimelb.bitbox.util.Document;
import unimelb.bitbox.util.FileSystemManager;
import unimelb.bitbox.util.FileSystemManager.FileSystemEvent;

public class UDP extends Thread{
	DatagramPacket packet;
	int i;
	FileSystemManager fileSystemManager;
	public void response(DatagramPacket packet, int i, FileSystemManager fileSystemManager){
		this.packet = packet;
		this.i = i;
		this.fileSystemManager = fileSystemManager;
		start();
	}
	public void run() {
		int blockSize = Integer.parseInt(Configuration.getConfigurationValue("blockSize"));
		int max = Integer.parseInt(Configuration.getConfigurationValue("maximumIncomingConnections"));
		byte[] data = packet.getData();
		int length = packet.getLength();
		String message;
		
		message = new String(data, 0, length);

		try {
			if (Document.parse(message).get("command").equals("HANDSHAKE_REQUEST")) {
				if(i<max) {
					System.out.println("Handshake request received.");
					// HANDSHAKE_RESPONSE
					Document Doc = new Document();
					Document hostPort = new Document();
					Doc.append("command", "HANDSHAKE_RESPONSE");
					hostPort.append("port", Integer.parseInt(Configuration.getConfigurationValue("udpPort")));
					hostPort.append("host", InetAddress.getLocalHost().getHostAddress());
					Doc.append("hostPort", hostPort);
					
					String HP = ((Document) Document.parse(message).get("hostPort")).toJson();
					ServerMain.rememberedPeers[i][0] = Document.parse(HP).get("host").toString();
					ServerMain.rememberedPeers[i][1] = Long.toString((long) Document.parse(HP).get("port"));
					ServerMain.countUDP++;
					String remoteHP[] = new String [] {ServerMain.rememberedPeers[i][0],ServerMain.rememberedPeers[i][1]};
					sendRequestDatagram(Doc, remoteHP);
					
					System.out.println("Handshake response sent.");
				} else {
					// CONNECTION_REFUSED
					Document Doc = new Document();
					ArrayList<Document> peers = new ArrayList<Document>();
					Doc.append("command", "CONNECTION_REFUSED");
					Doc.append("message", "connection limit reached");
					for (int i1 = 0; i1 < ServerMain.rememberedPeers.length; i1++) {
						if (ServerMain.rememberedPeers[i1][0] != null) {
							Document peer = new Document();
							peer.append("host", ServerMain.rememberedPeers[i1][0]);
							peer.append("port", Integer.parseInt(ServerMain.rememberedPeers[i1][1]));
							peers.add(peer);
						}
					}
					Doc.append("peers", peers);
					
					String HP = ((Document) Document.parse(message).get("hostPort")).toJson();
					String remoteHP[] = new String [] {Document.parse(HP).get("host").toString(),Long.toString((long) Document.parse(HP).get("port"))};
					sendRequestDatagram(Doc, remoteHP);
					
					System.out.println("Connection refuesed: exceed maximum number of incomming connections.");
				}
			}
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
				sendDatagram(Doc, packet);
				
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
				sendDatagram(Doc, packet);
				
				System.out.println("Directory delete rsponse sent:");
				System.out.println("	pathname: "+pathName);
				System.out.println("	message: "+Doc.get("message"));
				
			}

			if (Document.parse(message).get("command").equals("DIRECTORY_CREATE_RESPONSE")) {
				System.out.println("Directory create response received:");
				System.out.println("	pathname: "+Document.parse(message).get("pathName").toString());
				System.out.println("	message: "+Document.parse(message).get("message").toString());
			}
			if (Document.parse(message).get("command").equals("DIRECTORY_DELETE_RESPONSE")) {
				System.out.println("Directory delete response received:");
				System.out.println("	pathname: "+Document.parse(message).get("pathName").toString());
				System.out.println("	message: "+Document.parse(message).get("message").toString());
			}
			if (Document.parse(message).get("command").equals("FILE_DELETE_RESPONSE")) {
				System.out.println("File delete response received:");
				System.out.println("	pathname: "+Document.parse(message).get("pathName").toString());
				System.out.println("	message: "+Document.parse(message).get("message").toString());
			}
			if (Document.parse(message).get("command").equals("FILE_CREATE_RESPONSE")) {
				System.out.println("File create response received:");
				System.out.println("	pathname: "+Document.parse(message).get("pathName").toString());
				System.out.println("	message: "+Document.parse(message).get("message").toString());
			}

			
			boolean fileCreateReady = false;
			if (Document.parse(message).get("command").toString().equals("FILE_CREATE_REQUEST")) {
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
								fileCreateReady = true;
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
						fileCreateReady = fileSystemManager.modifyFileLoader(pathName, md5, lastModified);
					}
				}
				sendDatagram(Doc, packet);
				
				System.out.println("File create response sent:");
				System.out.println("	pathname: "+pathName);
				System.out.println("	message: "+Doc.get("message"));
			}
			
			if (fileCreateReady) {
				String pathName = Document.parse(message).get("pathName").toString();
				String fileDes = ((Document) Document.parse(message).get("fileDescriptor")).toJson();
				long fileSize = Document.parse(fileDes).getLong("fileSize");
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
				sendDatagram(Doc1, packet);
			
				System.out.println("File bytes request sent:");
				System.out.println("	pathname: "+pathName);
				System.out.println("	chunk: 1");
			}
			
			/*
			 * FILE_MODIFY_REQUEST -> FILE_MODIFY_RESPONSE
			 */
			boolean fileModifyReady = false;
			if (Document.parse(message).get("command").equals("FILE_MODIFY_REQUEST")) {
				String pathName = Document.parse(message).get("pathName").toString();
				String fileDes = ((Document) Document.parse(message).get("fileDescriptor")).toJson();
				String md5 = Document.parse(fileDes).get("md5").toString();
				long lastModified = Document.parse(fileDes).getLong("lastModified");

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
								fileModifyReady = true;
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
				sendDatagram(Doc, packet);
				
				System.out.println("File modify response sent:");
				System.out.println("	pathname: "+pathName);
				System.out.println("	message: "+Doc.get("message"));

			}
			if (fileModifyReady) {
				String pathName = Document.parse(message).get("pathName").toString();
				String fileDes = ((Document) Document.parse(message).get("fileDescriptor")).toJson();
				long fileSize = Document.parse(fileDes).getLong("fileSize");
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
				sendDatagram(Doc1, packet);
				
				System.out.println("File bytes request sent:");
				System.out.println("	pathname: "+pathName);
				System.out.println("	chunk: 1");
					
			}
			
			
			if (Document.parse(message).get("command").toString().equals("FILE_BYTES_REQUEST")) {
				String pathName = Document.parse(message).get("pathName").toString();
				String fileDes = ((Document) Document.parse(message).get("fileDescriptor")).toJson();
				String md5 = Document.parse(fileDes).get("md5").toString();
				long length1 = Document.parse(message).getLong("length");
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
						ByteBuffer content = fileSystemManager.readFile(md5, position, length1);
						if (content != null) {
							String src = Base64.getEncoder().encodeToString(content.array());
							Doc.append("position", position);
							Doc.append("length", length1);
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
					sendDatagram(Doc, packet);
					
					System.out.println("File bytes response sent:");
					System.out.println("	pathname: "+pathName);
					System.out.println("	chunk: "+(position/blockSize+1));
					System.out.println(
							"Already transfered " + (length1 + position) + " bytes.");
					
				} catch (NoSuchAlgorithmException e) {
					e.printStackTrace();
				}
			}

			if (Document.parse(message).get("command").toString().equals("FILE_BYTES_RESPONSE")) {
				String pathName = Document.parse(message).get("pathName").toString();
				String fileDes = ((Document) Document.parse(message).get("fileDescriptor")).toJson();
				long length1 = Document.parse(message).getLong("length");
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
									"Already transfered " + (length1 + position) + " bytes.");
							Document Doc1 = new Document();
							Doc1.append("command", "FILE_BYTES_REQUEST");
							Doc1.append("fileDescriptor", Document.parse(fileDes));
							Doc1.append("pathName", pathName);
							Doc1.append("position", position + length1);
							if (fileSize - position - length1 < blockSize) {// determine the number of request bytes
								Doc1.append("length", fileSize - position - length1);
							} else {
								Doc1.append("length", blockSize);
							}
							sendDatagram(Doc1, packet);
							
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
				sendDatagram(Doc, packet);
				
				System.out.println("File delete response send:");
				System.out.println("	pathname: "+pathName);
				System.out.println("	message: "+Doc.get("message"));
				
			}

		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void sendDatagram(Document Doc, DatagramPacket packet) {
		byte [] dataSent = Doc.toJson().getBytes();
		DatagramPacket packetSent = null;
		packetSent = new DatagramPacket(dataSent, 0, dataSent.length);
		packetSent.setAddress(packet.getAddress());
		packetSent.setPort(packet.getPort());
		try {
			DatagramSocket peer = new DatagramSocket(null);
			peer.setReuseAddress(true);
			peer.bind(new InetSocketAddress(Integer.parseInt(Configuration.getConfigurationValue("udpPort"))));
			peer.send(packetSent);
			peer.close();
		} catch (NumberFormatException e) {
			e.printStackTrace();
		} catch (SocketException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
	}

	public void Request(FileSystemEvent fileSystemEvent, String[] rememberedPeer) {
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
			sendRequestDatagram(Doc, rememberedPeer);
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
			sendRequestDatagram(Doc, rememberedPeer);
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
			sendRequestDatagram(Doc, rememberedPeer);
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
			sendRequestDatagram(Doc, rememberedPeer);
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
			sendRequestDatagram(Doc, rememberedPeer);
			System.out.println("Directory delete request sent:");
			System.out.println("	pathname: "+fileSystemEvent.pathName);
		}
	}

	private void sendRequestDatagram(Document Doc, String[] rememberedPeer) {
		byte [] dataSent = Doc.toJson().getBytes();
		DatagramPacket packetSent = null;
		packetSent = new DatagramPacket(dataSent, 0, dataSent.length);
		
		InetSocketAddress socketAddress = new InetSocketAddress(rememberedPeer[0], Integer.parseInt(rememberedPeer[1]));
		packetSent.setAddress(socketAddress.getAddress());
		packetSent.setPort(socketAddress.getPort());
		
		try {
			DatagramSocket request = new DatagramSocket(null);
			request.setReuseAddress(true);
			request.bind(new InetSocketAddress(Integer.parseInt(Configuration.getConfigurationValue("udpPort"))));
			request.send(packetSent);
			request.close();
		} catch (NumberFormatException e) {
			e.printStackTrace();
		} catch (SocketException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void sendHandshake(String host, int port) {
		Document Doc = new Document();
		Document hostPort = new Document();
		Doc.append("command", "HANDSHAKE_REQUEST");
		hostPort.append("port", Integer.parseInt(Configuration.getConfigurationValue("udpPort")));
		try {
			hostPort.append("host", InetAddress.getLocalHost().getHostAddress());
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
		Doc.append("hostPort", hostPort);
		String [] HP = new String [] {host, Integer.toString(port)};
		sendRequestDatagram(Doc, HP);
		System.out.println("Handshake request sent.");
	}

}
