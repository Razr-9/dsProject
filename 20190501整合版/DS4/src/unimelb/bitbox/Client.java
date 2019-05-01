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
		   //Socket = new Socket("43.113.235.184", 8111);
			Socket = new Socket("localhost", 8888);
			//Socket = new Socket("43.240.97.106",3000);
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
