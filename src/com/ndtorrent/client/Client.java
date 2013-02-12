package com.ndtorrent.client;

import java.util.HashMap;
import java.util.Map;

import com.ndtorrent.client.status.StatusObserver;

public final class Client implements ClientInfo {

	public static final int DEFAULT_PORT = 45000;

	private String storage_location = "torrents";
	private int port;
	private String id = "BTCLIENTID1234567890";

	private BTServerSocket server;
	private Map<String, Peer> peers = new HashMap<String, Peer>();

	public void setServerPort(int port) {
		if (server != null)
			server.close();

		server = new BTServerSocket(port);
		server.start();
		for (Peer p : peers.values()) {
			server.addHandler(p);
		}
	}

	public String addTorrent(String filename) {
		MetaInfo meta = new MetaInfo(filename);
		String info_hash = meta.getInfoHash();
		if (info_hash == null)
			return null;
		
		if (peers.containsKey(info_hash))
			return info_hash;

		Peer peer = new Peer(this, meta);
		peers.put(info_hash, peer);
		if (server != null) {
			server.addHandler(peer);
		}
		peer.start();
		return info_hash;
	}

	public void close() {
		server.close();
		for (Peer peer : peers.values()) {
			peer.close();
		}
		for (Peer peer : peers.values()) {
			try {
				peer.join();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		peers.clear();
	}

	@Override
	public String getID() {
		return id;
	}

	@Override
	public int getPort() {
		return port;
	}

	@Override
	public String getStorageLocation() {
		return storage_location;
	}

	public void addStatusObserver(StatusObserver observer, String info_hash) {
		Peer peer = peers.get(info_hash);
		if (peer != null) {
			peer.addStatusObserver(observer);
		}
	}

}
