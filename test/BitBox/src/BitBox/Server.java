package BitBox;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;

class Server {
	ServerSocket listeningSocket = null;
	Socket clientSocket = null;
	public Server (int port){
		try {
			//Create a server socket listening on port 4444
			listeningSocket = new ServerSocket(5555);
			int i = 0; //counter to keep track of the number of clients
			System.out.println("Server listening on port 4444 for a connection");
			//Listen for incoming connections for ever 
			while (true) {
				//Accept an incoming client connection request 
				clientSocket = listeningSocket.accept(); //This method will block until a connection request is received
				i++;
				//****Threads ct = 
				new Threads(i, clientSocket);
				System.out.println("Client conection number " + i + " accepted:");
				System.out.println("Remote Port: " + clientSocket.getPort());
				System.out.println("Remote Hostname: " + clientSocket.getInetAddress().getHostName());
				System.out.println("Local Port: " + clientSocket.getLocalPort());
				//****ct.start();
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
}