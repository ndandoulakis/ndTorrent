package com.ndtorrent.client.status;

import com.ndtorrent.client.PeerChannel;

public final class ConnectionInfo {

	// TODO add the sizes of various message queues
	private final String address;
	private final String id;
	private final double input_rate;
	private final boolean am_choked;
	private final boolean am_interested;
	private final boolean is_choked;
	private final boolean is_interested;
	private final boolean is_optimistic;
	private final boolean am_snubbed;
	private final boolean is_initiator = true;

	public ConnectionInfo(PeerChannel channel) {
		address = channel.socket.getRemoteSocketAddress().toString();
		id = channel.socket.getInputHandshake().getID();
		input_rate = channel.socket.inputPerSec();
		am_choked = channel.amChoked();
		am_interested = channel.amInterested();
		is_choked = channel.isChoked();
		is_interested = channel.isInterested();
		is_optimistic = channel.isOptimistic();
		am_snubbed = channel.amSnubbed();
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

	public boolean amChoked() {
		return am_choked;
	}

	public boolean amInterested() {
		return am_interested;
	}

	public boolean isChoked() {
		return is_choked;
	}

	public boolean isInterested() {
		return is_interested;
	}

	public boolean isOptimistic() {
		return is_optimistic;
	}

	public boolean amSnubbed() {
		return am_snubbed;
	}

	public boolean isInitiator() {
		return is_initiator;
	}

}
