package com.ndtorrent.client;

import java.util.BitSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

public final class PeerChannel implements Comparable<PeerChannel> {
	static final int MAX_REQUESTS = 255;

	// A rolling total longer than the choking round can make the
	// rating a bit more accurate due to data transmission delays.
	private static final int ROLLING_SECS = 15;

	public BTSocket socket;

	private BitSet available = new BitSet();
	private BitSet advertised = new BitSet();
	private BitSet participated = new BitSet(); // Pieces received

	private boolean am_initiator;
	public boolean is_banned;
	public boolean is_questionable; // participated in rejected pieces

	private boolean am_snubbed;
	private boolean is_optimistic;
	private boolean former_optimistic;

	private boolean am_choked = true;
	private boolean is_choked = true;
	private boolean am_interested;
	private boolean is_interested;

	// Reciprocation round
	private long unchoke_end_time;
	private RollingTotal blocks_total = new RollingTotal(ROLLING_SECS);

	private LinkedList<Message> incoming = new LinkedList<Message>();
	private LinkedList<Message> outgoing = new LinkedList<Message>();

	// Distinct list for Piece messages to reduce "iterate and filter" code.
	private LinkedList<Message> outgoing_pieces = new LinkedList<Message>();

	// Requests and pieces the client has received.
	private LinkedList<Message> unprocessed_pieces = new LinkedList<Message>();
	private LinkedList<Message> unprocessed_requests = new LinkedList<Message>();

	// Requests the client has sent.
	private LinkedList<Message> unfulfilled = new LinkedList<Message>();

	@Override
	public int compareTo(PeerChannel other) {
		// Blocks total comparison for descending order, c2 > c1
		return (int) Math.signum(other.avgBlocksTotal() - avgBlocksTotal());
	}

	public int numAvailablePieces() {
		return available.cardinality();
	}

	public int numIncomingRequests() {
		return outgoing_pieces.size() + unprocessed_requests.size();
	}

	public int numOutgoingRequests() {
		return unfulfilled.size();
	}

	public void rollBlocksTotal() {
		blocks_total.roll();
		blocks_total.add(socket.blocksInputTotal());
		socket.clearBlocksInputTotal();
	}

	public double avgBlocksTotal() {
		return blocks_total.average();
	}

	public void processIncomingMessages() {
		receiveIncoming();
		processIncoming();
	}

	public void processOutgoingMessages() {
		// Notification messages have higher priority and are sent ASAP,
		// because subsequent Pieces in a slow upload channel can block
		// peers from communicating.
		sendOutgoing(outgoing);
		sendOutgoing(outgoing_pieces);
	}

	public boolean hasOutgoingMessages() {
		return socket.hasOutputMessage() || hasReadyOutgoingPiece()
				|| !outgoing.isEmpty();
	}

	private boolean hasReadyOutgoingPiece() {
		for (Message m : outgoing_pieces) {
			if (m.isPrepared())
				return true;
		}
		return false;
	}

	public boolean hasUnprocessedIncoming() {
		return !unprocessed_pieces.isEmpty() || !unprocessed_requests.isEmpty();
	}

	public Message takeUnprocessedIncoming() {
		// Returns null if no message exists.
		if (!unprocessed_pieces.isEmpty())
			return unprocessed_pieces.pollFirst();
		else
			return unprocessed_requests.pollFirst();
	}

	public boolean participatedIn(int piece_index) {
		return participated.get(piece_index);
	}

	public boolean hasPiece(int index) {
		return available.get(index);
	}

	public boolean hasPieces(BitSet pieces) {
		BitSet common = (BitSet) available.clone();
		common.and(pieces);
		return common.equals(pieces);
	}

	public BitSet getAvailablePieces() {
		return available;
	}

	public BitSet findNotRequested(Piece piece) {
		BitSet requests = getPendingRequests(piece);
		requests.or(piece.getAvailableBlocks());
		requests.flip(0, piece.numBlocks());
		return requests;
	}

	public BitSet getPendingRequests(Piece piece) {
		// TODO make unfulfilled an ordered ArrayList and use binary search to
		// locate the requests.
		BitSet requests = new BitSet(piece.numBlocks());
		for (Message m : unfulfilled) {
			if (m.getPieceIndex() == piece.getIndex()) {
				// The requested length is a multiple of block length.
				int start = piece.getBlockIndex(m.getBlockBegin());
				int block_length = piece.getBlockLength(start);
				int nblocks = m.getBlockLength() / block_length;
				if (nblocks * block_length < m.getBlockLength())
					nblocks++;
				requests.set(start, start + nblocks);
			}
		}
		return requests;
	}

	public boolean canRequestMore() {
		// A small number of pipelined requests, i.e. 10, on fast channels,
		// can result to bad download rates even on local connections!
		if (avgBlocksTotal() < 1000) {
			return !socket.hasPartialInputMesssage()
					&& numOutgoingRequests() < 1;
		} else {
			final int REQUESTS = 2 + (int) (avgBlocksTotal() / (8 * 1024));
			return numOutgoingRequests() < Math.min(REQUESTS, MAX_REQUESTS);
		}
	}

	private int maxRequestLength() {
		// The length (1k, 4k and 16k) depends on the download speed.
		double speed = avgBlocksTotal();
		if (speed < 1024)
			return 1024;
		else if (speed < 4 * 1024)
			return 4 * 1024;
		else
			return 16 * 1024;
	}

	public void addMaximumRequests(Piece piece, BitSet blocks) {
		// The number of pipelined requests and the length of each
		// requested block (1k..16k) depend on the download speed.
		if (!canRequestMore())
			return;
		long now = System.nanoTime();
		int max_length = maxRequestLength();
		int length;
		int start_bit = blocks.nextSetBit(0);
		int end_bit;
		for (int i = start_bit; i >= 0; i = blocks.nextSetBit(end_bit)) {
			length = piece.getBlockLength(i);
			end_bit = i + 1;

			// Include as many consecutive blocks as possible.
			start_bit = blocks.nextSetBit(end_bit);
			for (int j = start_bit; j >= 0; j = blocks.nextSetBit(end_bit)) {
				if (j != end_bit || length >= max_length)
					break;
				length += piece.getBlockLength(j);
				end_bit = j + 1;
			}

			// May be set multiple times on end-game.
			piece.setBlocksAsRequested(i, end_bit);
			piece.setBlocksAsReserved(i, end_bit);

			int index = piece.getIndex();
			int offset = piece.getBlockOffset(i);
			Message m = Message.newBlockRequest(index, offset, length);
			m.setTimestamp((long) (now + 20 * 1e9));
			outgoing.add(m);
			unfulfilled.add(m);

			if (!canRequestMore())
				return;
		}
	}

	public void addBitfield(BitSet pieces, int nbits) {
		advertised = pieces;
		if (advertised.cardinality() > 0)
			outgoing.add(Message.newBitfield(advertised, nbits));
	}

	public void advertise(BitSet pieces) {
		advertised.xor(pieces);
		int start_bit = advertised.nextSetBit(0);
		for (int i = start_bit; i >= 0; i = advertised.nextSetBit(i + 1)) {
			outgoing.add(Message.newHavePiece(i));
		}
		advertised.or(pieces);
	}

	public void setAmInitiator(boolean initiator) {
		am_initiator = initiator;
	}

	public boolean amInitiator() {
		return am_initiator;
	}

	public void setAmSnubbed(boolean snubbed) {
		am_snubbed = snubbed;
	}

	public boolean amSnubbed() {
		// If true, it'll clear on optimistic unchoking.
		return am_snubbed;
	}

	public void setIsOptimistic(boolean optimistic) {
		is_optimistic = optimistic;
	}

	public boolean isOptimistic() {
		return is_optimistic;
	}

	public void setFormerOptimistic(boolean former) {
		former_optimistic = former;
	}

	public boolean isFormerOptimistic() {
		return former_optimistic;
	}

	public boolean amChoked() {
		return am_choked;
	}

	public boolean isChoked() {
		return is_choked;
	}

	public boolean amInterested() {
		return am_interested;
	}

	public boolean isInterested() {
		return is_interested;
	}

	public void setUnchokeEndTime(long at) {
		unchoke_end_time = at;
	}

	public long getUnchokeEndTime() {
		return unchoke_end_time;
	}

	public void updateIsChoked(boolean choke) {
		if (is_choked == choke)
			return;
		is_choked = choke;
		if (is_choked) {
			outgoing.add(Message.newChoke());
			outgoing_pieces.clear();
			unprocessed_requests.clear();
		} else {
			outgoing.add(Message.newUnchoke());
		}
	}

	public void updateAmInterested() {
		BitSet missing = (BitSet) available.clone();
		missing.andNot(advertised);
		boolean be_interested = missing.nextSetBit(0) >= 0;
		if (am_interested == be_interested)
			return;
		am_interested = be_interested;
		if (am_interested) {
			outgoing.add(Message.newInterested());
		} else {
			outgoing.add(Message.newNotInterested());
			removeOutgoingRequests();
		}
	}

	public void addPiece(Message m) {
		if (m == null)
			return;
		if (!m.isPiece())
			throw new IllegalArgumentException(m.getType());

		outgoing_pieces.add(m);
	}

	public void addKeepAlive() {
		outgoing.add(Message.newKeepAlive());
	}

	private void removeOutgoingPiece(Message m) {
		Iterator<Message> iter = outgoing_pieces.iterator();
		while (iter.hasNext()) {
			if (m.sameBlockRegion(iter.next())) {
				iter.remove();
				return;
			}
		}
	}

	private void removeOutgoingRequests() {
		unfulfilled.clear();
		Iterator<Message> iter = outgoing.iterator();
		while (iter.hasNext()) {
			if (iter.next().isBlockRequest())
				iter.remove();
		}
	}

	public void cancelAvailableBlocks(Collection<Piece> pieces) {
		for (Piece piece : pieces) {
			cancelPendingRequests(piece, piece.getAvailableBlocks());
		}
	}

	public void cancelPendingRequests(Piece piece, BitSet blocks) {
		// To remove timed out requests, pass null piece and null blocks.
		// To remove every request of a specific piece, pass null blocks.
		// To remove specific requests, pass the piece and the blocks.

		long now = System.nanoTime();

		Iterator<Message> iter;
		iter = unfulfilled.iterator();
		while (iter.hasNext()) {
			Message m = iter.next();
			boolean expired = piece == null && now >= m.getTimestamp();
			if (expired
					|| (piece != null && m.getPieceIndex() == piece.getIndex())) {
				int offset = m.getBlockBegin();
				int block_index = offset / 1024;
				if (blocks != null && !blocks.get(block_index))
					continue;
				int piece_index = m.getPieceIndex();
				int length = m.getBlockLength();
				outgoing.add(Message.newCancel(piece_index, offset, length));
				iter.remove();
			}
		}
		iter = outgoing.iterator();
		while (iter.hasNext()) {
			Message m = iter.next();
			if (!m.isBlockRequest())
				continue;
			boolean expired = piece == null && now >= m.getTimestamp();
			if (expired
					|| (piece != null && m.getPieceIndex() == piece.getIndex())) {
				int block_index = m.getBlockBegin() / 1024;
				if (blocks != null && !blocks.get(block_index))
					continue;
				iter.remove();
			}
		}
	}

	private void removeUnprocessedRequest(Message m) {
		Iterator<Message> iter = unprocessed_requests.iterator();
		while (iter.hasNext()) {
			if (m.sameBlockRegion(iter.next())) {
				iter.remove();
				return;
			}
		}
	}

	private void receiveIncoming() {
		while (true) {
			socket.processInput();
			if (!socket.hasInputMessage())
				return;
			incoming.add(socket.takeInputMessage());
		}
	}

	private void sendOutgoing(List<Message> messages) {
		socket.processOutput();
		Iterator<Message> iter = messages.iterator();
		while (iter.hasNext()) {
			Message m = iter.next();
			if (!m.isPrepared())
				continue;
			if (socket.hasOutputMessage())
				return;
			// System.out.printf("sent %s, %d\n", m.getType(), m.getLength());
			socket.setOutputMessage(m);
			socket.processOutput();
			iter.remove();
		}
	}

	private void processIncoming() {
		while (!incoming.isEmpty()) {
			Message m = incoming.pollFirst();

			// System.out.printf("got %s, %d\n", m.getType(), m.getLength());

			if (m.isKeepAlive())
				continue;

			switch (m.getID()) {
			case Message.CHOKE:
				onChoke(m);
				break;
			case Message.UNCHOKE:
				onUnchoke(m);
				break;
			case Message.INTERESTED:
				onInterested(m);
				break;
			case Message.NOT_INTERESTED:
				onNotInterested(m);
				break;
			case Message.HAVE:
				onHave(m);
				break;
			case Message.BITFIELD:
				onBitfield(m);
				break;
			case Message.REQUEST:
				onRequest(m);
				break;
			case Message.PIECE:
				onPiece(m);
				break;
			case Message.CANCEL:
				onCancel(m);
				break;

			default:
				socket.close();
				return;
			}
		}
	}

	private void onChoke(Message m) {
		am_choked = true;
		removeOutgoingRequests();
	}

	private void onUnchoke(Message m) {
		am_choked = false;
	}

	private void onInterested(Message m) {
		is_interested = true;
	}

	private void onNotInterested(Message m) {
		is_interested = false;

		// Discard pending pieces because it might not get choked.
		outgoing_pieces.clear();
		unprocessed_requests.clear();
	}

	private void onHave(Message m) {
		available.set(m.getPieceIndex());
	}

	private void onBitfield(Message m) {
		// TODO if not isValidBitfield close the socket
		available = m.toBitSet();
	}

	private void onRequest(Message m) {
		if (!is_choked && numIncomingRequests() < MAX_REQUESTS)
			unprocessed_requests.add(m);
	}

	private void onPiece(Message m) {
		Iterator<Message> iter = unfulfilled.iterator();
		while (iter.hasNext()) {
			Message request = iter.next();
			if (m.sameBlockRegion(request)) {
				iter.remove();
				break;
			}
		}
		// If an unfulfilled request wasn't found, the request probably was
		// canceled because the block is delayed. We enqueue it for further
		// processing anyway.
		unprocessed_pieces.add(m);
		participated.set(m.getPieceIndex());
	}

	private void onCancel(Message m) {
		// The request is either already processed and the piece is enqueued,
		removeOutgoingPiece(m);
		// or unprocessed.
		removeUnprocessedRequest(m);
	}

}
