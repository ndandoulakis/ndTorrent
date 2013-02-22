package com.ndtorrent.client.status;

import com.ndtorrent.client.tracker.Session;

public final class TrackerInfo {

	private final String url;
	private final int seeders;
	private final int leechers;
	private final int interval;
	private final long updated_at;
	private final boolean is_updating;
	private final boolean is_error;
	private final boolean is_timeout;
	private final boolean is_tracker_error;

	public TrackerInfo(Session session) {
		url = session.getUrl();
		is_updating = session.isUpdating();
		updated_at = is_updating ? 0 : session.updatedAt();
		seeders = is_updating ? 0 : session.getSeeders();
		leechers = is_updating ? 0 : session.getLeechers();
		interval = is_updating ? 0 : session.getInterval();
		is_timeout = is_updating ? false : session.isConnectionTimeout();
		is_error = is_updating ? false : session.isConnectionError();
		is_tracker_error = is_updating ? false : session.isTrackerError();
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
	
	public long getUpdatedAt() {
		return updated_at;
	}
	
	public boolean isUpdating() {
		return is_updating;
	}

	public boolean isConnectionError() {
		return is_error;
	}

	public boolean isConnectionTimeout() {
		return is_timeout;
	}

	public boolean isTrackerError() {
		return is_tracker_error;
	}

}
