package com.ndtorrent.client;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.Map.Entry;

import com.ndtorrent.client.tracker.Event;
import com.ndtorrent.client.tracker.Session;

public final class Peer extends Thread {
	static final int MAX_PEERS = 40;

	private volatile boolean stop_requested;

	private ClientInfo client_info;
	private MetaInfo meta;
	private Torrent torrent;
	private Selector channel_selector;
	private Selector socket_selector;

	// TODO localDTOs to expose status; simpler than synchronizing the threads

	public Peer(ClientInfo client_info, MetaInfo meta_info) {
		super("PEER-THREAD");

		this.client_info = client_info;
		this.meta = meta_info;
		torrent = new Torrent(meta_info, client_info.getStorageLocation());
	}

	public void close() {
		stop_requested = true;
	}

	@Override
	public void run() {
		System.out.println(meta.getPieceLength());
		System.out.println(torrent.getTotalLength());

		// announceTorrent();

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
				removeExpiredHandshakes();

				// a Selector doesn't clear the selected keys so it's our
				// responsibility to do it.
				socket_selector.selectedKeys().clear();
				socket_selector.selectNow();
				// processConnectMessages();
				processHandshakeMessages();

				configureChannelKeys();
				channel_selector.selectedKeys().clear();
				channel_selector.select(100);

				// processOutgoingMessages is called prior doing anything else,
				// mostly because it operates on selected keys. We get rid of
				// messages before new additions, especially for non selected
				// keys, and before connections are removed.
				processOutgoingMessages();
				removeBrokenPeerChannels();
				// removeFellowSeeders();
				// restoreRejectedPieces();
				restoreBrokenRequests();
				spawnOutgoingConnections();
				processIncomingMessages();
				advertisePieces();
				updateAmInterestedState();
				regularUnchoking();
				optimisticUnchoking();
				requestMoreBlocks();
				// update input/output totals
				keepConnectionsAlive();

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

	private void removeExpiredHandshakes() {
		for (SelectionKey key : socket_selector.keys()) {
			BTSocket socket = (BTSocket) key.attachment();
			if (socket.isHandshakeExpired()) {
				key.cancel();
				socket.close();
			}
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

	private void configureChannelKeys() {
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

	public boolean isSeeding() {
		return false;
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
		// incoming counter
		// outgoing counter
		Set<String> ip_set = new HashSet<String>();
		for (SelectionKey key : channel_selector.keys()) {
			PeerChannel peer = (PeerChannel) key.attachment();
			ip_set.add(peer.socket.getRemoteIP());
		}

		// Multiple connections with same IP are not allowed.
		if (ip_set.contains(socket.getRemoteIP()) || ip_set.size() >= MAX_PEERS) {
			socket.close();
			return;
		}

		PeerChannel channel = new PeerChannel();
		channel.socket = socket;
		channel.is_initiator = true; // local_port != torrent_port
		channel.optimistic_candidate = true;
		channel.advertiseBitfield(torrent.getCompletePieces(),
				torrent.numOfPieces());

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

	private void removeBrokenPeerChannels() {
		// TODO remove when we're not seeding and:
		// 1. X minutes passed since last time we're interested in them
		// 2. we're choked for ~45 minutes
		long now = System.nanoTime();
		for (SelectionKey key : channel_selector.keys()) {
			PeerChannel channel = (PeerChannel) key.attachment();
			long last_input = channel.socket.lastInputMessageAt();
			long last_output = channel.socket.lastOutputMessageAt();
			boolean expired = now - Math.max(last_input, last_output) > 135 * 1e9;
			boolean input_error = channel.socket.isInputError();
			boolean output_error = channel.socket.isOutputError();
			if (input_error || output_error || expired
					|| !channel.socket.isOpen()) {
				// Registered sockets that get closed will eventually be removed
				// by the selector.
				channel.socket.close();
				System.out.printf("%s - REMOVED\n",
						channel.socket.getInetAddress());
			}
		}
	}

	private void restoreBrokenRequests() {
		// If a block is flagged as requested but no channel has a corresponding
		// unfulfilled request, it's considered broken and must be restored.
		// TODO pieces not updated for a minute; remove remaining requests.
		Set<Entry<Integer, Piece>> partialEntries = torrent.getPartialPieces();
		BitSet requested = new BitSet();
		for (Entry<Integer, Piece> entry : partialEntries) {
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
		// TODO keep every address (except repeated IPs) that we can't accept
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
		BitSet pieces = torrent.getCompletePieces();
		for (SelectionKey key : channel_selector.keys()) {
			PeerChannel channel = (PeerChannel) key.attachment();
			channel.advertise(pieces);
		}
	}

	private void updateAmInterestedState() {
		for (SelectionKey key : channel_selector.keys()) {
			PeerChannel channel = (PeerChannel) key.attachment();
			channel.updateAmInterested();
		}
	}

	private void regularUnchoking() {
		long now = System.nanoTime();

		// unchoke 1..3 regular peers
		// LEECHER MODE
		// regular if is_interested and fast upload rate

		// System.out.println("regular unchoking");
	}

	private void optimisticUnchoking() {
		// optimistic if is_interested (random)
		// unchoke 1..4 optimistic peers
		// LEECHER MODE
		// replace worst regular with optimistic
	}

	private void requestMoreBlocks() {
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
				if (!channel.hasPiece(index))
					continue;
				Piece piece = entry.getValue();
				channel.addOutgoingRequests(index, piece);
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

	private void announceTorrent() {
		System.out.println(meta.announce_list);

		String url = "udp://tracker.openbittorrent.com:80/announce";

		Session session = Session.create(url, client_info, meta.getInfoHash());
		session.update(Event.STARTED, 0, 0, torrent.getRemainingLength());

		if (session.isValidResponse()) {
			if (session.isTrackerError())
				System.out.println("tracker error!");
			else {
				System.out.println(session.getLeechers());
				System.out.println(session.getSeeders());
				System.out.println(session.getPeers());
			}
		} else {
			System.out.println("invalid tracker response");
		}

	}

}
