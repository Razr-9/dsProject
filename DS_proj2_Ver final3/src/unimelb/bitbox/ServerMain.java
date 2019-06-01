package unimelb.bitbox;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;
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
	// Add
	static int max = Integer.parseInt(Configuration.getConfigurationValue("maximumIncomingConnections"));
	static String[][] rememberedPeers = new String[max][2];
	Socket Socket = null;
	String peers;
	int len;
	static Threads[] th;
	CmdHandler handler;
	static Threads[] thClient;
	static int countUDP = 0;

	public ServerMain() throws NumberFormatException, IOException, NoSuchAlgorithmException {
		fileSystemManager = new FileSystemManager(Configuration.getConfigurationValue("path"), this);
		peers = Configuration.getConfigurationValue("peers");
		handler = new CmdHandler(this);
		if (Configuration.getConfigurationValue("mode").equals("tcp")) {
			// TCP Client
			thClient = new Client(peers, fileSystemManager).thR();

			// TCP Server
			int port = Integer.parseInt(Configuration.getConfigurationValue("port"));
			int max = Integer.parseInt(Configuration.getConfigurationValue("maximumIncomingConnections"));
			
			th = new Threads[max + 1];
			ServerSocket listeningSocket = null;
			
			try {
				// Create a server socket listening on ports get from configuration
				listeningSocket = new ServerSocket(port);
				System.out.println("Server listening on port " + port + " for a connection");
				// Listen for incoming connections for ever
				while (true) {
					// Accept an incoming client connection request
					Socket = listeningSocket.accept(); // This method will block until a connection request is received
					for (int j = 0; j < max + 1; j++) {
						if (th[j] == null || !th[j].isAlive()) {
							th[j] = new Threads(Socket, j, "Server", fileSystemManager);
							break;
						}
					}
				}
			} catch (SocketException ex) {
				ex.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			} finally {
				if (listeningSocket != null) {
					try {
						listeningSocket.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
		} else if(Configuration.getConfigurationValue("mode").equals("udp")) {
			int udpPort = Integer.parseInt(Configuration.getConfigurationValue("udpPort"));
		//UDP Client
		new Client(peers);
			
		// Synchronous
		Timer timer = new Timer();
		int syncInterval = Integer.parseInt(Configuration.getConfigurationValue("syncInterval"));
		timer.schedule(new TimerTask() {
			public void run() {
				System.out.println("Synchronizing...");
				ArrayList<FileSystemEvent> eventSync = fileSystemManager.generateSyncEvents();
				for (int i = 0; i < eventSync.size(); i++) {
					if(rememberedPeers!=null) {
						for(int i1=0; i1<rememberedPeers.length ;i1++){
							if(rememberedPeers[i1][0]!=null) {
								new UDPRequest(eventSync.get(i), rememberedPeers[i1]);
							}
						}
					}
				}
					System.out.println("Synchronization finished");
			}
		}, 0, syncInterval * 1000);
			
		//UDP Server
			DatagramSocket listeningSocket = new DatagramSocket(null);
			System.out.println("Server listening on port "+udpPort+" for a connection");
			try {
				while (true) {
					//Create a datagram socket listening on port get from configuration
					listeningSocket = new DatagramSocket(null);
					listeningSocket.setReuseAddress(true);
					listeningSocket.bind(new InetSocketAddress(udpPort));
					byte[] container = new byte[Integer.parseInt(Configuration.getConfigurationValue("blockSize"))+30000];
					DatagramPacket packet = new DatagramPacket(container, container.length);
						
					//Listen for incoming connections
					listeningSocket.receive(packet); //This method will block until a connection request is received
					listeningSocket.close();
					new UDPResponse(packet,countUDP, fileSystemManager);
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
						new UDPRequest(fileSystemEvent, rememberedPeers[i]);
					}
				}
			}
			
		}
		
	}

	public static boolean Disconnection(String host, String port) {
		if(Configuration.getConfigurationValue("mode").equals("tcp")) {
			for(int i=0; i<max ;i++){
				if(th[i]!=null && th[i].Socket.getInetAddress().getHostAddress().equals(host)
				  && Integer.toString(th[i].Socket.getPort()).equals(port) && th[i].isAlive()){
					try {
						th[i].Socket.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
					th[i].interrupt();
					return true;
				}
			}
			if(thClient!=null) {
				for(int i=0; i<thClient.length ;i++){
					if(thClient[i]!=null && thClient[i].Socket.getInetAddress().getHostAddress().equals(host)
							  && Integer.toString(thClient[i].Socket.getPort()).equals(port) && thClient[i].isAlive()){
						try {
							th[i].Socket.close();
						} catch (IOException e) {
							e.printStackTrace();
						}
						thClient[i].interrupt();
						return true;
					}
				}
			}
		} else if (Configuration.getConfigurationValue("mode").equals("udp")) {
			if(rememberedPeers!=null) {
				for(int i=0; i<rememberedPeers.length ;i++){
					if(rememberedPeers[i][0]!=null && rememberedPeers[i][0].equals(host) && rememberedPeers[i][1].equals(port)) {
						rememberedPeers[i][0]=null;
						rememberedPeers[i][1]=null;
						return true;
					}
				}
			}
		}
		return false;
	}	
}
