package com.ndtorrent.client.status;

import java.util.BitSet;

import com.ndtorrent.client.Torrent;

public final class TorrentInfo {

	private final BitSet available;
	private final BitSet missing;
	private final int num_pieces;
	private final String name;
	private final long remaining_length;
	private final long total_length;
	private final long completion_time;
	private final double input_rate;
	private final double output_rate;

	public TorrentInfo(Torrent torrent, BitSet missing, long completion_time,
			double input_rate, double output_rate) {

		available = torrent.getAvailablePieces();
		this.missing = missing;
		num_pieces = torrent.numPieces();
		name = torrent.getName();
		remaining_length = torrent.getRemainingLength();
		total_length = torrent.getTotalLength();
		this.completion_time = completion_time;
		this.input_rate = input_rate;
		this.output_rate = output_rate;

	}

	public BitSet getAvailablePieces() {
		return available;
	}

	public BitSet getMissingPieces() {
		return missing;
	}

	public int numPieces() {
		return num_pieces;
	}

	public String getName() {
		return name;
	}

	public long getRemainingLength() {
		return remaining_length;
	}

	public long getTotalLength() {
		return total_length;
	}

	public long getCompletionTime() {
		return completion_time;
	}

	public double getInputRate() {
		return input_rate;
	}

	public double getOutputRate() {
		return output_rate;
	}

}
