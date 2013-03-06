package com.ndtorrent.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

public final class Choking {

	public static void update(ArrayList<PeerChannel> channels) {
		updateRollingTotals(channels);
		updateOptimistic(channels);
		updateRegular(channels);
		// TODO ANTI-SNUBBING
	}

	private static void updateRollingTotals(List<PeerChannel> channels) {
		long now = System.nanoTime();
		for (PeerChannel channel : channels) {
			channel.rollBlocksTotal(now);
		}
	}

	private static void updateOptimistic(List<PeerChannel> channels) {
		// set candidate=false if expired
		// select choked && interested && candidate
		// if no candidates, set all candidate=true
		// shuffle
		// unchoke 1..4 channels
		// flag optimistic candidates and clear snubbed flag
	}

	private static void updateRegular(List<PeerChannel> channels) {
		List<PeerChannel> candidates = new ArrayList<PeerChannel>(channels);

		int optimistic = removeCurrentOptimistic(candidates);
		int regular = removeCurrentRegular(candidates);
		sortByBlocksTotal(candidates);

		final int MAX_SLOTS = 3 + Math.min(optimistic, 1);
		long now = System.nanoTime();
		int slots = 0;
		for (PeerChannel channel : candidates) {
			boolean full_slots = (optimistic + regular + slots) >= MAX_SLOTS;
			if (full_slots || !channel.isInterested()) {
				channel.updateIsChoked(true);
				continue;
			}
			slots++;
			channel.updateIsChoked(false);
			channel.setUnchokeEndTime((long) (now + 10 * 1e9));
		}

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
		boolean optimistic = channel.isOptimistic()
				|| channel.isOptimisticCandidate();
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
