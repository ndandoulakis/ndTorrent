package com.ndtorrent.client.status;

import java.util.BitSet;

import com.ndtorrent.client.Piece;

public final class PieceInfo {

	private final int index;
	private final int length;
	private final int num_blocks;
	private final BitSet available;

	public PieceInfo(int index, Piece piece) {
		this.index = index;
		length = piece.getLength();
		num_blocks = piece.numBlocks();
		available = piece.getAvailableBlocks();
	}

	public int getIndex() {
		return index;
	}

	public int getLength() {
		return length;
	}

	public int numBlocks() {
		return num_blocks;
	}

	public int numAvailableBlocks() {
		return available.cardinality();
	}

	public BitSet getAvailableBlocks() {
		return available;
	}

}
