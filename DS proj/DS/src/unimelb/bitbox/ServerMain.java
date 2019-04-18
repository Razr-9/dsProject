package unimelb.bitbox;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.logging.Logger;

import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;

import unimelb.bitbox.util.Configuration;
import unimelb.bitbox.util.FileSystemManager;
import unimelb.bitbox.util.FileSystemObserver;
import unimelb.bitbox.util.HostPort;
import unimelb.bitbox.util.FileSystemManager.FileSystemEvent;

public class ServerMain implements FileSystemObserver {
	private static Logger log = Logger.getLogger(ServerMain.class.getName());
	protected FileSystemManager fileSystemManager;
	//Add
	ServerSocket listeningSocket = null;
	Socket Socket = null;
	String peers;
	int port;
	private static int i = 0;
	
	public ServerMain() throws NumberFormatException, IOException, NoSuchAlgorithmException {
		fileSystemManager=new FileSystemManager(Configuration.getConfigurationValue("path"),this);
		
		//client(Configuration.getConfigurationValue(peers));
		new Client(Configuration.getConfigurationValue("peers"));

		//Server
		port = Integer.parseInt(Configuration.getConfigurationValue("port"));
		try {
			//Create a server socket listening on port 4444
			listeningSocket = new ServerSocket(4444);
			//i = 0; //counter to keep track of the number of clients
			System.out.println("Server listening on port "+port+" for a connection");
			//Listen for incoming connections for ever 
			while (true) {
				//Accept an incoming client connection request 
				Socket = listeningSocket.accept(); //This method will block until a connection request is received
				this.i++;
				new Threads(Socket, i,  "Server");
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
		// TODO: process events
	}
	
}
