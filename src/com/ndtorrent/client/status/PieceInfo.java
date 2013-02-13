package com.ndtorrent.client.status;

import com.ndtorrent.client.Piece;

public final class PieceInfo {

	private final int index;
	private final int num_blocks;
	private final int num_written;

	public PieceInfo(int index, Piece piece) {
		this.index = index;
		num_blocks = piece.numBlocks();
		num_written = piece.numWrittenBlocks();
	}
	
	public int getIndex() {
		return index;
	}

	public int numBlocks() {
		return num_blocks;
	}

	public int numWrittenBlocks() {
		return num_written;
	}

}
