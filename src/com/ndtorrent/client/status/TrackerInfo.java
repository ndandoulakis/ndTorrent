package com.ndtorrent.client.status;

import com.ndtorrent.client.tracker.Session;

public final class TrackerInfo {

	private final String url;
	private final int seeders;
	private final int leechers;
	private final int interval;

	public TrackerInfo(Session session) {
		url = session.getUrl();
		seeders = session.getSeeders();
		leechers = session.getLeechers();
		interval = session.getInterval();
	}

	public String getUrl() {
		return url;
	}

	public int getSeeders() {
		return seeders;
	}

	public int getLeechers() {
		return leechers;
	}

	public int getInterval() {
		return interval;
	}

}
