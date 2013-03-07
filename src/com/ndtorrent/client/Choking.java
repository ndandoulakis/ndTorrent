package com.ndtorrent.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

public final class Choking {

	public static void updateAsLeech(List<PeerChannel> channels) {
		rollBlocksTotal(channels);
		regularLeechUpdate(channels);
		optimisticUpdate(channels);
		// TODO ANTI-SNUBBING
	}

	public static void updateAsSeed(List<PeerChannel> channels) {
		// As a seed, rolling totals and ANTI-SNUBBING aren't needed.
		regularSeedUpdate(channels);
		// optimisticUpdate(channels);
	}

	private static void rollBlocksTotal(List<PeerChannel> channels) {
		long now = System.nanoTime();
		for (PeerChannel channel : channels) {
			channel.rollBlocksTotal(now);
		}
	}

	private static void regularLeechUpdate(List<PeerChannel> channels) {
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
			if (channel.isOptimistic()) {
				// Channel is optimistic and not current (expired).
				// Ignore it, we don't mess here with optimistic unchoking.
				continue;
			}
			boolean full_slots = (optimistic + regular + slots) >= MAX_SLOTS;
			boolean interested = channel.isInterested();
			boolean am_choked = channel.amChoked();
			boolean former_optimistic = channel.isFormerOptimistic();
			// Candidates that are choking the client are forced to pass
			// through an optimistic slot at least once. This constraint,
			// and ANTI-SNUBBING, can increase the optimistic slots.
			boolean promote = !am_choked || former_optimistic;
			boolean snubbed = channel.amSnubbed();
			if (full_slots || !interested || !promote || snubbed) {
				channel.updateIsChoked(true);
				continue;
			}
			slots++;
			channel.updateIsChoked(false);
			channel.setUnchokeEndTime((long) (now + 10 * 1e9));
		}

	}

	private static void regularSeedUpdate(List<PeerChannel> channels) {
		// For testing, choke only not interested peers.
		for (PeerChannel channel : channels) {
			boolean choke = channel.isInterested() == false;
			channel.updateIsChoked(choke);
		}
	}

	private static void optimisticUpdate(List<PeerChannel> channels) {
		List<PeerChannel> candidates = new ArrayList<PeerChannel>(channels);

		int optimistic = removeCurrentOptimistic(candidates);
		int regular = removeCurrentRegular(candidates);

		if (optimistic + regular >= 4)
			return;

		for (PeerChannel channel : candidates) {
			if (channel.isOptimistic()) {
				// Channel is optimistic and not current (expired).
				// Don't choke here to avoid CHOKE / UNCHOKE messages,
				// assuming the regular choking won't choke the channel.
				channel.setIsOptimistic(false);
				channel.setFormerOptimistic(true);
			}
		}

		// At this point, the unchoked channels have been expired.
		removeUnchokedOrFormerOptimistic(candidates);

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

	private static int removeUnchokedOrFormerOptimistic(
			List<PeerChannel> channels) {

		// Returns the number of channels removed.
		Iterator<PeerChannel> it = channels.iterator();
		int count = 0;
		while (it.hasNext()) {
			PeerChannel channel = it.next();
			if (!channel.isChoked() || channel.isFormerOptimistic()) {
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
