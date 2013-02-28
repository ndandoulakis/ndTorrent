package com.ndtorrent.client.tracker;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Scanner;
import java.util.SortedMap;
import java.util.TreeMap;

import com.ndtorrent.client.Bdecoder;
import com.ndtorrent.client.ClientInfo;

public final class HttpSession extends Session implements Runnable {

	// Implements the HTTP tracker protocol

	private Thread thread;

	private String tracker;
	private String tracker_id;

	private boolean is_connection_error;
	private boolean is_timeout;
	private long updated_at;
	private Event last_event;

	private String request;
	private volatile SortedMap<String, Object> response = new TreeMap<String, Object>();

	protected HttpSession(String url, ClientInfo client_info, String info_hash) {
		super(client_info, info_hash);
		tracker = url;
	}

	@Override
	public String getUrl() {
		return tracker;
	}

	@Override
	public long updatedAt() {
		return updated_at;
	}

	@Override
	public boolean isConnectionError() {
		return is_connection_error;
	}

	@Override
	public boolean isConnectionTimeout() {
		return is_timeout;
	}

	@Override
	public Event lastEvent() {
		return last_event;
	}

	@Override
	public void update(Event event, long uploaded, long downloaded, long left) {
		if (isUpdating())
			return;

		last_event = event;
		is_connection_error = false;
		is_timeout = false;

		// Prepare Request
		String escaped_id = getUrlEncodedString(client_info.getID());
		String escaped_hash = getUrlEncodedString(info_hash);

		request = String
				.format("?peer_id=%s&port=%d&info_hash=%s&event=%s&uploaded=%d&downloaded=%d&left=%d&no_peer_id=1&compact=1",
						escaped_id, client_info.getPort(), escaped_hash, event,
						uploaded, downloaded, left);

		if (tracker_id != null) {
			request += "&trackerid=" + getUrlEncodedString(tracker_id);
		}

		// Run
		thread = new Thread(this);
		thread.start();

	}

	private String getUrlEncodedString(String s) {
		// URLEncoder converts space character " " into a plus sign "+".
		// It seems that some trackers have problem with this encoding,
		// therefore "+" is replace with "%20".
		try {
			return URLEncoder.encode(s, "ISO-8859-1").replace("+", "%20");
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		return null;
	}

	@Override
	public boolean isUpdating() {
		return thread != null && thread.isAlive();
	}

	@SuppressWarnings("unchecked")
	@Override
	public void run() {
		response = new TreeMap<String, Object>();

		URLConnection connection = null;
		try {
			connection = new URL(tracker + request).openConnection();
			connection.setUseCaches(false);

			String ben_response;
			ben_response = new Scanner(connection.getInputStream())
					.useDelimiter("^").next();
			response = (SortedMap<String, Object>) Bdecoder
					.decode(ben_response);
			if (response.containsKey("tracker id"))
				tracker_id = (String) response.get("tracker id");

		} catch (SocketTimeoutException e) {
			is_timeout = true;
		} catch (IOException e) {
			// TODO save the connection error
			is_connection_error = true;
			e.printStackTrace();
		} finally {
			// On HTTP exception, i.e. error 400, the connection remains open.
			((HttpURLConnection) connection).disconnect();
			updated_at = System.nanoTime();
		}

	}

	@Override
	public boolean isValidResponse() {
		return response != null && !response.isEmpty();
	}

	@Override
	public boolean isTrackerError() {
		return response.containsKey("failure reason");
	}

	@Override
	public int getInterval() {
		return responseIntValue("interval");
	}

	@Override
	public int getLeechers() {
		return responseIntValue("incomplete");
	}

	@Override
	public int getSeeders() {
		return responseIntValue("complete");
	}

	@Override
	public Collection<InetSocketAddress> getPeers() {
		Object peers = response.get("peers");

		if (peers instanceof String)
			return parseCompactPeerList(peers);
		else if (peers instanceof Iterable<?>)
			return parsePeerDictionary(peers);

		return null;
	}

	private Collection<InetSocketAddress> parseCompactPeerList(Object peers) {
		ArrayList<InetSocketAddress> result = new ArrayList<InetSocketAddress>();

		try {
			ByteBuffer bb = ByteBuffer.wrap(((String) peers)
					.getBytes("ISO-8859-1"));

			for (int ofs = 0; ofs < bb.capacity(); ofs += 6) {
				result.add(peerAddress(bb, ofs));
			}

		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}

		return result;
	}

	@SuppressWarnings("unchecked")
	private Collection<InetSocketAddress> parsePeerDictionary(Object peers) {
		ArrayList<InetSocketAddress> result = new ArrayList<InetSocketAddress>();

		for (Object p : (Iterable<Object>) peers) {
			if (p instanceof SortedMap<?, ?>) {
				SortedMap<String, Object> d = (SortedMap<String, Object>) p;
				String ip = Bdecoder.utf8EncodedString(d.get("ip"));
				int port = ((Long) d.get("port")).intValue();

				result.add(new InetSocketAddress(ip, port));
			}
		}

		return result;
	}

	private int responseIntValue(String key) {
		Object v = response.get(key);
		return (v instanceof Long) ? ((Long) v).intValue() : 0;
	}

}
