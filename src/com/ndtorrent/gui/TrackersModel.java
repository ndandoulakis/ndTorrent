package com.ndtorrent.gui;

import java.util.List;

import javax.swing.table.AbstractTableModel;

import com.ndtorrent.client.status.TrackerInfo;

public class TrackersModel extends AbstractTableModel {

	private static final long serialVersionUID = 1L;

	private String[] column_names = { "Name", "Status", "Update In", "Seeds",
			"Peers" };

	private List<TrackerInfo> trackers;

	public void setTrackers(List<TrackerInfo> trackers) {
		this.trackers = trackers;
		fireTableDataChanged();
	}

	@Override
	public int getRowCount() {
		return trackers == null ? 0 : trackers.size();
	}

	@Override
	public int getColumnCount() {
		return column_names.length;
	}

	@Override
	public String getColumnName(int column) {
		return column_names[column];
	}

	@Override
	public Object getValueAt(int rowIndex, int columnIndex) {
		TrackerInfo info = trackers.get(rowIndex);
		switch (columnIndex) {
		case 0:
			return info.getUrl();
		case 1:
			return statusValue(info);
		case 2:
			return getIntervalValue(info);
		case 3:
			return info.getSeeders();
		case 4:
			return info.getLeechers();
		default:
			return null;
		}
	}

	private String statusValue(TrackerInfo info) {
		if (info.isUpdating())
			return "updating...";
		else if (info.isConnectionError())
			return "connection error";
		else if (info.isConnectionTimeout())
			return "timed out";
		else if (info.isTrackerError())
			return "tracker error";
		else
			return new String("OK");
	}

	private String getIntervalValue(TrackerInfo info) {
		long now = System.nanoTime();
		long interval = now - info.getUpdatedAt();
		int value = info.getInterval() - (int) (interval / 1e9);
		return (value < 0 ? 0 : value) + " secs";
	}
}
