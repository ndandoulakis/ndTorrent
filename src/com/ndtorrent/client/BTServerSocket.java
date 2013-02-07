package com.ndtorrent.client;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class BTServerSocket extends Thread {

	public volatile boolean stop_requested;

	private List<Peer> handlers;
	private Selector selector;
	private ServerSocketChannel server;
	private int port;

	public BTServerSocket(int port) {
		super("BTSERVER_SOCKET-THREAD");
		this.handlers = new CopyOnWriteArrayList<Peer>();
		this.port = port;
	}

	public void addHandler(Peer peer) {
		if (peer != null) {
			handlers.add(peer);
		}
	}

	public void removeHandler(Peer peer) {
		handlers.remove(peer);
	}

	@Override
	public void run() {
		try {
			selector = Selector.open();
			server = ServerSocketChannel.open();
			server.socket().bind(new InetSocketAddress(port));
			server.configureBlocking(false);
		} catch (IOException e) {
			e.printStackTrace();
			stop_requested = true;
		}

		while (!stop_requested) {
			try {
				acceptIncoming();
				removeExpiredHandshakes();

				selector.selectedKeys().clear();
				selector.select(100);
				notifyHandlers();

			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		try {
			selector.close();
			server.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

		handlers.clear();
	}

	private void acceptIncoming() {
		while (true) {
			try {
				SocketChannel channel = server.accept();
				if (channel == null)
					break;
				BTSocket socket = new BTSocket(channel);
				socket.register(selector, SelectionKey.OP_READ, socket);
			} catch (IOException e) {
				e.printStackTrace();
				break;
			}
		}
	}

	private void removeExpiredHandshakes() {
		for (SelectionKey key : selector.keys()) {
			BTSocket socket = (BTSocket) key.attachment();
			if (socket.isHandshakeExpired()) {
				key.cancel();
				socket.close();
			}
		}
	}

	private void notifyHandlers() {
		NEXTKEY: for (SelectionKey key : selector.selectedKeys()) {
			if (!key.isValid())
				continue;
			BTSocket socket = (BTSocket) key.attachment();
			socket.processHandshakeMessages();
			if (socket.hasInputHandshake()) {
				key.cancel();
				for (Peer peer : handlers) {
					if (peer.addIncomingConnection(socket))
						continue NEXTKEY;
				}
				socket.close();
			}
		}
	}

}
