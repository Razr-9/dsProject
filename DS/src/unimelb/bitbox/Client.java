package unimelb.bitbox;

import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;

import unimelb.bitbox.util.HostPort;


class Client {
	Socket Socket = null;
	String [] hostPorts;
	public Client (String peers) {
		hostPorts = peers.split(",");
		//int len = length(IPAddress)
		try {
			// Create a stream socket bounded to any port and connect it to the
			// socket bound to localhost on port 4444
		  int len = hostPorts.length;
		  while(len > 0){
			HostPort HP = new HostPort(hostPorts[len-1]);
			//Socket = new Socket(HP.host, HP.port);
		    //Socket = new Socket("45.113.235.237", 8111);
			//Socket = new Socket("localhost", 5555);
			Socket = new Socket("localhost", 5555);
			System.out.println("Connection established");
			new Threads(Socket, 0, "Client");
			// Get the input/output streams for reading/writing data from/to the socket
			len--;
		  }
			
		} catch (UnknownHostException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} 
	}
}
