package com.ndtorrent.client.status;

import java.util.BitSet;

import com.ndtorrent.client.Torrent;

// TODO FileInfo
// TODO TrackerInfo

public final class TorrentInfo {

	private final BitSet available;
	private final int num_pieces;

	public TorrentInfo(Torrent torrent) {
		available = torrent.getAvailablePieces();
		num_pieces = torrent.numPieces();
	}

	public BitSet getAvailablePieces() {
		return available;
	}

	public int numPieces() {
		return num_pieces;
	}

}
