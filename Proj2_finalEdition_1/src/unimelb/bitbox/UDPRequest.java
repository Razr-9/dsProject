package unimelb.bitbox;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import unimelb.bitbox.util.Configuration;
import unimelb.bitbox.util.Document;
import unimelb.bitbox.util.FileSystemManager.FileSystemEvent;

public class UDPRequest extends Thread{
	FileSystemEvent fileSystemEvent;
	String[] rememberedPeer;
	public UDPRequest(FileSystemEvent fileSystemEvent, String[] rememberedPeer){
		this.fileSystemEvent = fileSystemEvent;
		this.rememberedPeer = rememberedPeer;
		start();
	}
	public void run() {
		try {
		DatagramSocket listeningSocket = new DatagramSocket(null);
		listeningSocket.bind(null);
		listeningSocket.setSoTimeout(Integer.parseInt(Configuration.getConfigurationValue("udpTimeout")));
		byte[] container = new byte[Integer.parseInt(Configuration.getConfigurationValue("blockSize"))+30000];
		DatagramPacket packet = new DatagramPacket(container, container.length);
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
			for(int j=0;j<Integer.parseInt(Configuration.getConfigurationValue("udpRetries"));j++) {
				sendRequestDatagram(Doc, rememberedPeer);
				System.out.println("File create request sent:");
				System.out.println("	pathname: "+fileSystemEvent.pathName);
				try {
					listeningSocket.receive(packet);//This method will block until a connection request is received
					listeningSocket.close();
					if(packet.getData() != null) {
						byte[] data = packet.getData();
						int length = packet.getLength();
						String message = new String(data, 0, length);
						if(Document.parse(message).get("command").equals("FILE_CREATE_RESPONSE")
								&&Document.parse(message).get("pathName").equals(fileSystemEvent.pathName)
								&&Document.parse(message).get("fileDescriptor").equals(fileSystemEvent.fileDescriptor.toDoc())) {
							System.out.println("File create response received.");
							break;
						}
					}
				}catch(SocketTimeoutException e){
					listeningSocket.close();
					System.out.println("Timeout. Retrying...");
					continue;
				}
			}
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
			for(int j=0;j<Integer.parseInt(Configuration.getConfigurationValue("udpRetries"));j++) {
				sendRequestDatagram(Doc, rememberedPeer);
				System.out.println("File modify request sent:");
				System.out.println("	pathname: "+fileSystemEvent.pathName);
				try {
					listeningSocket.receive(packet);//This method will block until a connection request is received
					listeningSocket.close();
					if(packet.getData() != null) {
						byte[] data = packet.getData();
						int length = packet.getLength();
						String message = new String(data, 0, length);
						if(Document.parse(message).get("command").equals("FILE_MODIFY_RESPONSE")
								&&Document.parse(message).get("pathName").equals(fileSystemEvent.pathName)
								&&Document.parse(message).get("fileDescriptor").equals(fileSystemEvent.fileDescriptor.toDoc())) {
							System.out.println("File modify response received.");
							break;
						}
					}
				}catch(SocketTimeoutException e){
					listeningSocket.close();
					System.out.println("Timeout. Retrying...");
					continue;
				}
			}
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
			for(int j=0;j<Integer.parseInt(Configuration.getConfigurationValue("udpRetries"));j++) {
				sendRequestDatagram(Doc, rememberedPeer);
				System.out.println("File delete request sent:");
				System.out.println("	pathname: "+fileSystemEvent.pathName);
				try {
					listeningSocket.receive(packet);//This method will block until a connection request is received
					listeningSocket.close();
					if(packet.getData() != null) {
						byte[] data = packet.getData();
						int length = packet.getLength();
						String message = new String(data, 0, length);
						if(Document.parse(message).get("command").equals("FILE_DELETE_RESPONSE")
								&&Document.parse(message).get("pathName").equals(fileSystemEvent.pathName)
								&&Document.parse(message).get("fileDescriptor").equals(fileSystemEvent.fileDescriptor.toDoc())) {
							System.out.println("File delete response received.");
							break;
						}
					}
				}catch(SocketTimeoutException e){
					listeningSocket.close();
					System.out.println("Timeout. Retrying...");
					continue;
				}
			}
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
			for(int j=0;j<Integer.parseInt(Configuration.getConfigurationValue("udpRetries"));j++) {
				sendRequestDatagram(Doc, rememberedPeer);
				System.out.println("Directory create request sent:");
				System.out.println("	pathname: "+fileSystemEvent.pathName);
				try {
					listeningSocket.receive(packet);//This method will block until a connection request is received
					listeningSocket.close();
					if(packet.getData() != null) {
						byte[] data = packet.getData();
						int length = packet.getLength();
						String message = new String(data, 0, length);
						if(Document.parse(message).get("command").equals("DIRECTORY_CREATE_RESPONSE")
								&&Document.parse(message).get("pathName").equals(fileSystemEvent.pathName)) {
							System.out.println("Directory create response received.");
							break;
						}
					}
				}catch(SocketTimeoutException e){
					listeningSocket.close();
					System.out.println("Timeout. Retrying...");
					continue;
				}
			}
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
			for(int j=0;j<Integer.parseInt(Configuration.getConfigurationValue("udpRetries"));j++) {
				sendRequestDatagram(Doc, rememberedPeer);
				System.out.println("Directory delete request sent:");
				System.out.println("	pathname: "+fileSystemEvent.pathName);
				try {
					listeningSocket.receive(packet);//This method will block until a connection request is received
					listeningSocket.close();
					if(packet.getData() != null) {
						byte[] data = packet.getData();
						int length = packet.getLength();
						String message = new String(data, 0, length);
						if(Document.parse(message).get("command").equals("DIRECTORY_DELETE_RESPONSE")
								&&Document.parse(message).get("pathName").equals(fileSystemEvent.pathName)) {
							System.out.println("Directory delete response received.");
							break;
						}
					}
				}catch(SocketTimeoutException e){
					listeningSocket.close();
					System.out.println("Timeout. Retrying...");
					continue;
				}
			}
		}
		} catch (NumberFormatException e) {
			e.printStackTrace();
		} catch (UnknownHostException e) {
			e.printStackTrace();
		} catch (SocketException e) {
		} catch (IOException e) {
			e.printStackTrace();
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
}
