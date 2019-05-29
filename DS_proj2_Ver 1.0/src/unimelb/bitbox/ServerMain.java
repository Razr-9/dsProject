package unimelb.bitbox;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.logging.Logger;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;

import unimelb.bitbox.util.Configuration;
import unimelb.bitbox.util.Document;
import unimelb.bitbox.util.FileSystemManager;
import unimelb.bitbox.util.FileSystemObserver;
import unimelb.bitbox.util.HostPort;
import unimelb.bitbox.util.FileSystemManager.FileSystemEvent;

public class ServerMain implements FileSystemObserver {
	private static Logger log = Logger.getLogger(ServerMain.class.getName());
	protected FileSystemManager fileSystemManager;
	//Add
	static int max = Integer.parseInt(Configuration.getConfigurationValue("maximumIncomingConnections"));
	static String [] [] rememberedPeers = new String [max][2];
	Socket Socket = null;
	String peers;
	int len;
	Threads [] th;
	static Threads [] thClient;
	static int countUDP = 0;
	
	public ServerMain() throws NumberFormatException, IOException, NoSuchAlgorithmException {
		fileSystemManager=new FileSystemManager(Configuration.getConfigurationValue("path"),this);
		peers = Configuration.getConfigurationValue("peers");
		
		if(Configuration.getConfigurationValue("mode").equals("tcp")) {
		//TCP Client
		thClient = new Client(peers, fileSystemManager).thR();
		
		//TCP Server
		int port = Integer.parseInt(Configuration.getConfigurationValue("port"));
		int max = Integer.parseInt(Configuration.getConfigurationValue("maximumIncomingConnections"));
		th = new Threads [max+1];
		ServerSocket listeningSocket = null;
		
		try {
			//Create a server socket listening on port get from configuration
			listeningSocket = new ServerSocket(port);
			System.out.println("Server listening on port "+port+" for a connection");
			//Listen for incoming connections for ever 
			while (true) {
				//Accept an incoming client connection request 
				Socket = listeningSocket.accept(); //This method will block until a connection request is received
				for(int j=0;j<max+1;j++) {
					if(th[j] == null || !th[j].isAlive()) {
						th[j] = new Threads(Socket, j,  "Server", fileSystemManager);
						break;
					}
				}
			}
		} catch (SocketException ex) {
			ex.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} 
		finally {
			if(listeningSocket != null) {
				try {
					listeningSocket.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		}else if(Configuration.getConfigurationValue("mode").equals("udp")) {
			int udpPort = Integer.parseInt(Configuration.getConfigurationValue("udpPort"));
		//UDP Client
			String [] hostPorts;
			hostPorts = peers.split(",");
			for(int i=0;i<hostPorts.length;i++) {
				HostPort HP = new HostPort(hostPorts[i]);
				byte[] container = new byte[Integer.parseInt(Configuration.getConfigurationValue("blockSize"))];
				DatagramPacket packet = new DatagramPacket(container, container.length);
				
				for(int j=0;j<Integer.parseInt(Configuration.getConfigurationValue("udpRetries"));j++) {
					
					new UDP().sendHandshake(HP.host, HP.port);
					
					DatagramSocket ClientSocket = new DatagramSocket(udpPort);
					ClientSocket.setSoTimeout(Integer.parseInt(Configuration.getConfigurationValue("udpTimeout")));
					try {
					ClientSocket.receive(packet); //This method will block until a connection request is received
					ClientSocket.close();
					
					if(packet.getData() != null) {
						byte[] data = packet.getData();
						int length = packet.getLength();
						String message = new String(data, 0, length);
						if(Document.parse(message).get("command").equals("HANDSHAKE_RESPONSE")) {
							rememberedPeers[countUDP][0] = HP.host;
							rememberedPeers[countUDP][1] = Integer.toString(HP.port);
							System.out.println(rememberedPeers[countUDP][0]+" "+rememberedPeers[countUDP][1]);
							countUDP++;
							System.out.println("Handshake response received.");
							System.out.println("Connection established.");
							break;
						}
					}
					}catch(SocketTimeoutException e){
						ClientSocket.close();
						System.out.println("Timeout. Retrying...");
						continue;
					}
				}
				
			}
			
		//UDP Server
				DatagramSocket listeningSocket = new DatagramSocket(null);
				System.out.println("Server listening on port "+udpPort+" for a connection");
				try {
					while (true) {
						//Create a datagram socket listening on port get from configuration
						listeningSocket = new DatagramSocket(null);
						listeningSocket.setReuseAddress(true);
						listeningSocket.bind(new InetSocketAddress(udpPort));
						byte[] container = new byte[Integer.parseInt(Configuration.getConfigurationValue("blockSize"))];
						DatagramPacket packet = new DatagramPacket(container, container.length);
						
						//Listen for incoming connections
						listeningSocket.receive(packet); //This method will block until a connection request is received
						listeningSocket.close();
						
						if(new UDP().response(packet,countUDP, fileSystemManager)) {
							countUDP++;
						}
						
					}
				} catch (SocketException ex) {
					ex.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				} 
				finally {
					if(listeningSocket != null) {
						listeningSocket.close();
					}
				}
		}
	}

	@Override
	public void processFileSystemEvent(FileSystemEvent fileSystemEvent) {
		int max = Integer.parseInt(Configuration.getConfigurationValue("maximumIncomingConnections"));
		
		if(Configuration.getConfigurationValue("mode").equals("tcp")) {
			//TCP
			for(int i=0; i<max ;i++){
				if(th[i]!=null && th[i].isAlive()){
					th[i].Request(fileSystemEvent);
				}
			}
			if(thClient!=null) {
				for(int i=0; i<thClient.length ;i++){
					if(thClient[i]!=null && thClient[i].isAlive()){
						thClient[i].Request(fileSystemEvent);
					}
				}
			}
		} else if (Configuration.getConfigurationValue("mode").equals("udp")){
			//UDP
			if(rememberedPeers!=null) {
				for(int i=0; i<rememberedPeers.length ;i++){
					if(rememberedPeers[i][0]!=null) {
						System.out.println(rememberedPeers[i][0]+" "+rememberedPeers[i][1]);
						new UDP().Request(fileSystemEvent, rememberedPeers[i]);
					}
				}
			}
		}
		
	}	
}
