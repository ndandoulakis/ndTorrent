package com.ndtorrent.client;

import java.nio.ByteBuffer;
import java.util.BitSet;

public final class Piece {
	public static final int SPEED_MODE_NONE = 0;
	public static final int SPEED_MODE_FAST = 1;
	public static final int SPEED_MODE_MEDIUM = 2;
	public static final int SPEED_MODE_SLOW = 3;

	static final long MINUTE = (long) (60 * 1e9);

	private ByteBuffer data;

	private int index;
	private int piece_length;
	private int num_blocks;
	private int block_length;
	private int tail_length;

	private int completed_length;

	private BitSet available;
	private BitSet not_requested;
	private BitSet reserved;
	// TODO BitSet pending;

	private int mode;
	private long mode_timeout;

	public Piece(int index, int length) {
		this(index, length, 2 * 1024);
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

		resetSpeedModeTimeout();
	}

	public boolean isValid(/* expected_sha1 */) {
		// TODO calculate sha1
		return true;
	}

	public boolean isComplete() {
		return piece_length == completed_length;
	}

	public void restorePendingRequests(BitSet requests) {
		not_requested.set(0, num_blocks, true);
		not_requested.andNot(requests);
		not_requested.andNot(available);

		reserved.and(requests);

		if (requests.isEmpty())
			setSpeedMode(Piece.SPEED_MODE_NONE);

	}

	public boolean hasPendingRequests() {
		int requests = num_blocks - not_requested.cardinality();
		return numAvailableBlocks() < requests;
	}

	public BitSet getPendingRequests() {
		BitSet requests = not_requested.get(0, num_blocks);
		requests.flip(0, num_blocks);
		requests.andNot(available);
		return requests;
	}

	public void setBlockAsReserved(int index) {
		if (index < 0 || index >= num_blocks)
			throw new IllegalArgumentException("Bad index: " + index);

		reserved.set(index, true);
	}

	public BitSet getReservedBlocks() {
		return reserved;
	}

	public void setBlockAsRequested(int index) {
		if (index < 0 || index >= num_blocks)
			throw new IllegalArgumentException("Bad index: " + index);

		not_requested.set(index, false);
	}

	public BitSet getNotRequested() {
		return not_requested;
	}

	public ByteBuffer getData() {
		return data;
	}

	public int getIndex() {
		return index;
	}

	public int getLength() {
		return piece_length;
	}

	public int getRemainingLength() {
		return piece_length - completed_length;
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
		if (offset + length < data.capacity() && length == block_length)
			return true;
		if (offset + length == data.capacity() && length == tail_length)
			return true;
		return false;
	}

	public void setSpeedMode(int mode) {
		this.mode = mode;
	}

	public int getSpeedMode() {
		return mode;
	}

	public void resetSpeedModeTimeout() {
		long now = System.nanoTime();
		if (mode == SPEED_MODE_SLOW)
			mode_timeout = now + MINUTE * 3;
		else
			mode_timeout = now + MINUTE / 2;
	}

	public long getSpeedModeTimeout() {
		return mode_timeout;
	}

	public void write(Message block) {
		int length = block.getPayloadLength() - 2 * 4;
		int offset = block.getBlockBegin();
		if (!validBlockRegion(offset, length))
			throw new IllegalArgumentException();

		int block_index = getBlockIndex(offset);

		if (!available.get(block_index)) {
			available.set(block_index, true);
			completed_length += length;
			data.position(offset);
			data.put(block.getData().array(), 1 + 2 * 4, length);
		}

		resetSpeedModeTimeout();
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

}
