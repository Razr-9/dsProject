package BitBox;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.net.SocketException;

class ThreadForClient extends Thread{
	int i;
	BufferedReader serverIn;
	BufferedWriter serverOut;
	String clientMsg = null;
	Socket clientSocket;
	
	public ThreadForClient(int i, Socket clientSocket) {
		try {
		//Get the input/output streams for reading/writing data from/to the socket
		serverIn = new BufferedReader(new InputStreamReader(clientSocket.getInputStream(), "UTF-8"));
		serverOut = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream(), "UTF-8"));
		this.i = i;
		this.clientSocket = clientSocket;
		} catch (SocketException ex) {
			ex.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} 
	}
	
	public void run() {
		//Read the message from the client and reply
		//Notice that no other connection can be accepted and processed until the last line of 
		//code of this loop is executed, incoming connections have to wait until the current
		//one is processed unless...we use threads!
		try {
			while((clientMsg = serverIn.readLine()) != null) {
				//JSON
				System.out.println("Message from client " + i + ": " + clientMsg);
				serverOut.write("Server Ack " + clientMsg + "\n");
				serverOut.flush();
				System.out.println("Response sent");
			}
			clientSocket.close();
		} catch (SocketException ex) {
			ex.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} 
	}
}
