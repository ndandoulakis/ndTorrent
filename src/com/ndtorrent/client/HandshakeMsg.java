package com.ndtorrent.client;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;

public final class HandshakeMsg {
	public static final String PROTOCOL = "BitTorrent protocol";
	public static final int HANDSHAKE_LENGTH = 49 + PROTOCOL.length();

	private ByteBuffer data = ByteBuffer.allocate(HANDSHAKE_LENGTH);

	private HandshakeMsg() {
	}

	public HandshakeMsg(String client_id, String info_hash) {
		try {
			data.put((byte) PROTOCOL.length());
			data.put(PROTOCOL.getBytes());
			data.putLong(0);
			data.put(info_hash.getBytes("ISO-8859-1"));
			data.put(client_id.getBytes("ISO-8859-1"));
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
	}

	public static HandshakeMsg newEmptyHandshake() {
		return new HandshakeMsg();
	}

	public ByteBuffer getData() {
		return data;
	}

	public String getInfoHash() {
		byte[] hash = new byte[20];
		ByteBuffer dup = data.duplicate();
		dup.position(1 + PROTOCOL.length() + 8);
		dup.get(hash, 0, 20);
		try {
			return new String(hash, "ISO-8859-1");
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
			return null;
		}
	}

}
