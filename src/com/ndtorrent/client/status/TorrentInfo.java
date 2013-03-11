package com.ndtorrent.client.status;

import java.util.BitSet;

import com.ndtorrent.client.Torrent;

public final class TorrentInfo {

	private final BitSet available;
	private final int num_pieces;
	private final String name;
	private final long length;

	public TorrentInfo(Torrent torrent) {
		available = torrent.getAvailablePieces();
		num_pieces = torrent.numPieces();
		name = torrent.getName();
		length = torrent.getTotalLength();

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

}
