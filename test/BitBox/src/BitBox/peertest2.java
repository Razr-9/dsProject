package BitBox;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Scanner;
import java.net.ServerSocket;
import java.net.SocketException;

public class peertest2 {
	public static void main (String [] args) {
		String path;
		int port;
		String advertisedName;
		String [] peers;
		int maximumIncomingConnections;
		int blockSize;
		int SyncInterval;
		
		//Client client = new Client();
		//new Server2(5555);
		Client3 client2 = new Client3();
		client2.createClient("localhost", 4444);
	}
}
	//client part
class Client3 {
	Socket clientSocket = null;
	public void createClient(String hostName, int port) {
		try {
			// Create a stream socket bounded to any port and connect it to the
			// socket bound to localhost on port 4444
			clientSocket = new Socket("10.13.58.203", 5555);
			System.out.println("Connection established");

			// Get the input/output streams for reading/writing data from/to the socket
			BufferedReader clientIn = new BufferedReader(new InputStreamReader(clientSocket.getInputStream(), "UTF-8"));
			BufferedWriter clientOut = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream(), "UTF-8"));

			Scanner clientScanner = new Scanner(System.in);
			String clientInputStr = null;

			//While the user input differs from "exit"
			/**while (!(clientInputStr = clientScanner.nextLine()).equals("exit")) {
				
				// Send the input string to the server by writing to the socket output stream
				clientOut.write(clientInputStr + "\n");
				clientOut.flush();
				System.out.println("Message sent");
				
				// Receive the reply from the server by reading from the socket input stream
				String clientReceived = clientIn.readLine(); // This method blocks until there
													// is something to read from the
													// input stream
				System.out.println("Message received: " + clientReceived);
			}
			*/
			clientScanner.close();

		} catch (UnknownHostException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			// Close the socket
			if (clientSocket != null) {
				try {
					clientSocket.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}
}

	//server part
class Server3 {
	ServerSocket listeningSocket = null;
	Socket cSocket = null;
	public Server3 (int port){
		try {
			//Create a server socket listening on port 4444
			listeningSocket = new ServerSocket(port);
			int i = 0; //counter to keep track of the number of clients
			
			
			//Listen for incoming connections for ever 
			while (true) {
				System.out.println("Server listening on port 4444 for a connection");
				//Accept an incoming client connection request 
				cSocket = listeningSocket.accept(); //This method will block until a connection request is received
				i++;
				System.out.println("Client conection number " + i + " accepted:");
				System.out.println("Remote Port: " + cSocket.getPort());
				System.out.println("Remote Hostname: " + cSocket.getInetAddress().getHostName());
				System.out.println("Local Port: " + cSocket.getLocalPort());
				
				//Get the input/output streams for reading/writing data from/to the socket
				BufferedReader in = new BufferedReader(new InputStreamReader(cSocket.getInputStream(), "UTF-8"));
				BufferedWriter out = new BufferedWriter(new OutputStreamWriter(cSocket.getOutputStream(), "UTF-8"));

				
				//Read the message from the client and reply
				//Notice that no other connection can be accepted and processed until the last line of 
				//code of this loop is executed, incoming connections have to wait until the current
				//one is processed unless...we use threads!
				String clientMsg = null;
				try {
				while((clientMsg = in.readLine()) != null) {
					System.out.println("Message from client " + i + ": " + clientMsg);
					out.write("Server Ack " + clientMsg + "\n");
					out.flush();
					System.out.println("Response sent");
				}}
				catch(SocketException e) {
					System.out.println("closed...");
				}
				cSocket.close();
			}
		} catch (SocketException ex) {
			ex.printStackTrace();
		}catch (IOException e) {
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