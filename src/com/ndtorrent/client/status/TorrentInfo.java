package com.ndtorrent.client.status;

import java.util.BitSet;

import com.ndtorrent.client.Torrent;

public final class TorrentInfo {

	private final BitSet available;
	private final int num_pieces;
	private final String name;
	private final long length;
	private final double input_rate;
	private final double output_rate;

	public TorrentInfo(Torrent torrent, double input_rate, double output_rate) {
		available = torrent.getAvailablePieces();
		num_pieces = torrent.numPieces();
		name = torrent.getName();
		length = torrent.getTotalLength();
		this.input_rate = input_rate;
		this.output_rate = output_rate;

	}

	public BitSet getAvailablePieces() {
		return available;
	}

	public int numPieces() {
		return num_pieces;
	}

	public String getName() {
		return name;
	}

	public long getLength() {
		return length;
	}

	public double getInputRate() {
		return input_rate;
	}

	public double getOutputRate() {
		return output_rate;
	}

}
