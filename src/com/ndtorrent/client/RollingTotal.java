package com.ndtorrent.client;

public final class RollingTotal {
	// A rolling total longer than the choking round can make the
	// rating a bit more accurate due to data transmission delays.
	private static final int NBUCKETS = 15;
	private static final long SECOND = (long) 1e9;

	private long last_sec = System.nanoTime() / SECOND;

	// A bucket holds an amount for a certain second.
	private long[] buckets = new long[NBUCKETS];

	// The object needs NBUCKETS seconds of lifetime in order to
	// reflect an accurate rolling total.
	private long total;

	public void add(long amount) {
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
			for (long a : buckets) {
				total += a;
			}
		}

		last_sec = sec;
	}

	public long getTotal() {
		return total;
	}

}
