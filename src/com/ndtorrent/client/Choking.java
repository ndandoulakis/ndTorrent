package com.ndtorrent.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

public final class Choking {

	public static void update(List<PeerChannel> channels) {
		updateRollingTotals(channels);
		updateRegular(channels);
		updateOptimistic(channels);
		// TODO ANTI-SNUBBING
	}

	private static void updateRollingTotals(List<PeerChannel> channels) {
		long now = System.nanoTime();
		for (PeerChannel channel : channels) {
			channel.rollBlocksTotal(now);
		}
	}

	private static void updateRegular(List<PeerChannel> channels) {
		List<PeerChannel> candidates = new ArrayList<PeerChannel>(channels);

		int optimistic = removeCurrentOptimistic(candidates);
		int regular = removeCurrentRegular(candidates);

		if (optimistic + regular >= 4)
			return;

		sortByBlocksTotal(candidates);

		final int MAX_SLOTS = 3 + Math.min(optimistic, 1);
		long now = System.nanoTime();
		int slots = 0;
		for (PeerChannel channel : candidates) {
			boolean full_slots = (optimistic + regular + slots) >= MAX_SLOTS;
			boolean interested = channel.isInterested();
			boolean former_optimistic = channel.isFormerOptimistic();
			if (full_slots || !interested || !former_optimistic) {
				channel.updateIsChoked(true);
				continue;
			}
			slots++;
			channel.updateIsChoked(false);
			channel.setUnchokeEndTime((long) (now + 10 * 1e9));
		}

	}

	private static void updateOptimistic(List<PeerChannel> channels) {
		List<PeerChannel> candidates = new ArrayList<PeerChannel>(channels);

		int optimistic = removeCurrentOptimistic(candidates);
		int regular = removeCurrentRegular(candidates);

		if (optimistic + regular >= 4)
			return;

		for (PeerChannel channel : candidates) {
			if (channel.isOptimistic()) {
				// Channel is optimistic but not current (expired).
				// Don't choke here to avoid CHOKE / UNCHOKE messages,
				// assuming the regular choking won't choke the channel.
				channel.setIsOptimistic(false);
				channel.setFormerOptimistic(true);
			}
		}

		removeFormerOptimistic(candidates);

		if (candidates.isEmpty()) {
			for (PeerChannel channel : channels) {
				if (channel.isChoked())
					channel.setFormerOptimistic(false);
			}
			return;
		}

		// Optimistic takes a single slot a time, if any.
		// Only regular attempts to take multiple slots at once.
		for (PeerChannel channel : candidates) {
			if (!channel.isInterested())
				continue;

			channel.setAmSnubbed(false);
			channel.setIsOptimistic(true);
			channel.updateIsChoked(false);

			long now = System.nanoTime();
			channel.setUnchokeEndTime((long) (now + 30 * 1e9));
			break;
		}

	}

	private static int removeFormerOptimistic(List<PeerChannel> channels) {
		// Returns the number of channels removed.
		Iterator<PeerChannel> it = channels.iterator();
		int count = 0;
		while (it.hasNext()) {
			PeerChannel channel = it.next();
			if (channel.isFormerOptimistic()) {
				it.remove();
				count++;
			}
		}
		return count;
	}

	private static int removeCurrentOptimistic(List<PeerChannel> channels) {
		// Returns the number of channels removed.
		Iterator<PeerChannel> it = channels.iterator();
		int count = 0;
		while (it.hasNext()) {
			PeerChannel channel = it.next();
			if (isCurrentOptimistic(channel)) {
				it.remove();
				count++;
			}
		}
		return count;
	}

	private static int removeCurrentRegular(List<PeerChannel> channels) {
		// Returns the number of channels removed.
		Iterator<PeerChannel> it = channels.iterator();
		int count = 0;
		while (it.hasNext()) {
			PeerChannel channel = it.next();
			if (isCurrentRegular(channel)) {
				it.remove();
				count++;
			}
		}
		return count;
	}

	private static boolean isCurrentOptimistic(PeerChannel channel) {
		return isCurrent(channel) && channel.isOptimistic();
	}

	private static boolean isCurrentRegular(PeerChannel channel) {
		// A channel can become regular if it has been optimistic
		// unchoked at least once.
		boolean current = isCurrent(channel);
		boolean snubbed = channel.amSnubbed();
		boolean optimistic = channel.isOptimistic();
		return current && !optimistic && !snubbed;
	}

	private static boolean isCurrent(PeerChannel channel) {
		long now = System.nanoTime();
		boolean choked = channel.isChoked();
		boolean interested = channel.isInterested();
		boolean expired = now > channel.getUnchokeEndTime();
		return !choked && interested && !expired;

	}

	private static void sortByBlocksTotal(List<PeerChannel> channels) {
		Collections.sort(channels, new Comparator<PeerChannel>() {
			@Override
			public int compare(PeerChannel c1, PeerChannel c2) {
				// descending order, c2 > c1
				return Long.signum(c2.getBlocksTotal() - c1.getBlocksTotal());
			}
		});
	}

}
