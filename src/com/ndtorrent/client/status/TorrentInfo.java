package com.ndtorrent.client.status;

import java.util.BitSet;

import com.ndtorrent.client.Torrent;

// TODO FileInfo
// TODO TrackerInfo

public final class TorrentInfo {

	private final BitSet bitfield;
	private final int num_pieces;

	public TorrentInfo(Torrent torrent) {
		bitfield = torrent.getCompletePieces();
		num_pieces = torrent.numOfPieces();
	}
	
	public BitSet getBitfield() {
		return bitfield;
	}
	
	public int numOfPieces() {
		return num_pieces;
	}

}
