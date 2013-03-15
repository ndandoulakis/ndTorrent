package com.ndtorrent.client;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class Torrent {
	private String name;
	private int piece_length;
	private long total_length;
	private String parent_path;
	private BTFile[] files;

	private int num_pieces;
	private BitSet available;
	private BitSet unregistered;
	// private BitSet rejected
	// private BitSet skip // pieces contained fully in skipped files
	private byte[] sha1_list;

	private Map<Integer, Piece> partial = new HashMap<Integer, Piece>();

	private ExecutorService reader;
	private ExecutorService writer;

	public Torrent(MetaInfo meta, String storage_location) {
		sha1_list = meta.getPieces();
		num_pieces = sha1_list.length / 20;

		available = new BitSet(num_pieces);
		// available.set(0, num_pieces); // test

		unregistered = new BitSet(num_pieces);
		unregistered.set(0, num_pieces);
		unregistered.andNot(available);

		name = meta.getName();

		parent_path = storage_location;
		if (meta.areMultipleFiles()) {
			parent_path += '/' + name;
		}

		files = BTFile.fromMetaInfo(meta);

		piece_length = meta.getPieceLength().intValue();
		total_length = 0;
		for (BTFile f : files) {
			total_length += f.getLength();
		}
	}

	public void open() throws IOException {
		for (BTFile f : files) {
			f.createFileAndPath(parent_path);
		}

		reader = Executors.newSingleThreadExecutor();
		writer = Executors.newSingleThreadExecutor();
	}

	public void close() {
		if (reader != null)
			reader.shutdownNow();

		if (writer != null)
			writer.shutdownNow();

		// TODO close files
	}

	public long getRemainingLength() {
		// TODO sum unregistered subtract partial
		return total_length;
	}

	public String getName() {
		return name;
	}

	public long getTotalLength() {
		return total_length;
	}

	public int numPieces() {
		return num_pieces;
	}

	public int numAvailablePieces() {
		return available.cardinality();
	}

	public BitSet getAvailablePieces() {
		return (BitSet) available.clone();
	}

	public Collection<Piece> getPartialPieces() {
		return partial.values();
	}

	public void registerPiece(int index) {
		if (index < 0 || index >= num_pieces)
			throw new IndexOutOfBoundsException("index: " + index);
		if (!unregistered.get(index)) {
			return;
		}

		int length = (index + 1) * piece_length <= total_length ? piece_length
				: (int) (total_length % piece_length);

		partial.put(index, new Piece(index, length));
		unregistered.flip(index);
	}

	public BitSet getUnregistered() {
		return (BitSet) unregistered.clone();
	}

	public void saveBlock(Message block) {
		// The corresponding piece must be registered and in partial state,
		// otherwise the block will be discarded.
		final int index = block.getPieceIndex();
		final Piece piece = partial.get(index);
		if (piece == null)
			return;

		piece.write(block);

		if (piece.isComplete()) {
			partial.remove(index);
			writer.submit(new Runnable() {
				@Override
				public void run() {
					if (!piece.isValid()) {
						// TODO reject
						return;
					}

					if (!savePiece(piece)) {
						// error
						return;
					}

					available.set(index, true);
				}
			});
		}
	}

	private boolean savePiece(Piece piece) {
		ByteBuffer data = piece.getData();
		data.rewind();
		long piece_offset = piece.getIndex() * piece_length;
		int start = Arrays.binarySearch(files, Long.valueOf(piece_offset));
		start = Math.max(start, (-start - 1) - 1);
		for (int i = start; i < files.length; i++) {
			BTFile f = files[i];
			try {
				f.write(data, i > start ? 0 : piece_offset - f.getOffset());
				if (!data.hasRemaining()) {
					return true;
				}
			} catch (IOException e) {
				e.printStackTrace();
				break;
			}
		}
		return false;
	}

	public Message loadBlock(Message request) {
		// The torrent must has the corresponding piece, otherwise the request
		// will be discarded.
		final int index = request.getPieceIndex();
		if (!available.get(index))
			return null;

		final Message block = Message.newBlock(index, request.getBlockBegin(),
				request.getBlockLength());

		block.setPreparedStatus(false);

		reader.submit(new Runnable() {
			@Override
			public void run() {
				readBlock(index, block);
				block.setPreparedStatus(true);
			}
		});

		return block;
	}

	private boolean readBlock(int index, Message block) {
		// Buffer's remaining length is expected to match block's length.
		ByteBuffer data = block.getData();
		long piece_offset = index * piece_length;
		int start = Arrays.binarySearch(files, Long.valueOf(piece_offset));
		start = Math.max(start, (-start - 1) - 1);
		int block_begin = block.getBlockBegin();
		for (int i = start; i < files.length; i++) {
			BTFile f = files[i];
			try {
				long ofs = index * piece_length + block_begin - f.getOffset();
				f.read(data, ofs < 0 ? 0 : ofs);
				if (!data.hasRemaining()) {
					return true;
				}
			} catch (IOException e) {
				e.printStackTrace();
				break;
			}
		}
		return false;
	}

}
