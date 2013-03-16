package com.ndtorrent.client;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.ndtorrent.client.status.ConnectionInfo;
import com.ndtorrent.client.status.PieceInfo;
import com.ndtorrent.client.status.StatusObserver;
import com.ndtorrent.client.status.TorrentInfo;
import com.ndtorrent.client.status.TrackerInfo;
import com.ndtorrent.client.tracker.Event;
import com.ndtorrent.client.tracker.Session;

public final class Peer extends Thread {
	static final int MAX_CHANNELS = 80;
	static final long SECOND = (long) 1e9;

	private volatile boolean stop_requested;

	private MetaInfo meta;
	private Torrent torrent;
	private ClientInfo client_info;
	private Selector channel_selector;
	private Selector socket_selector;

	private List<PeerChannel> channels = new LinkedList<PeerChannel>();
	private List<Session> sessions = new ArrayList<Session>();

	// Expose status through local Data Transfer Object messages.
	private List<StatusObserver> observers = new CopyOnWriteArrayList<StatusObserver>();

	public Peer(ClientInfo client_info, MetaInfo meta_info) {
		super("PEER-THREAD");

		this.client_info = client_info;
		this.meta = meta_info;
		torrent = new Torrent(meta_info, client_info.getStorageLocation());

		String announce = meta.getAnnounce();
		List<String> trackers = meta.getAnnounceList();
		if (trackers.isEmpty() && announce != null) {
			trackers.add(announce);
		}
		for (String url : trackers) {
			sessions.add(Session.create(url, client_info, meta.getInfoHash()));
		}

		sessions.add(Session.create("udp://tracker.openbittorrent.com:80",
				client_info, meta.getInfoHash()));
	}

	public void close() {
		stop_requested = true;
	}

	@Override
	public void run() {
		try {
			channel_selector = Selector.open();
			socket_selector = Selector.open();
			torrent.open();
		} catch (IOException e) {
			e.printStackTrace();
			stop_requested = true;
		}

		long last_time = 0;

		while (!stop_requested) {
			try {
				// a Selector doesn't clear the selected keys so it's our
				// responsibility to do it.
				removeBrokenSockets();
				socket_selector.selectedKeys().clear();
				socket_selector.selectNow();
				// processConnectMessages();
				processHandshakeMessages();

				removeBrokenChannels();
				configureChannelKeys();
				channel_selector.selectedKeys().clear();
				channel_selector.select(100);

				// High priority //
				processIncomingMessages();
				processOutgoingMessages();
				requestMoreBlocks();

				// Low priority //
				// Operations that are performed once per second.
				long now = System.nanoTime();
				if (now - last_time < SECOND)
					continue;

				last_time = now;

				cancelDelayedRequests();
				restoreBrokenRequests();
				// restoreRejectedPieces();
				advertisePieces();
				updateAmInterestedState();
				choking();
				removeFellowSeeders();
				// update input/output totals
				keepConnectionsAlive();
				spawnOutgoingConnections();

				updateTrackerSessions();
				// update peer Set

				notifyStatusObservers();

			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		try {
			channel_selector.close();
			socket_selector.close();
			torrent.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	private void updateTrackerSessions() {
		long now = System.nanoTime();
		for (Session session : sessions) {
			if (session.isUpdating())
				continue;
			if (session.isConnectionTimeout())
				continue;
			if (session.isConnectionError())
				continue;
			if (session.isTrackerError())
				continue;

			long interval = now - session.updatedAt();
			if (interval < session.getInterval() * 1e9)
				continue;

			Event event = session.lastEvent();
			if (event == null)
				event = Event.STARTED;
			else if (event == Event.STARTED && session.isValidResponse())
				event = Event.REGULAR;

			session.update(event, 0, 0, torrent.getRemainingLength());
		}
	}

	private void processHandshakeMessages() {
		for (SelectionKey key : socket_selector.selectedKeys()) {
			if (!key.isValid() || key.isConnectable())
				continue;
			BTSocket socket = (BTSocket) key.attachment();
			if (key.isReadable() && socket.hasInputHandshake()) {
				key.interestOps(key.interestOps() & ~SelectionKey.OP_READ);
			}
			if (key.isWritable() && !socket.hasOutputHandshake()) {
				// We assume that socket's send buffer is empty at this
				// phase, and the whole handshake message will be written.
				key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
				socket.setOutputHandshake(new HandshakeMsg(client_info.getID(),
						meta.getInfoHash()));
			}
			socket.processHandshakeMessages();
			if (socket.isHandshakeDone()) {
				key.cancel();
				if (socket.isHandshakeSuccessful())
					addReadyConnection(socket);
				else
					socket.close();
			}
		}
	}

	private void removeBrokenSockets() {
		for (SelectionKey key : socket_selector.keys()) {
			BTSocket socket = (BTSocket) key.attachment();
			if (socket.isHandshakeExpired() || socket.isError()
					|| !socket.isOpen()) {
				key.cancel();
				socket.close();
			}
		}
	}

	private void configureChannelKeys() {
		// ? To avoid filling up the memory with too many pieces,
		// disable OP_READ if Torrent writer is busy.
		for (SelectionKey key : channel_selector.keys()) {
			if (!key.isValid())
				continue;
			PeerChannel channel = (PeerChannel) key.attachment();
			if (channel.hasOutgoingMessages())
				key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
			else
				key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
		}
	}

	public boolean isSeed() {
		return torrent.numAvailablePieces() == torrent.numPieces();
	}

	public boolean addIncomingConnection(BTSocket socket) {
		if (!socket.hasInputHandshake())
			return false;
		HandshakeMsg msg = socket.getInputHandshake();
		if (msg.getInfoHash().equals(meta.getInfoHash())) {
			try {
				return socket.register(socket_selector, SelectionKey.OP_WRITE,
						socket) != null;
			} catch (ClosedChannelException e) {
			}
		}
		return false;
	}

	private void addReadyConnection(BTSocket socket) {
		if (channels.size() >= MAX_CHANNELS) {
			socket.close();
			return;
		}

		// Multiple connections with same IP are not allowed.
		String ip = socket.getRemoteIP();
		for (PeerChannel channel : channels) {
			// incoming counter
			// outgoing counter
			if (ip.equals(channel.socket.getRemoteIP())) {
				socket.close();
				return;
			}
		}

		PeerChannel channel = new PeerChannel();
		channel.socket = socket;
		channel.is_initiator = true; // local_port != torrent_port
		channel.addBitfield(torrent.getAvailablePieces(), torrent.numPieces());

		try {
			socket.register(channel_selector, SelectionKey.OP_READ, channel);
			channels.add(channel);
			System.out
					.printf("incoming: %s\n", socket.getRemoteSocketAddress());

		} catch (IOException e) {
			e.printStackTrace();
			socket.close();
		}
	}

	private void processOutgoingMessages() {
		for (SelectionKey key : channel_selector.selectedKeys()) {
			if (!key.isValid() || !key.isWritable())
				continue;
			PeerChannel channel = (PeerChannel) key.attachment();
			channel.processOutgoingMessages();
		}
	}

	private void removeBrokenChannels() {
		// TODO remove when we're not seeding and:
		// 1. X minutes passed since last time we're interested in them
		// 2. we're choked for ~45 minutes
		long now = System.nanoTime();
		Iterator<PeerChannel> iter = channels.iterator();
		while (iter.hasNext()) {
			PeerChannel channel = iter.next();
			long last_input = channel.socket.lastInputMessageAt();
			long last_output = channel.socket.lastOutputMessageAt();
			boolean expired = now - Math.max(last_input, last_output) > 135 * 1e9;
			boolean is_error = channel.socket.isError();
			if (is_error || expired || !channel.socket.isOpen()) {
				// Registered sockets that get closed will eventually be removed
				// by the selector.
				channel.socket.close();
				iter.remove();
			}
		}
	}

	private void removeFellowSeeders() {
		if (!isSeed())
			return;

		BitSet available = torrent.getAvailablePieces();
		for (PeerChannel channel : channels) {
			if (channel.hasPieces(available))
				channel.socket.close();
		}
	}

	private void cancelDelayedRequests() {
		// Since it's possible a remote peer to discard any request,
		// all pending requests are removed when the corresponding
		// piece has to be updated for more than one minute.

		Collection<Piece> partial_entries = torrent.getPartialPieces();
		long now = System.nanoTime();
		for (Piece piece : partial_entries) {
			if (now > piece.getTimeout()) {
				int index = piece.getIndex();
				for (PeerChannel channel : channels) {
					if (channel.hasPiece(index)) {
						// We can call this even if there are no requests.
						channel.cancelPendingRequests(index);
					}
				}
				piece.resetTimeout();
			}
		}
	}

	private void restoreBrokenRequests() {
		// If a block is flagged as requested but no channel has a corresponding
		// unfulfilled request, it's considered broken and must be restored.
		Collection<Piece> partial_entries = torrent.getPartialPieces();
		BitSet requested = new BitSet();
		for (Piece piece : partial_entries) {
			requested.set(0, requested.length(), false);
			int piece_index = piece.getIndex();
			int block_length = piece.getBlockLength();
			for (PeerChannel channel : channels) {
				channel.getRequested(requested, piece_index, block_length);
			}
			piece.restoreRequested(requested);
		}
	}

	private void spawnOutgoingConnections() {
		// TODO keep every address (unique IPs) that we can't accept
		// due to max connections limit, for future outgoing connections.

		// Check channel_selector size + socket_selector size.
		// Consume the list of peer addresses in a round-robin manner.
		// Give priority to peers not yet collaborated
		// Doesn't matter if the list gets empty. It'll filled up again
		// through the peer tracking phase, and with the incoming connections.
	}

	private void processIncomingMessages() {
		for (SelectionKey key : channel_selector.selectedKeys()) {
			if (!key.isValid() || !key.isReadable())
				continue;
			PeerChannel channel = (PeerChannel) key.attachment();
			channel.processIncomingMessages();
			while (channel.hasUnprocessedIncoming()) {
				Message m = channel.takeUnprocessedIncoming();
				if (m.isPiece())
					torrent.saveBlock(m);
				else if (m.isBlockRequest())
					channel.addPiece(torrent.loadBlock(m));
				else
					channel.socket.close();
			}
		}
	}

	private void advertisePieces() {
		BitSet available = torrent.getAvailablePieces();
		for (PeerChannel channel : channels) {
			channel.advertise(available);
		}
	}

	private void updateAmInterestedState() {
		for (PeerChannel channel : channels) {
			channel.updateAmInterested();
		}
	}

	private void choking() {
		if (isSeed())
			Choking.updateAsSeed(channels);
		else
			Choking.updateAsLeech(channels);
	}

	private void requestMoreBlocks() {
		if (isSeed())
			return;

		// Blocks of the same piece can be requested from different channels.
		// The number of channels that will contribute to a particular piece
		// depends on how many requests each channel can pipeline.

		// If no available pieces, multiple random pieces may be registered.
		boolean first = !torrent.hasAvailablePieces();

		Collection<Piece> partial_entries = torrent.getPartialPieces();
		for (PeerChannel channel : channels) {
			if (channel.amChoked() || !channel.amInterested())
				continue;
			for (Piece piece : partial_entries) {
				if (!channel.canRequestMore())
					break;
				int index = piece.getIndex();
				if (channel.hasPiece(index)) {
					channel.addOutgoingRequests(piece);
				}
			}
			if (channel.canRequestMore()) {
				int index = first ? selectRandomPiece(channel)
						: selectRarePiece(channel);
				if (index < 0)
					continue;
				torrent.registerPiece(index);
				// Update partial_entries because we might prevent next channels
				// from registering a new piece, thus avoid piling up pieces.
				partial_entries = torrent.getPartialPieces();
			}
		}
	}

	private int selectRandomPiece(PeerChannel channel_interested) {
		BitSet unregistered = torrent.getUnregistered();
		BitSet available = channel_interested.getAvailablePieces();
		int index = -1;
		int nmatch = 0;
		int start_bit = available.nextSetBit(0);
		for (int i = start_bit; i >= 0; i = available.nextSetBit(i + 1)) {
			if (!unregistered.get(i))
				continue;
			if (Math.floor(Math.random() * ++nmatch) == 0)
				index = i;
		}
		return index;
	}

	private int selectRarePiece(PeerChannel channel_interested) {
		// The list of pieces is partitioned in 10 overlapped segments,
		// which are traversed in random order. From the first segment
		// with available pieces the least common piece is selected.
		// This algorithm does not select a piece that has the global
		// minimum availability, but a local one instead.

		Integer[] segments = { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9 };
		Collections.shuffle(Arrays.asList(segments));

		BitSet unregistered = torrent.getUnregistered();
		int pieces_length = unregistered.length();
		for (Integer seg : segments) {
			int min = Integer.MAX_VALUE;
			int index = -1;
			int nmatch = 0;
			for (int i = seg; i < pieces_length; i += 10) {
				if (!unregistered.get(i) || !channel_interested.hasPiece(i))
					continue;
				int availability = 0;
				for (PeerChannel channel : channels) {
					if (!channel.hasPiece(i))
						continue;
					if (++availability > min)
						break;
				}
				if (availability == 0 || availability > min)
					continue;
				// If the segment has n pieces with the same minimum value
				// we select one of them at random with probability 1/n.
				nmatch = availability == min ? nmatch + 1 : 0;
				if (availability < min
						|| Math.floor(Math.random() * nmatch) == 0) {
					min = availability;
					index = i;
				}
			}
			if (index >= 0)
				return index;
		}

		return -1;
	}

	private void keepConnectionsAlive() {
		long now = System.nanoTime();
		for (PeerChannel channel : channels) {
			// If the socket has an outgoing message for more than 60 seconds,
			// it probably has stalled. In this case we don't add a keep-alive
			// message.
			if (now - channel.socket.lastOutputMessageAt() > 60 * 1e9
					&& !channel.socket.hasOutputMessage()) {
				channel.addKeepAlive();
			}
		}
	}

	public void addStatusObserver(StatusObserver observer) {
		if (observer == null)
			throw new NullPointerException();

		observers.add(observer);
	}

	public void removeStatusObserver(StatusObserver observer) {
		observers.remove(observer);
	}

	private void notifyStatusObservers() {
		if (observers.isEmpty())
			return;

		List<ConnectionInfo> connections = new ArrayList<ConnectionInfo>();
		for (PeerChannel channel : channels) {
			connections.add(new ConnectionInfo(channel));
		}

		List<PieceInfo> pieces = new ArrayList<PieceInfo>();
		Collection<Piece> partial_entries = torrent.getPartialPieces();
		for (Piece piece : partial_entries) {
			pieces.add(new PieceInfo(piece));
		}

		List<TrackerInfo> trackers = new ArrayList<TrackerInfo>();
		for (Session session : sessions) {
			trackers.add(new TrackerInfo(session));
		}

		BitSet missing = new BitSet(torrent.numPieces());
		missing.set(0, torrent.numPieces());
		missing.andNot(torrent.getAvailablePieces());
		double input_rate = 0;
		double output_rate = 0;
		for (PeerChannel channel : channels) {
			input_rate += channel.socket.inputPerSec();
			output_rate += channel.socket.outputPerSec();
			missing.andNot(channel.getAvailablePieces());
		}

		TorrentInfo torrent_info = new TorrentInfo(torrent, missing,
				input_rate, output_rate);

		String info_hash = meta.getInfoHash();
		for (StatusObserver o : observers) {
			o.asyncTorrentStatus(torrent_info, info_hash);
			o.asyncTrackers(trackers, info_hash);
			o.asyncPieces(pieces, info_hash);
			o.asyncConnections(connections, info_hash);
		}
	}
}
