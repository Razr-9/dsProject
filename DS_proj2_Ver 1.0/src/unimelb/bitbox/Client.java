package unimelb.bitbox;

import java.io.IOException;
import java.net.ConnectException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;

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
				boolean exist = false;
				for(int i1 = 0; i1<th.length;i1++) {
					if(th[i1]!=null&&th[i1].isAlive()&&th[i1].Socket.getPort()==HP.port) {
						exist = true;
						System.out.println("Connection exists");
						len--;
						i++;
						break;
					}
				}
				if(!exist) {
				Socket = new Socket(HP.host, HP.port);
				System.out.println("Connecting to peer "+i+" ...");
				th[len-1]= new Threads(Socket, i, "Client", fileSystemManager);
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				if(!th[len-1].isAlive()) {
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
							BFS.clear();
							break;
						}
						A++;
					}
					len--;
					i++;
				}else {
					len--;
					i++;
				}
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
