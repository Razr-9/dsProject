package unimelb.bitbox;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import unimelb.bitbox.util.Configuration;

public class JSON {
	JSONObject command = new JSONObject();
	@SuppressWarnings("unchecked")
	public String marshaling(String protocol) {
		if(protocol == "CONNECTION_REFUSED") {
			command.put("command","CONNECTION_REFUSED");
			command.put("message","connection limit reached");
			JSONArray peers = new JSONArray();
			//while() {
			//JSONObject hostPost = new JSONObject();
			//	hostPost.put("name", "Distributed Systems");
			//	peers.put(hostPost);
			//}
			command.put("peers",peers);
		}
		
		if(protocol == "INVALID_PROTOCOL_01") {
			command.put("command","INVALID_PROTOCOL");
			command.put("message","message must contain a command field as string");
		}
		
		if(protocol == "HANDSHAKE_REQUEST") {
			command.put("command","HANDSHAKE_REQUEST");
			JSONObject hostPort = new JSONObject();
			hostPort.put("port", Integer.parseInt(Configuration.getConfigurationValue("port")));
			hostPort.put("host", Configuration.getConfigurationValue("advertisedName"));
			command.put("hostPort",hostPort);
		}
		
		if(protocol == "HANDSHAKE_RESPONSE") {
			command.put("command","HANDSHAKE_RESPONSE");
			JSONObject hostPort = new JSONObject();
			hostPort.put("port", Integer.parseInt(Configuration.getConfigurationValue("port")));
			hostPort.put("host", Configuration.getConfigurationValue("advertisedName"));
			command.put("hostPort",hostPort);
		}
		return command.toJSONString();
	}
}
