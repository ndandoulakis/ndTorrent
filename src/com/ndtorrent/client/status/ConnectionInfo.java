package com.ndtorrent.client.status;

import com.ndtorrent.client.PeerChannel;

public final class ConnectionInfo {

	// TODO add the sizes of various message queues
	private final String address;
	private final String id;
	private final double input_rate;
	private final double output_rate;
	private final long input_total;
	private final long output_total;
	private final int incoming_requests;
	private final int outgoing_requests;
	private final boolean am_choked;
	private final boolean am_interested;
	private final boolean is_choked;
	private final boolean is_interested;
	private final boolean is_optimistic;
	private final boolean is_former_optimistic;
	private final boolean am_snubbed;
	private final boolean am_initiator;

	public ConnectionInfo(PeerChannel channel) {
		address = channel.socket.getRemoteIP();
		id = channel.socket.getInputHandshake().getID();
		input_rate = channel.socket.inputPerSec();
		output_rate = channel.socket.outputPerSec();
		input_total = channel.socket.getInputTotal();
		output_total = channel.socket.getOutputTotal();
		incoming_requests = channel.numIncomingRequests();
		outgoing_requests = channel.numOutgoingRequests();
		am_choked = channel.amChoked();
		am_interested = channel.amInterested();
		is_choked = channel.isChoked();
		is_interested = channel.isInterested();
		is_optimistic = channel.isOptimistic();
		is_former_optimistic = channel.isFormerOptimistic();
		am_snubbed = channel.amSnubbed();
		am_initiator = channel.amInitiator();
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

	public double getOutputRate() {
		return output_rate;
	}

	public long getInputTotal() {
		return input_total;
	}

	public long getOutputTotal() {
		return output_total;
	}

	public int numIncomingRequests() {
		return incoming_requests;
	}

	public int numOutgoingRequests() {
		return outgoing_requests;
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

	public boolean isFormerOptimistic() {
		return is_former_optimistic;
	}

	public boolean amSnubbed() {
		return am_snubbed;
	}

	public boolean isInitiator() {
		return am_initiator;
	}

}
