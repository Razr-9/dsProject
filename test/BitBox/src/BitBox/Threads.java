package BitBox;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.net.SocketException;
import java.util.Scanner;

class Threads extends Thread{
	int i;
	BufferedReader In;
	BufferedWriter Out;
	String Msg = null;
	Socket Socket;
	Scanner Scanner = new Scanner(System.in);
	String InputStr = null;

	public Threads(Socket Socket) {
		try {
		//Get the input/output streams for reading/writing data from/to the socket
			In = new BufferedReader(new InputStreamReader(Socket.getInputStream(), "UTF-8"));
			Out = new BufferedWriter(new OutputStreamWriter(Socket.getOutputStream(), "UTF-8"));
			this.Socket = Socket;
		} catch (SocketException ex) {
			ex.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} 
	}
	public Threads(int i, Socket Socket) {
		this(Socket);
		this.i = i;
	}
	
	public void run() {
		//Read the message from the client and reply
		//Notice that no other connection can be accepted and processed until the last line of 
		//code of this loop is executed, incoming connections have to wait until the current
		//one is processed unless...we use threads!
		try {
			while(!(InputStr = Scanner.nextLine()).equals("exit") || 
					(Msg = In.readLine()) != null) {
				//JSON
				System.out.println("Message from client " + i + ": " + Msg);
				Out.write("Server Ack " + Msg + "\n");
				Out.flush();
				System.out.println("Response sent");
				
				Out.write(InputStr + "\n");
				Out.flush();
				System.out.println("Message sent");
			
			// Receive the reply from the server by reading from the socket input stream
				String clientReceived = In.readLine(); // This method blocks until there
												// is something to read from the
												// input stream
				System.out.println("Message received: " + clientReceived);
			}
			Socket.close();
		} catch (SocketException ex) {
			ex.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} 
	}
}
