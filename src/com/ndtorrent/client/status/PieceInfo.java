package com.ndtorrent.client.status;

import com.ndtorrent.client.Piece;

public final class PieceInfo {

	private final int index;
	private final int num_blocks;
	private final int num_available;

	public PieceInfo(int index, Piece piece) {
		this.index = index;
		num_blocks = piece.numBlocks();
		num_available = piece.numAvailableBlocks();
	}

	public int getIndex() {
		return index;
	}

	public int numBlocks() {
		return num_blocks;
	}

	public int numAvailableBlocks() {
		return num_available;
	}

}
