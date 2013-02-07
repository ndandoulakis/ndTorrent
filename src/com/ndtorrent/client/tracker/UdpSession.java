package com.ndtorrent.client.tracker;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Random;

public final class UdpSession extends Session {

	// Implements the UDP tracker protocol

	static final long CONNECTION_ID = 0x41727101980L;
	static final int ACTION_CONNECT = 0;
	static final int ACTION_ANNOUNCE = 1;
	static final int ACTION_SCRAPE = 2;
	static final int ACTION_ERROR = 3;
	static final int ACTION_ERROR_LE = 0x03000000;

	static final int MAX_REQUEST_LENGTH = 100;
	static final int MAX_RESPONSE_LENGTH = 1500;
	static final int MAX_TIMEOUT = 15 * 60;
	static final int DEFAULT_PORT = 80;

	Random random = new Random();

	DatagramSocket socket;
	ByteBuffer request = ByteBuffer.allocate(MAX_REQUEST_LENGTH);
	ByteBuffer response = ByteBuffer.allocate(MAX_RESPONSE_LENGTH);
	int received_bytes;

	int timeStep = 1;
	int transaction_id = -1;
	long connection_id = -1;
	long expire_time = 0; // immune to system's real-time clock adjustments

	URI tracker;

	public UdpSession(String url) {
		try {
			if (url.startsWith("udp"))
				tracker = new URI(url);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public void update() {
		timeStep = 1;
		try {
			socket = new DatagramSocket();
			performAction(ACTION_ANNOUNCE);

		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (socket != null) {
				socket.close();
				socket = null;
			}
		}
	}

	@Override
	public boolean validResponse() {
		return response.getInt(4) == transaction_id;
	}

	@Override
	public boolean trackerError() {
		int action = response.getInt(0);
		return validResponse()
				&& (action == ACTION_ERROR || action == ACTION_ERROR_LE);
	}

	@Override
	public int getInterval() {
		return response.getInt(8);
	}

	@Override
	public int getLeechers() {
		return response.getInt(12);
	}

	@Override
	public int getSeeders() {
		return response.getInt(16);
	}

	@Override
	public Collection<InetSocketAddress> getPeers() {
		ArrayList<InetSocketAddress> result = new ArrayList<InetSocketAddress>();

		for (int ofs = 20; ofs < received_bytes; ofs += 6) {
			if (ofs + 6 > MAX_RESPONSE_LENGTH)
				break;

			result.add(peerAddress(response, ofs));
		}

		return result;
	}

	// Protocol //

	private void performAction(int action) throws IOException {
		do {
			if (connectionExpired() && action != ACTION_CONNECT)
				performAction(ACTION_CONNECT);

			int timeout = 15 * timeStep;
			timeStep *= 2;

			if (timeout > MAX_TIMEOUT)
				return;

			transaction_id = random.nextInt();

			request.rewind();
			request.putLong(action == ACTION_CONNECT ? CONNECTION_ID
					: connection_id);
			request.putInt(action);
			request.putInt(transaction_id);

			if (action == ACTION_ANNOUNCE) {
				request.put(params.info_hash.getBytes("ISO-8859-1"));
				request.put(params.client_id.getBytes("ISO-8859-1"));
				request.putLong(params.downloaded);
				request.putLong(params.left);
				request.putLong(params.uploaded);
				request.putInt(params.event.toInteger());
				request.putInt(params.client_ip);
				request.putInt(params.key);
				request.putInt(params.num_want);
				request.putShort((short) params.client_port);
			}

			byte[] reqBlock = request.array();
			DatagramPacket out = new DatagramPacket(reqBlock, reqBlock.length);
			out.setAddress(InetAddress.getByName(tracker.getHost()));
			out.setPort(tracker.getPort() < 0 ? DEFAULT_PORT : tracker
					.getPort());

			socket.send(out);

			try {
				byte[] resBlock = response.array();
				DatagramPacket in = new DatagramPacket(resBlock,
						resBlock.length);
				socket.setSoTimeout(timeout * 1000);
				socket.receive(in);
				received_bytes = in.getLength();
			} catch (SocketTimeoutException e) {
			}

		} while (!validResponse());

		timeStep = 1;

		if (action == ACTION_CONNECT) {
			expire_time = (long) (System.nanoTime() + 6e10);
			connection_id = response.getLong(8);
		}
	}

	private boolean connectionExpired() {
		return System.nanoTime() > expire_time;
	}

}
