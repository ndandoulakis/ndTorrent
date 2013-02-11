package com.ndtorrent.client;

import java.util.ArrayList;
import java.util.List;

public final class Client implements ClientInfo {

	public static final int DEFAULT_PORT = 45000;

	private String storage_location = "torrents";
	private int port;
	private String id = "BTCLIENTID1234567890";

	private BTServerSocket server;
	private List<Peer> peers = new ArrayList<Peer>();

	public void setServerPort(int port) {
		if (server != null)
			server.close();

		server = new BTServerSocket(port);
		server.start();
		for (Peer p : peers) {
			server.addHandler(p);
		}
	}

	public String addTorrent(String filename) {
		// TODO avoid adding same MetaInfo twice
		MetaInfo meta = new MetaInfo(filename);
		if (meta.getInfoHash() == null)
			return null;

		Peer peer = new Peer(this, meta);
		peers.add(peer);
		if (server != null) {
			server.addHandler(peer);
		}
		peer.start();
		return meta.getInfoHash();
	}

	public void close() {
		server.close();
		for (Peer peer : peers) {
			peer.close();
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

}
