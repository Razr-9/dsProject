package BitBox;

public class Peer{
	public static void main (String [] args) {
		String path;
		int port;
		String advertisedName;
		String peers = "peers";
		int maximumIncomingConnections;
		int blockSize;
		int SyncInterval;
		
		//ThreadForRun S = new ThreadForRun("Server");
		ThreadForRun C = new ThreadForRun("Client", peers);
		//S.start();
		C.start();
	}
}