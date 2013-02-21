package com.ndtorrent.client.tracker;

public enum Event {
	REGULAR, COMPLETED, STARTED, STOPPED;

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
