package com.ndtorrent.client;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class Torrent {
	private int piece_length;
	private long total_length;
	private String parent_path;
	private BTFile[] files;

	private int num_pieces;
	private BitSet bitfield;
	private BitSet unregistered;
	// private BitSet rejected
	// private BitSet skip // pieces contained fully in skipped files
	private byte[] sha1_list;

	private Map<Integer, Piece> partial = new HashMap<Integer, Piece>();

	private ExecutorService writer;

	public Torrent(MetaInfo meta, String storage_location) {
		sha1_list = meta.getPieces();
		num_pieces = sha1_list.length / 20;
		bitfield = new BitSet(num_pieces);
		unregistered = new BitSet(num_pieces);
		unregistered.set(0, num_pieces, true);

		parent_path = storage_location;
		if (meta.areMultipleFiles()) {
			parent_path += '/' + meta.getName();
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

		writer = Executors.newSingleThreadExecutor();
	}

	public void close() {
		// shutdown services, files, etc
	}

	public long getRemainingLength() {
		// TODO sum unregistered subtract partial
		return total_length;
	}

	public long getTotalLength() {
		return total_length;
	}

	public int numOfPieces() {
		return num_pieces;
	}

	public BitSet getCompletePieces() {
		return (BitSet) bitfield.clone();
	}

	public Set<Entry<Integer, Piece>> getPartialPieces() {
		return partial.entrySet();
	}

	public void registerPiece(int index) {
		if (index < 0 || index >= num_pieces)
			throw new IndexOutOfBoundsException("index: " + index);
		if (!unregistered.get(index))
			return;

		int length = (index + 1) * piece_length <= total_length ? piece_length
				: (int) (total_length % piece_length);

		partial.put(index, new Piece(length));
		// TODO synchronize bitfield
		unregistered.flip(index);
	}

	public BitSet getUnregistered() {
		// TODO synchronize bitfield
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
					if (piece.isValid() && savePiece(index, piece)) {
						// TODO synchronize bitfield
						bitfield.set(index, true);
					} else {
						// TODO reject
					}
				}
			});
		}
	}

	private boolean savePiece(int index, Piece piece) {
		ByteBuffer data = piece.getData();
		data.rewind();
		long piece_offset = index * piece_length;
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
		return new Message(null) {
			// The data are read the first time getData() is called.

			@Override
			public byte getID() {
				return PIECE;
			}

			@Override
			public int getLength() {
				return 0;
			}

			@Override
			public ByteBuffer getData() {
				if (data == null) {
					data = ByteBuffer.allocate(getLength());
					data.put(getID());
					// read ...
				}
				return data;
			}
		};
	}

}
