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

	public static void main(String[] args) {
		Client client = new Client();
		client.setServerPort(Client.DEFAULT_PORT);
		client.addTorrent("test_big.torrent");
	}

	public void setServerPort(int port) {
		if (server != null)
			server.stop_requested = true;

		server = new BTServerSocket(port);
		server.start();
		for (Peer p : peers) {
			server.addHandler(p);
		}
	}

	public void addTorrent(String filename) {
		// TODO adding same MetaInfo again should fail
		MetaInfo meta = new MetaInfo(filename);
		if (meta.getInfoHash() == null)
			return;

		Peer peer = new Peer(this, meta);
		peers.add(peer);
		if (server != null) {
			server.addHandler(peer);
		}
		peer.start();
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
