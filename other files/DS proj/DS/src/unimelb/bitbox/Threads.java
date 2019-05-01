package unimelb.bitbox;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.net.SocketException;
import java.util.Scanner;

import unimelb.bitbox.util.Configuration;

class Threads extends Thread{
	BufferedReader In;
	BufferedWriter Out;
	String Msg = null;
	Socket Socket;
	String InputStr = null;
	
	String ServerOrClient;
	int i;
	
	Scanner scanner = new Scanner(System.in);
	String inputStr = null;
	
	public Threads(Socket Socket, int i, String ServerOrClient) {
		try {
		//Get the input/output streams for reading/writing data from/to the socket
			In = new BufferedReader(new InputStreamReader(Socket.getInputStream(), "UTF-8"));
			Out = new BufferedWriter(new OutputStreamWriter(Socket.getOutputStream(), "UTF-8"));
			this.ServerOrClient = ServerOrClient;
			this.Socket = Socket;
			this.i = i;
		} catch (SocketException ex) {
			ex.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} 
		start();
	}
	
	public void run() {
		//Read the message from the client and reply
		//Notice that no other connection can be accepted and processed until the last line of 
		//code of this loop is executed, incoming connections have to wait until the current
		//one is processed unless...we use threads!
		try {
			if(ServerOrClient=="Server") {
				if(i < Integer.parseInt(Configuration.getConfigurationValue("maximumIncomingConnections"))) {
					while((Msg = In.readLine()) != null) {
						//JSON
						
					}
				}
				else {
					Out.write(new JSON().marshaling("CONNECTION_REFUSED")+"\n");
					Out.flush();
				}
			}

			else if (ServerOrClient=="Client") {
				Out.write(new JSON().marshaling("HANDSHAKE_REQUEST")+"\n");
				Out.flush();
				while((Msg = In.readLine()) != null) {
					//JSON
					// Receive the reply from the server by reading from the socket input stream
					String Received = In.readLine(); // This method blocks until there
													// is something to read from the
													// input stream
					System.out.println("Message received: " + Received);
				}
			}
			Socket.close();
		} catch (SocketException ex) {
			ex.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
