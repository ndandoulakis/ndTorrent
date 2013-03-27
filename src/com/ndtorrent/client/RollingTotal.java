package com.ndtorrent.client;

public final class RollingTotal {

	private double[] buckets;
	private double total;

	public RollingTotal(int nbuckets) {
		if (nbuckets < 1)
			throw new IllegalArgumentException(
					"nbuckets must be greater than zero");

		buckets = new double[nbuckets];
	}

	public void add(double amount) {
		buckets[0] += amount;
		total += amount;
	}

	public void roll() {
		// Roll after you have queried the total.

		total -= buckets[buckets.length - 1];

		for (int i = buckets.length - 1; i >= 1; i--) {
			buckets[i] = buckets[i - 1];
		}

		buckets[0] = 0;
	}

	public double total() {
		return total;
	}

	public double average() {
		return total / buckets.length;
	}

	public double[] array() {
		return buckets;
	}

}
