package com.ndtorrent.client.tracker;

import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.Collections;

import com.ndtorrent.client.ClientInfo;

public final class NullSession extends Session {

	private String url;

	protected NullSession(String url, ClientInfo client_info, String info_hash) {
		super(client_info, info_hash);
		this.url = url;
	}

	@Override
	public String getUrl() {
		return url;
	}

	@Override
	public void update(Event event, long uploaded, long downloaded, long left) {
	}

	@Override
	public boolean isUpdating() {
		return false;
	}

	@Override
	public boolean isValidResponse() {
		return false;
	}

	@Override
	public boolean isTrackerError() {
		return false;
	}

	@Override
	public int getInterval() {
		return 0;
	}

	@Override
	public int getLeechers() {
		return 0;
	}

	@Override
	public int getSeeders() {
		return 0;
	}

	@Override
	public Collection<InetSocketAddress> getPeers() {
		return Collections.emptyList();
	}

	@Override
	public boolean isConnectionError() {
		return false;
	}

	@Override
	public boolean isConnectionTimeout() {
		return false;
	}

	@Override
	public long updatedAt() {
		return 0;
	}

	@Override
	public Event lastEvent() {
		return null;
	}

}
