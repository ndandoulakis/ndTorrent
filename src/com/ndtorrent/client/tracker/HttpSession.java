package com.ndtorrent.client.tracker;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
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

public final class HttpSession extends Session {

	// Implements the HTTP tracker protocol

	private String tracker;
	private String tracker_id;
	private SortedMap<String, Object> response = new TreeMap<String, Object>();

	protected HttpSession(String url, ClientInfo client_info, String info_hash) {
		super(client_info, info_hash);
		tracker = url;
	}

	@SuppressWarnings("unchecked")
	@Override
	public void update(Event event, long uploaded, long downloaded, long left) {
		// TODO check if previous response is still running; new Thread

		URLConnection conn = null;

		try {
			String escapedId = URLEncoder.encode(client_info.getID(),
					"ISO-8859-1");

			String escapedHash = URLEncoder.encode(info_hash, "ISO-8859-1");

			String query = String
					.format("?peer_id=%s&port=%d&info_hash=%s&event=%s&uploaded=%d&downloaded=%d&left=%d&no_peer_id=1&compact=1",
							escapedId, client_info.getPort(), escapedHash,
							event, uploaded, downloaded, left);

			if (tracker_id != null) {
				query = query + "&trackerid="
						+ URLEncoder.encode(tracker_id, "ISO-8859-1");
			}

			conn = new URL(tracker + query).openConnection();
			conn.setUseCaches(false);

			String response = new Scanner(conn.getInputStream()).useDelimiter(
					"^").next();

			this.response = (SortedMap<String, Object>) Bdecoder
					.decode(response);

			if (this.response.containsKey("tracker id"))
				tracker_id = (String) this.response.get("tracker id");

		} catch (IOException e) {
			this.response = new TreeMap<String, Object>();
			e.printStackTrace();
		} finally {
			// On HTTP exception, i.e. error 400, the connection remains open.
			((HttpURLConnection) conn).disconnect();
		}
	}

	@Override
	public boolean isValidResponse() {
		return !response.isEmpty();
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
