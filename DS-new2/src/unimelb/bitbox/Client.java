package unimelb.bitbox;

import java.io.IOException;
import java.net.ConnectException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Timer;

import unimelb.bitbox.util.FileSystemManager;
import unimelb.bitbox.util.HostPort;


class Client {
	Socket Socket = null;
	String [] hostPorts;
	int len;
	Threads [] th;
	static ArrayList<HostPort> BFS = new ArrayList<HostPort>();
	static int bfsCount = 0;
	
	public Client (String peers, FileSystemManager fileSystemManager) {
		System.out.println("Attempting to connect to other peers");
		hostPorts = peers.split(",");
		
			// Create a stream socket bounded to any port and connect it to the
			// socket bound to localhost on port 4444
		  len = hostPorts.length;
		  th = new Threads [len];
		  
		  int i = 1;
		  while(len > 0){
			HostPort HP = new HostPort(hostPorts[len-1]);
			try {
				Socket = new Socket(HP.host, HP.port);
				System.out.println("Connecting to peer "+i+" ...");
				th[len-1]= new Threads(Socket, i, "Client", fileSystemManager);
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				if(!th[len-1].isAlive()) {
					System.out.println("Attempting to connect to one of available peers via breadth first search...");
					int A = 0;
					while(A<bfsCount) {
						Socket = new Socket(BFS.get(A).host, BFS.get(A).port);
						th[len-1] = new Threads(Socket, i, "Client", fileSystemManager);
						try {
							Thread.sleep(1000);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
						if(th[len-1].isAlive()) {
							bfsCount=0;
							len--;
							i++;
							break;
						}
						A++;
					}
				}else {
					len--;
					i++;
				}
			} catch (UnknownHostException e) {
				e.printStackTrace();
			} catch (ConnectException e) {
				System.out.println("Connection to peer "+i+" failed");
				len--;
				i++;
			} catch (IOException e) {
				e.printStackTrace();
			}
		  }
	}

	public Threads[] thR() {
		return th;
	}
}
