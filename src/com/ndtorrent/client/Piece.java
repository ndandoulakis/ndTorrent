package com.ndtorrent.client;

import java.nio.ByteBuffer;
import java.util.BitSet;

public final class Piece {
	private int total_blocks;
	private int block_length;
	private int tail_length;

	private ByteBuffer data;
	private BitSet written;
	private BitSet notrequested;
	private long modified_at;

	public Piece(int length) {
		this(length, 16 * 1024);
	}

	public Piece(int piece_length, int block_length) {
		data = ByteBuffer.allocate(piece_length);
		this.block_length = block_length;

		total_blocks = (piece_length + block_length - 1) / block_length;
		written = new BitSet(total_blocks);
		notrequested = new BitSet(total_blocks);
		notrequested.set(0, total_blocks, true);

		tail_length = piece_length % block_length;
		if (piece_length != 0 && tail_length == 0)
			tail_length = block_length;
	}

	public boolean isValid(/* expected_sha1 */) {
		// TODO calculate sha1
		return true;
	}

	public boolean isComplete() {
		return numWrittenBlocks() == total_blocks;
	}

	public void restoreRequested(BitSet requested) {
		notrequested.set(0, total_blocks, true);
		notrequested.andNot(requested);
		notrequested.andNot(written);
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
		return index + 1 == total_blocks ? tail_length : block_length;
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
	
	public long modifiedAt() {
		return modified_at;
	}

	public void write(Message block) {
		int length = block.getPayloadLength() - 2 * 4;
		int offset = block.getBlockBegin();
		if (!validBlockRegion(offset, length))
			throw new IllegalArgumentException();

		data.position(offset);
		data.put(block.getData().array(), 1 + 2 * 4, length);
		written.set(getBlockIndex(offset), true);
		modified_at = System.nanoTime();
	}

	public int numWrittenBlocks() {
		return written.cardinality();
	}

}
