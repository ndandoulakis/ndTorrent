package com.ndtorrent.client.tracker;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
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

import com.ndtorrent.client.ClientInfo;

public final class UdpSession extends Session implements Runnable {

	// Implements the UDP tracker protocol

	static final long CONNECTION_ID = 0x41727101980L;
	static final int ACTION_CONNECT = 0;
	static final int ACTION_ANNOUNCE = 1;
	static final int ACTION_SCRAPE = 2;
	static final int ACTION_ERROR = 3;
	static final int ACTION_ERROR_LE = 0x03000000;

	static final int REQUEST_BODY_LENGTH = 2 * 20 + 3 * 8 + 4 * 4 + 2;
	static final int MAX_REQUEST_LENGTH = 100;
	static final int MAX_RESPONSE_LENGTH = 1500;
	static final int MAX_TIMEOUT = 2 * 60;
	static final int DEFAULT_PORT = 80;

	private Thread thread;

	private Random random = new Random();

	private DatagramSocket socket;
	private ByteBuffer request_body;
	private ByteBuffer request = ByteBuffer.allocate(MAX_REQUEST_LENGTH);
	private volatile ByteBuffer response = ByteBuffer.allocate(0);
	
	private volatile boolean is_timeout; 

	private int timeStep = 1;
	private int transaction_id = -1;
	private long connection_id = -1;
	private long expire_time = 0;

	private URI tracker;

	public UdpSession(String url, ClientInfo client_info, String info_hash) {
		super(client_info, info_hash);
		try {
			tracker = new URI(url);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public String getUrl() {
		return tracker != null ? tracker.toString() : null;
	}
	
	@Override
	public boolean isConnectionTimeout() {
		return is_timeout;
	}

	@Override
	public void update(Event event, long uploaded, long downloaded, long left) {
		if (isUpdating())
			return;
		
		is_timeout = false;

		request_body = ByteBuffer.allocate(REQUEST_BODY_LENGTH);
		try {
			// Prepare Request
			request_body.put(info_hash.getBytes("ISO-8859-1"));
			request_body.put(client_info.getID().getBytes("ISO-8859-1"));
			request_body.putLong(downloaded);
			request_body.putLong(left);
			request_body.putLong(uploaded);
			request_body.putInt(event.toInteger());
			request_body.putInt(0); // IP
			request_body.putInt(0); // key
			request_body.putInt(-1); // num_want
			request_body.putShort((short) client_info.getPort());

			// Run
			thread = new Thread(this);

		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
	}

	@Override
	public boolean isUpdating() {
		return thread != null && thread.isAlive();
	}

	@Override
	public void run() {
		timeStep = 1;
		try {
			socket = new DatagramSocket();
			performAction(ACTION_ANNOUNCE);

		} catch (IOException e) {
			// TODO save the connection error
			e.printStackTrace();
		} finally {
			if (socket != null) {
				socket.close();
				socket = null;
			}
		}
	}

	private int responseIntValue(int index) {
		// Safe read; make sure we examine the same object
		final ByteBuffer response = this.response;
		return index + 3 < response.limit() ? response.getInt(index) : 0;
	}

	@Override
	public boolean isValidResponse() {
		return responseIntValue(4) == transaction_id;
	}

	@Override
	public boolean isTrackerError() {
		int action = responseIntValue(0);
		return isValidResponse()
				&& (action == ACTION_ERROR || action == ACTION_ERROR_LE);
	}

	@Override
	public int getInterval() {
		return responseIntValue(8);
	}

	@Override
	public int getLeechers() {
		return responseIntValue(12);
	}

	@Override
	public int getSeeders() {
		return responseIntValue(16);
	}

	@Override
	public Collection<InetSocketAddress> getPeers() {
		ArrayList<InetSocketAddress> result = new ArrayList<InetSocketAddress>();

		final ByteBuffer response = this.response;
		for (int ofs = 20; ofs < response.limit(); ofs += 6) {
			if (ofs + 6 > MAX_RESPONSE_LENGTH)
				break;

			result.add(peerAddress(response, ofs));
		}

		return result;
	}

	// Protocol //

	private void performAction(int action) throws IOException {
		ByteBuffer response = ByteBuffer.allocate(MAX_RESPONSE_LENGTH);
		do {
			if (connectionExpired() && action != ACTION_CONNECT)
				performAction(ACTION_CONNECT);

			int timeout = 15 * timeStep;
			timeStep *= 2;

			if (timeout > MAX_TIMEOUT) {
				is_timeout = true;
				return;
			}

			transaction_id = random.nextInt();

			request.rewind();
			request.putLong(action == ACTION_CONNECT ? CONNECTION_ID
					: connection_id);
			request.putInt(action);
			request.putInt(transaction_id);

			if (action == ACTION_ANNOUNCE) {
				request_body.rewind();
				request.put(request_body);
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
				response.limit(in.getLength());
			} catch (SocketTimeoutException e) {
			}

		} while (!isValidResponse());

		timeStep = 1;

		if (action == ACTION_CONNECT) {
			expire_time = (long) (System.nanoTime() + 6e10);
			connection_id = response.getLong(8);
		}

		if (action == ACTION_ANNOUNCE) {
			this.response = response;
		}
	}

	private boolean connectionExpired() {
		return System.nanoTime() > expire_time;
	}

}
