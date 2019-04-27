package unimelb.bitbox;

import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;

import unimelb.bitbox.util.FileSystemManager;
import unimelb.bitbox.util.HostPort;


class Client {
	Socket Socket = null;
	String [] hostPorts;
	int len;
	Threads [] th;
	
	public Client (String peers, FileSystemManager fileSystemManager) {
		hostPorts = peers.split(",");
		//int len = length(IPAddress)
		try {
			// Create a stream socket bounded to any port and connect it to the
			// socket bound to localhost on port 4444
		  len = hostPorts.length;
		  th = new Threads [len];
		  
		  while(len > 0){
			HostPort HP = new HostPort(hostPorts[len-1]);
			//Socket = new Socket(HP.host, HP.port);
		    //Socket = new Socket("10.13.58.203", 5555);
			Socket = new Socket("localhost", 5555);
			//Socket = new Socket("10.13.150.43", 4444);
			System.out.println("Connection established");
			th[len-1]= new Threads(Socket, 0, "Client", fileSystemManager);
			// Get the input/output streams for reading/writing data from/to the socket
			len--;
		  }
			
		} catch (UnknownHostException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} 
	}

	public Threads[] thR() {
		return th;
	}
}
