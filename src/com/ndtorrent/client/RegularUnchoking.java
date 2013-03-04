package com.ndtorrent.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

public final class RegularUnchoking {

	public static void unchoke(ArrayList<PeerChannel> channels) {
		// Note that the array might be modified.
		updateRollingTotals(channels);
		unchokeOptimistics(channels);
		unchokeRegular(channels);
		// TODO ANTI-SNUBBING
	}

	private static void updateRollingTotals(List<PeerChannel> channels) {
		long now = System.nanoTime();
		for (PeerChannel channel : channels) {
			channel.rollBlocksTotal(now);
		}
	}

	private static void unchokeOptimistics(List<PeerChannel> channels) {
		// clear if expired
		// if no free slots, return
		// if none optimistic candidate, clear optimistic flags
		// select choked & interested & optimistic
		// shuffle
		// unchoke 1..4 channels
		// flag optimistic candidates; clear snubbed flag
	}

	private static void unchokeRegular(List<PeerChannel> channels) {
		int optimistic = 0; // removeOptimistic(channels);
		int regular = removeRegular(channels);
		sortByBlocksTotal(channels);

		final int MAX_SLOTS = 3 + Math.min(optimistic, 1);
		long now = System.nanoTime();
		int slots = 0;
		for (PeerChannel channel : channels) {
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

	private static int removeRegular(List<PeerChannel> channels) {
		// Returns the number of channels removed.
		Iterator<PeerChannel> it = channels.iterator();
		int count = 0;
		while (it.hasNext()) {
			PeerChannel channel = it.next();
			if (isRegular(channel)) {
				it.remove();
				count++;
			}
		}
		return count;
	}

	private static boolean isRegular(PeerChannel channel) {
		long now = System.nanoTime();
		boolean choked = channel.isChoked();
		boolean interested = channel.isInterested();
		boolean snubbed = channel.amSnubbed();
		boolean expired = now > channel.getUnchokeEndTime();
		return !choked && interested && !snubbed && !expired;
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
