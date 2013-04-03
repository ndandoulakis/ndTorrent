package com.ndtorrent.client;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class Torrent {
	private MessageDigest sha1;

	private String name;
	private int piece_length;
	private int tail_length;
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

		try {
			sha1 = MessageDigest.getInstance("SHA-1");
		} catch (NoSuchAlgorithmException e) {
		}

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

		tail_length = (int) (total_length % piece_length);
		if (total_length != 0 && tail_length == 0)
			tail_length = piece_length;

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

		for (BTFile f : files) {
			f.close();
		}
	}

	public String getName() {
		return name;
	}

	public long getTotalLength() {
		return total_length;
	}

	public long getRemainingLength() {
		int registered = num_pieces - unregistered.cardinality();
		long length = registered * piece_length;

		if (!unregistered.get(num_pieces - 1))
			length -= piece_length - tail_length;

		for (Piece piece : partial.values()) {
			length -= piece.getRemainingLength();
		}

		return total_length - length;
	}

	public boolean isSeed() {
		// though not wrong, there is no reason to check availability
		// when unregistered pieces exist
		return !hasUnregisteredPieces() && numAvailablePieces() == numPieces();
	}

	public int numPieces() {
		return num_pieces;
	}

	public boolean hasAvailablePieces() {
		return !available.isEmpty();
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

	public boolean hasUnregisteredPieces() {
		return !unregistered.isEmpty();
	}

	public Piece registerPiece(int index) {
		if (index < 0 || index >= num_pieces)
			throw new IndexOutOfBoundsException("index: " + index);
		if (!unregistered.get(index)) {
			return null;
		}

		int length = (index + 1) * piece_length <= total_length ? piece_length
				: (int) (total_length % piece_length);

		Piece piece = new Piece(index, length);
		partial.put(index, piece);
		unregistered.flip(index);
		return piece;
	}

	public BitSet getUnregistered() {
		return (BitSet) unregistered.clone();
	}

	private boolean validHash(Piece piece) {
		ByteBuffer data = piece.getData();
		data.rewind();
		this.sha1.update(data);
		ByteBuffer sha1 = ByteBuffer.wrap(sha1_list, piece.getIndex() * 20, 20);
		return sha1.equals(ByteBuffer.wrap(this.sha1.digest()));
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

			if (!validHash(piece)) {
				unregistered.set(piece.getIndex());
				System.out.println("Bad hash: " + piece.getIndex());
				return;
			}

			writer.submit(new Runnable() {
				@Override
				public void run() {
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
		// The corresponding piece must be available, otherwise the
		// request will be discarded.
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
