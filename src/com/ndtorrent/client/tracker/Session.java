package com.ndtorrent.client.tracker;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.Collection;

import com.ndtorrent.client.ClientInfo;

// TODO a client can be directed to use UDP instead of HTTP in
// an old torrent where only the previous service are listed.
// Such an example is "http://tracker.openbittorrent.com:80/announce"
// See "DNS Tracker Preferences"
// http://bittorrent.org/beps/bep_0034.html

public abstract class Session {
	// Session and its subclasses are not thread safe.
	// Do not call any query method while Session.isUpdating() returns true.

	protected ClientInfo client_info;
	protected String info_hash;

	protected Session(ClientInfo client_info, String info_hash) {
		this.client_info = client_info;
		this.info_hash = info_hash;
	}

	public static final Session create(String url, ClientInfo client_info,
			String info_hash) {
		if (url != null)
			if (url.startsWith("udp"))
				return new UdpSession(url, client_info, info_hash);
			else if (url.startsWith("http"))
				return new HttpSession(url, client_info, info_hash);

		return new NullSession(url, client_info, info_hash);
	}

	public abstract String getUrl();

	public abstract void update(Event event, long uploaded, long downloaded,
			long left);

	public abstract Event lastEvent();

	public abstract long updatedAt();

	public final boolean isUpdateError() {
		if (isUpdating())
			return false;
		if (isConnectionTimeout())
			return true;
		if (isConnectionError())
			return true;
		if (isTrackerError())
			return true;
		return false;
	}

	public abstract boolean isConnectionError();

	public abstract boolean isConnectionTimeout();

	public abstract boolean isUpdating();

	public abstract boolean isValidResponse();

	public abstract boolean isTrackerError();

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

}
