package BitBox;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.net.SocketException;
import java.util.Scanner;

class ThreadForServer extends Thread{
	BufferedReader clientIn;
	BufferedWriter clientOut;
	Socket serverSocket;
	Scanner clientScanner = new Scanner(System.in);
	String clientInputStr = null;
	public ThreadForServer(Socket serverSocket) {
		try {
		//Get the input/output streams for reading/writing data from/to the socket
			clientIn = new BufferedReader(new InputStreamReader(serverSocket.getInputStream(), "UTF-8"));
			clientOut = new BufferedWriter(new OutputStreamWriter(serverSocket.getOutputStream(), "UTF-8"));
			this.serverSocket = serverSocket;
		} catch (SocketException ex) {
			ex.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} 
		start();
	}
	
	public void run() {
		try {

		//Read the message from the client and reply
		//Notice that no other connection can be accepted and processed until the last line of 
		//code of this loop is executed, incoming connections have to wait until the current
		//one is processed unless...we use threads!

		//While the user input differs from "exit"
			while (!(clientInputStr = clientScanner.nextLine()).equals("exit")) {
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
		clientScanner.close();
		} catch (SocketException ex) {
			ex.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			// Close the socket
			if (serverSocket != null) {
				try {
					serverSocket.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}
}
