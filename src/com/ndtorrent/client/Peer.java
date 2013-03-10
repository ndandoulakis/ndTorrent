package com.ndtorrent.client;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.CopyOnWriteArrayList;

import com.ndtorrent.client.status.ConnectionInfo;
import com.ndtorrent.client.status.PieceInfo;
import com.ndtorrent.client.status.StatusObserver;
import com.ndtorrent.client.status.TorrentInfo;
import com.ndtorrent.client.status.TrackerInfo;
import com.ndtorrent.client.tracker.Event;
import com.ndtorrent.client.tracker.Session;

public final class Peer extends Thread {
	static final int MAX_PEERS = 40;
	static final long ONE_SECOND = (long) 1e9;

	private volatile boolean stop_requested;

	private ClientInfo client_info;
	private MetaInfo meta;
	private Torrent torrent;
	private Selector channel_selector;
	private Selector socket_selector;

	private List<Session> sessions = new ArrayList<Session>();

	// Expose status through local Data Transfer Object messages.
	private List<StatusObserver> observers = new CopyOnWriteArrayList<StatusObserver>();

	// Timestamps for methods that are executed periodically.
	private long last_status_at;

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
		System.out.println(meta.getPieceLength());
		System.out.println(torrent.getTotalLength());

		try {
			channel_selector = Selector.open();
			socket_selector = Selector.open();
			torrent.open();
		} catch (IOException e) {
			e.printStackTrace();
			stop_requested = true;
		}

		while (!stop_requested) {
			try {
				updateTrackerSessions();
				// update peer Set

				// a Selector doesn't clear the selected keys so it's our
				// responsibility to do it.
				socket_selector.selectedKeys().clear();
				socket_selector.selectNow();
				// processConnectMessages();
				processHandshakeMessages();
				removeBrokenSockets();

				configureChannelKeys();
				channel_selector.selectedKeys().clear();
				channel_selector.select(100);

				// processOutgoingMessages is called prior doing anything else,
				// mostly because it operates on selected keys. We get rid of
				// messages before new additions, especially for non selected
				// keys, and before connections are removed.
				processOutgoingMessages();
				removeBrokenChannels();
				removeFellowSeeders();
				removeDelayedRequests();
				restoreBrokenRequests();
				// restoreRejectedPieces();
				spawnOutgoingConnections();
				processIncomingMessages();
				advertisePieces();
				updateAmInterestedState();
				choking();
				requestMoreBlocks();
				// update input/output totals
				keepConnectionsAlive();

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

	private List<PeerChannel> getChannels() {
		List<PeerChannel> channels = new ArrayList<PeerChannel>();
		for (SelectionKey key : channel_selector.keys()) {
			channels.add((PeerChannel) key.attachment());
		}
		return channels;
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
			if ((!channel.amChoked() && channel.amInterested() && channel
					.canRequestMore()) || channel.hasOutgoingMessages())
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
		if (channel_selector.keys().size() >= MAX_PEERS) {
			socket.close();
			return;
		}

		// Multiple connections with same IP are not allowed.
		String ip = socket.getRemoteIP();
		for (SelectionKey key : channel_selector.keys()) {
			// incoming counter
			// outgoing counter
			PeerChannel peer = (PeerChannel) key.attachment();
			if (ip.equals(peer.socket.getRemoteIP())) {
				socket.close();
				return;
			}
		}

		PeerChannel channel = new PeerChannel();
		channel.socket = socket;
		channel.is_initiator = true; // local_port != torrent_port
		channel.advertiseBitfield(torrent.getAvailablePieces(),
				torrent.numPieces());

		try {
			socket.register(channel_selector, SelectionKey.OP_READ, channel);
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
		for (SelectionKey key : channel_selector.keys()) {
			PeerChannel channel = (PeerChannel) key.attachment();
			long last_input = channel.socket.lastInputMessageAt();
			long last_output = channel.socket.lastOutputMessageAt();
			boolean expired = now - Math.max(last_input, last_output) > 135 * 1e9;
			boolean is_error = channel.socket.isError();
			if (is_error || expired || !channel.socket.isOpen()) {
				// Registered sockets that get closed will eventually be removed
				// by the selector.
				channel.socket.close();
				System.out.printf("%s - REMOVED\n",
						channel.socket.getInetAddress());
			}
		}
	}

	private void removeFellowSeeders() {
		if (!isSeed())
			return;

		BitSet available = torrent.getAvailablePieces();
		for (SelectionKey key : channel_selector.keys()) {
			PeerChannel channel = (PeerChannel) key.attachment();
			if (channel.hasPieces(available))
				channel.socket.close();
		}
	}

	private void removeDelayedRequests() {
		// Since it's possible a remote peer to discard any request,
		// all pending requests are removed when the corresponding
		// piece has to be updated for more than one minute.

		Set<Entry<Integer, Piece>> partial_entries = torrent.getPartialPieces();
		long now = System.nanoTime();
		for (Entry<Integer, Piece> entry : partial_entries) {
			Piece piece = entry.getValue();
			int index = entry.getKey();
			if (now > piece.getTimeout()) {
				for (SelectionKey key : channel_selector.keys()) {
					PeerChannel channel = (PeerChannel) key.attachment();
					if (channel.hasPiece(index)) {
						// We can call this even if there are no requests.
						channel.removePendingRequests(index);
					}
				}
				piece.resetTimeout();
			}
		}
	}

	private void restoreBrokenRequests() {
		// If a block is flagged as requested but no channel has a corresponding
		// unfulfilled request, it's considered broken and must be restored.
		Set<Entry<Integer, Piece>> partial_entries = torrent.getPartialPieces();
		BitSet requested = new BitSet();
		for (Entry<Integer, Piece> entry : partial_entries) {
			requested.set(0, requested.length(), false);
			int index = entry.getKey();
			Piece piece = entry.getValue();
			for (SelectionKey key : channel_selector.keys()) {
				PeerChannel channel = (PeerChannel) key.attachment();
				channel.getRequested(requested, index, piece.getBlockLength());
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
		for (SelectionKey key : channel_selector.keys()) {
			PeerChannel channel = (PeerChannel) key.attachment();
			channel.advertise(available);
		}
	}

	private void updateAmInterestedState() {
		for (SelectionKey key : channel_selector.keys()) {
			PeerChannel channel = (PeerChannel) key.attachment();
			channel.updateAmInterested();
		}
	}

	private void choking() {
		// TODO update once per second

		if (isSeed())
			Choking.updateAsSeed(getChannels());
		else
			Choking.updateAsLeech(getChannels());
	}

	private void requestMoreBlocks() {
		if (isSeed())
			return;

		// Blocks of the same piece can be requested from different channels.
		// The number of channels that will contribute to a particular piece
		// depends on how many requests each channel can pipeline.
		Set<Entry<Integer, Piece>> partial_entries = torrent.getPartialPieces();
		for (SelectionKey key : channel_selector.selectedKeys()) {
			if (!key.isValid() || !key.isWritable())
				continue;
			PeerChannel channel = (PeerChannel) key.attachment();
			if (channel.amChoked() || !channel.amInterested())
				continue;
			for (Entry<Integer, Piece> entry : partial_entries) {
				if (!channel.canRequestMore())
					break;
				int index = entry.getKey();
				if (channel.hasPiece(index)) {
					Piece piece = entry.getValue();
					channel.addOutgoingRequests(index, piece);
				}
			}
			if (channel.canRequestMore()) {
				int index = selectUnregisteredPiece(channel);
				if (index < 0)
					continue;
				torrent.registerPiece(index);
			}
		}
	}

	private int selectUnregisteredPiece(PeerChannel channel_interested) {
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
				for (SelectionKey key : channel_selector.keys()) {
					PeerChannel channel = (PeerChannel) key.attachment();
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
		for (SelectionKey key : channel_selector.keys()) {
			PeerChannel peer = (PeerChannel) key.attachment();
			// If the socket has an outgoing message for more than 60 seconds,
			// it probably has stalled. In this case we don't add a keep-alive
			// message.
			// TODO? BTSocket.hasStalled(): hasOutput && upload_rate==0
			if (now - peer.socket.lastOutputMessageAt() > 60 * 1e9
					&& !peer.socket.hasOutputMessage()) {
				peer.addKeepAlive();
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

		long now = System.nanoTime();
		if (now - last_status_at < ONE_SECOND)
			return;

		last_status_at = now;

		List<ConnectionInfo> connections = new ArrayList<ConnectionInfo>();
		for (SelectionKey key : channel_selector.keys()) {
			PeerChannel channel = (PeerChannel) key.attachment();
			connections.add(new ConnectionInfo(channel));
		}

		List<PieceInfo> pieces = new ArrayList<PieceInfo>();
		Set<Entry<Integer, Piece>> partial_entries = torrent.getPartialPieces();
		for (Entry<Integer, Piece> entry : partial_entries) {
			pieces.add(new PieceInfo(entry.getKey(), entry.getValue()));
		}

		List<TrackerInfo> trackers = new ArrayList<TrackerInfo>();
		for (Session session : sessions) {
			trackers.add(new TrackerInfo(session));
		}

		String info_hash = meta.getInfoHash();
		for (StatusObserver o : observers) {
			o.asyncTorrentStatus(new TorrentInfo(torrent), info_hash);
			o.asyncTrackers(trackers, info_hash);
			o.asyncPieces(pieces, info_hash);
			o.asyncConnections(connections, info_hash);
		}
	}
}
