package unimelb.bitbox;
import org.kohsuke.args4j.Option;
import unimelb.bitbox.util.HostPort;

public class CmdLineArgs {
	
	@Option(required = true, name = "-c", aliases = {"--command"}, usage = "Command name")
	private String command;
	
	@Option(required = true, name = "-s", aliases = {"--server"}, usage = "Server address")
	private String server;
	
	@Option(required = false, name = "-i", aliases = {"--identity"}, usage = "Identity")
	private String identity = null;
	
	@Option(required = false, name = "-p", aliases = {"--peer"}, usage = "Peer address")
	private String peer = null;
	
	public HostPort getServer() {
		return new HostPort(server);
	}
	
	public HostPort getPeer() {
		return new HostPort(peer);
	}
	
	public String getCommand() {
		if(command.equals("list_peers") && server != null) return "LIST_PEERS_REQUEST";
		else if (command.equals("connect_peer") && server != null && peer != null) return "CONNECT_PEER_REQUEST";
		else if (command.equals("disconnect_peer") && server != null && peer != null) return "DISCONNECT_PEER_REQUEST";
		else return "INVALID";
	}
	
	public String getIdentity() {
		return identity;
	}
	
	public boolean isValid() {
		if(command.equals("list_peers") && server != null) return true;
		else if (command.equals("connect_peer") && server != null && peer != null) return true;
		else if (command.equals("disconnect_peer") && server != null && peer != null) return true;
		else return false;
	}
}
