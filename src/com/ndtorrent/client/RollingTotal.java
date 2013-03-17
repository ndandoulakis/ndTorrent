package com.ndtorrent.client;

public final class RollingTotal {
	private static final long SECOND = (long) 1e9;

	private long last_sec = System.nanoTime() / SECOND;

	private double[] buckets;

	// The object needs BUCKETS length seconds of lifetime in order to
	// reflect an accurate rolling total.
	private double total;

	public RollingTotal(int nbuckets) {
		// A bucket holds an amount for a certain second.
		buckets = new double[nbuckets];
	}

	public void add(double amount) {
		// To keep the rolling total in sync with the clock,
		// always call roll() before adding a new amount.

		buckets[0] += amount;
		total += amount;
	}

	public void roll(long current_time) {
		long sec = current_time / SECOND;
		int interval = (int) Math.min(sec - last_sec, buckets.length);
		if (interval > 0) {
			for (int i = buckets.length - 1; i >= interval; i--) {
				buckets[i] = buckets[i - interval];
			}
			for (int i = 0; i < interval; i++) {
				buckets[i] = 0;
			}
			total = 0;
			for (double a : buckets) {
				total += a;
			}
		}

		last_sec = sec;
	}

	public double getTotal() {
		return total;
	}

	public double[] array() {
		return buckets;
	}

}
