package com.ndtorrent.client;

import java.nio.ByteBuffer;
import java.util.BitSet;

public final class Piece {
	static final long ONE_MINUTE = (long) (60 * 1e9);

	private int num_blocks;
	private int block_length;
	private int tail_length;

	private ByteBuffer data;
	private BitSet available;
	private BitSet notrequested;
	private long timeout;

	public Piece(int length) {
		this(length, 16 * 1024);
	}

	public Piece(int piece_length, int block_length) {
		data = ByteBuffer.allocate(piece_length);
		this.block_length = block_length;

		num_blocks = (piece_length + block_length - 1) / block_length;
		available = new BitSet(num_blocks);
		notrequested = new BitSet(num_blocks);
		notrequested.set(0, num_blocks, true);

		tail_length = piece_length % block_length;
		if (piece_length != 0 && tail_length == 0)
			tail_length = block_length;

		resetTimeout();
	}

	public boolean isValid(/* expected_sha1 */) {
		// TODO calculate sha1
		return true;
	}

	public boolean isComplete() {
		return numAvailableBlocks() == num_blocks;
	}

	public void restoreRequested(BitSet requested) {
		notrequested.set(0, num_blocks, true);
		notrequested.andNot(requested);
		notrequested.andNot(available);
	}

	public BitSet getNotRequested() {
		return notrequested;
	}

	public ByteBuffer getData() {
		return data;
	}

	public int getBlockIndex(int offset) {
		return offset % block_length == 0 ? offset / block_length : -1;
	}

	public int getBlockOffset(int index) {
		return index * block_length;
	}

	public int getBlockLength() {
		return block_length;
	}

	public int getBlockLength(int index) {
		return index + 1 == num_blocks ? tail_length : block_length;
	}

	private boolean validBlockRegion(int offset, int length) {
		if (offset < 0 || offset % block_length != 0)
			return false;
		if (offset + length < data.capacity() && length == block_length)
			return true;
		if (offset + length == data.capacity() && length == tail_length)
			return true;
		return false;
	}

	public void resetTimeout() {
		long now = System.nanoTime();
		timeout = now + ONE_MINUTE;
	}

	public long getTimeout() {
		return timeout;
	}

	public void write(Message block) {
		int length = block.getPayloadLength() - 2 * 4;
		int offset = block.getBlockBegin();
		if (!validBlockRegion(offset, length))
			throw new IllegalArgumentException();

		data.position(offset);
		data.put(block.getData().array(), 1 + 2 * 4, length);
		available.set(getBlockIndex(offset), true);
		resetTimeout();
	}

	public int numBlocks() {
		return num_blocks;
	}

	public int numAvailableBlocks() {
		return available.cardinality();
	}

}
