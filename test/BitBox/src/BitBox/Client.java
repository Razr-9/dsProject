package BitBox;

import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;


class Client {
	Socket serverSocket = null;
	public Client (String peers) {
		//int len = length(IPAddress)
		try {
			// Create a stream socket bounded to any port and connect it to the
			// socket bound to localhost on port 4444
			//while(len>0){
			//serverSocket = new Socket("localhost", 4444);
			serverSocket = new Socket("192.168.1.1", 4444);
		 //serverSocket = new Socket("43.240.97.106", 3000);
			System.out.println("Connection established");
			//****Threads cs = 
			new ThreadForServer(serverSocket);
			// Get the input/output streams for reading/writing data from/to the socket
			//****cs.start();
			//len--;
			//}
			
		} catch (UnknownHostException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} 
	}
}
