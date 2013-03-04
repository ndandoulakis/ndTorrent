package com.ndtorrent.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

public final class RegularUnchoking {

	public static void unchoke(ArrayList<PeerChannel> channels) {
		// Note that the array might be modified.

		int optimistic = 0; // removeRegular(channels, optimistic);
		int regular = removeRegular(channels);
		sortByBlocksTotal(channels);

		final int MAX_SLOTS = 3 + Math.min(optimistic, 1);
		long now = System.nanoTime();
		int slots = 0;
		for (PeerChannel channel : channels) {
			// boolean snubbed = channel.getBlocksTotal() == 0;
			boolean is_interested = channel.isInterested();
			boolean full_slots = (optimistic + regular + slots) >= MAX_SLOTS;
			if (full_slots || !is_interested) {
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
		long now = System.nanoTime();
		Iterator<PeerChannel> it = channels.iterator();
		int count = 0;
		while (it.hasNext()) {
			PeerChannel channel = it.next();
			boolean expired = now > channel.getUnchokeEndTime();
			if (!channel.isChoked() && channel.isInterested() && !expired) {
				it.remove();
				count++;
			}
		}
		return count;
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
