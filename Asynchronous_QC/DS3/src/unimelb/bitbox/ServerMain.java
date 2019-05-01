package unimelb.bitbox;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.logging.Logger;

import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;

import unimelb.bitbox.util.Configuration;
import unimelb.bitbox.util.FileSystemManager;
import unimelb.bitbox.util.FileSystemObserver;
import unimelb.bitbox.util.FileSystemManager.FileSystemEvent;

public class ServerMain implements FileSystemObserver {
	private static Logger log = Logger.getLogger(ServerMain.class.getName());
	protected FileSystemManager fileSystemManager;
	//Add
	ServerSocket listeningSocket = null;
	Socket Socket = null;
	String peers;
	int port;
	int len;
	Threads [] th;
	Threads [] thClient;
	
	public ServerMain() throws NumberFormatException, IOException, NoSuchAlgorithmException {
		fileSystemManager=new FileSystemManager(Configuration.getConfigurationValue("path"),this);
		
		peers = Configuration.getConfigurationValue("peers");
		thClient = new Client(peers, fileSystemManager).thR();

		//Server
		port = Integer.parseInt(Configuration.getConfigurationValue("port"));
		int max = Integer.parseInt(Configuration.getConfigurationValue("maximumIncomingConnections"));
		th = new Threads [max+1];
		
		try {
			//Create a server socket listening on port 4444
			listeningSocket = new ServerSocket(5555);
			//i = 0; //counter to keep track of the number of clients
			System.out.println("Server listening on port "+port+" for a connection");
			//Listen for incoming connections for ever 
			while (true) {
				//Accept an incoming client connection request 
				Socket = listeningSocket.accept(); //This method will block until a connection request is received
				for(int j=0;j<max+1;j++) {
					if(th[j] == null || !th[j].isAlive()) {
						System.out.println(j);
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
	}

	@Override
	public void processFileSystemEvent(FileSystemEvent fileSystemEvent) {
		int max = Integer.parseInt(Configuration.getConfigurationValue("maximumIncomingConnections"));
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
	}	
}
