package com.ndtorrent.client.status;

import com.ndtorrent.client.PeerChannel;

public final class ConnectionInfo {

	private final String address;

	public ConnectionInfo(PeerChannel channel) {
		address = channel.socket.getRemoteSocketAddress().toString();

	}

	public String getIP() {
		return address;
	}

}
