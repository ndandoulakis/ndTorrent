package com.ndtorrent.client;

import java.nio.ByteBuffer;
import java.util.BitSet;

public final class Piece {
	private ByteBuffer data;

	private int index;
	private int piece_length;
	private int num_blocks;
	private int block_length;
	private int tail_length;

	private BitSet available;
	private BitSet not_requested;
	private BitSet reserved;

	public Piece(int index, int length) {
		this(index, length, 1 * 1024);
	}

	public Piece(int index, int piece_length, int block_length) {
		data = ByteBuffer.allocate(piece_length);

		this.index = index;
		this.piece_length = piece_length;
		this.block_length = block_length;

		num_blocks = (piece_length + block_length - 1) / block_length;
		available = new BitSet(num_blocks);
		not_requested = new BitSet(num_blocks);
		not_requested.set(0, num_blocks, true);
		reserved = new BitSet(num_blocks);

		tail_length = piece_length % block_length;
		if (piece_length != 0 && tail_length == 0)
			tail_length = block_length;
	}

	public boolean isComplete() {
		return getRemainingLength() == 0;
	}

	public void restorePendingRequests(BitSet requests) {
		not_requested.set(0, num_blocks, true);
		not_requested.andNot(requests);
		not_requested.andNot(available);

		reserved.and(requests);
	}

	public void setBlocksAsReserved(int fromIndex, int toIndex) {
		reserved.set(fromIndex, toIndex, true);
	}

	public BitSet getReservedBlocks() {
		return reserved;
	}

	public void setBlocksAsRequested(int fromIndex, int toIndex) {
		not_requested.set(fromIndex, toIndex, false);
	}

	public BitSet getNotRequested() {
		return not_requested;
	}

	public ByteBuffer getData() {
		return data;
	}

	public int numBlocks() {
		return num_blocks;
	}

	public BitSet getAvailableBlocks() {
		return (BitSet) available.clone();
	}

	public int numAvailableBlocks() {
		return available.cardinality();
	}

	public int getIndex() {
		return index;
	}

	public int getLength() {
		return piece_length;
	}

	public int getRemainingLength() {
		int completed = available.cardinality() * block_length;
		if (available.get(num_blocks - 1))
			completed += tail_length - block_length;
		return piece_length - completed;
	}

	public int getBlockIndex(int offset) {
		return offset % block_length == 0 ? offset / block_length : -1;
	}

	public int getBlockOffset(int index) {
		return index * block_length;
	}

	public int getBlockLength(int index) {
		return index + 1 == num_blocks ? tail_length : block_length;
	}

	private boolean validBlockRegion(int offset, int length) {
		if (offset < 0 || offset % block_length != 0)
			return false;
		return offset + length <= data.capacity();
	}

	public void write(Message block) {
		int length = block.getPayloadLength() - 2 * 4;
		int offset = block.getBlockBegin();
		if (!validBlockRegion(offset, length))
			return;

		int start = getBlockIndex(offset);
		int block_length = getBlockLength(start);
		int nblocks = block.getBlockLength() / block_length;
		if (nblocks * block_length < block.getBlockLength())
			nblocks++;
		available.set(start, start + nblocks, true);
		not_requested.set(start, start + nblocks, false);

		data.position(offset);
		data.put(block.getData().array(), 1 + 2 * 4, length);
	}

}
