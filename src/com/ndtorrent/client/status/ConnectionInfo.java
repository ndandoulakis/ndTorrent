package com.ndtorrent.client.status;

import com.ndtorrent.client.PeerChannel;

public final class ConnectionInfo {

	private final String address;
	private final String id;
	private final double input_rate;

	public ConnectionInfo(PeerChannel channel) {
		address = channel.socket.getRemoteSocketAddress().toString();
		id = channel.socket.getInputHandshake().getID();
		input_rate = channel.socket.inputPerSec();
	}

	public String getIP() {
		return address;
	}
	
	public String getID() {
		return id;
	}
	
	public double getInputRate() {
		return input_rate;
	}

}
