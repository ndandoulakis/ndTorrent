package com.ndtorrent.client.tracker;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.Collection;

// TODO a client can be directed to use UDP instead of HTTP in
// an old torrent where only the previous service are listed.
// Such an example is "http://tracker.openbittorrent.com:80/announce"
// See "DNS Tracker Preferences"
// http://bittorrent.org/beps/bep_0034.html

public abstract class Session {

	public final Announce params = new Announce();

	public static final Session create(String url) {
		if (url != null)
			if (url.startsWith("udp"))
				return new UdpSession(url);
			else if (url.startsWith("http"))
				return new HttpSession(url);

		return null;
	}

	public abstract void update();

	public abstract boolean validResponse();

	public abstract boolean trackerError();

	public abstract int getInterval();

	public abstract int getLeechers();

	public abstract int getSeeders();

	public abstract Collection<InetSocketAddress> getPeers();

	final InetSocketAddress peerAddress(ByteBuffer bb, int ofs) {
		if (ofs < 0 || ofs >= bb.capacity())
			return null;

		byte[] ip = ByteBuffer.allocate(4).putInt(bb.getInt(ofs)).array();
		int port = bb.getShort(ofs + 4) & 0xFFFF;

		try {
			return new InetSocketAddress(InetAddress.getByAddress(ip), port);
		} catch (UnknownHostException e) {
			e.printStackTrace();
			return null;
		}
	}

	public class Announce {
		public String info_hash;
		public String client_id;
		public long downloaded;
		public long left;
		public long uploaded;
		public Event event;
		public int client_ip;
		public int key;
		public int num_want = -1;
		public int client_port;
	}

	public enum Event {
		NONE, COMPLETED, STARTED, STOPPED;

		public Integer toInteger() {
			switch (this) {
			case COMPLETED:
				return 1;
			case STARTED:
				return 2;
			case STOPPED:
				return 3;
			default:
				return 0;
			}
		}

		public String toString() {
			switch (this) {
			case COMPLETED:
				return "completed";
			case STARTED:
				return "started";
			case STOPPED:
				return "stopped";
			default:
				return "";
			}
		}
	}

}
