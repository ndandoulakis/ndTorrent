package com.ndtorrent.client;

public final class TransferRate {
	private static final double SECOND = 1e9;

	private long last_time = System.nanoTime();

	private double rate;

	public void update(double amount) {
		// Frequent updating makes the rate smoother.

		long now = System.nanoTime();
		long interval = now - last_time;

		if (interval < SECOND)
			rate = amount + rate * (SECOND - interval) / SECOND;
		else
			rate = amount * SECOND / interval;

		last_time = now;
	}

	public double perSec() {
		update(0);
		return rate;
	}

}
