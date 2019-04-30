package BitBox;

public class ThreadForRun extends Thread{
	String name;
	String peers;
	public ThreadForRun(String name) {
		this.name = name;
	}
	public ThreadForRun(String name, String peers) {
		this.name = name;
		this.peers = peers;
	}
	public void run() {
		if (name == "Server") {
			new Server(4444);
		}
		else if (name == "Client"){
			new Client(peers);
		}
	}
}
