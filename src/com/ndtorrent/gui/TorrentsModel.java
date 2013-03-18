package com.ndtorrent.gui;

import javax.swing.table.AbstractTableModel;

import com.ndtorrent.client.status.TorrentInfo;

public class TorrentsModel extends AbstractTableModel {

	private static final long serialVersionUID = 1L;

	private String[] column_names = { "Name", "Size", "Remaining", "Status",
			"Progress", "Dn Speed", "Up Speed" };

	private TorrentInfo torrent;

	public void setTorrent(TorrentInfo torrent) {
		this.torrent = torrent;
		fireTableDataChanged();
	}

	@Override
	public int getRowCount() {
		return torrent == null ? 0 : 1;
	}

	@Override
	public Class<?> getColumnClass(int columnIndex) {
		return columnIndex != 3 ? Object.class : BarRenderer.class;
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
		TorrentInfo info = torrent;
		switch (columnIndex) {
		case 0:
			return info.getName();
		case 1:
			return String.format("%,.1f KB", info.getTotalLength() / 1000.0);
		case 2:
			return String
					.format("%,.1f KB", info.getRemainingLength() / 1000.0);
		case 3:
			return info;
		case 4:
			return getProgressValue(info);
		case 5:
			return String.format("%.1f KB/s", info.getInputRate() / 1000.0);
		case 6:
			return String.format("%.1f KB/s", info.getOutputRate() / 1000.0);
		default:
			return null;
		}
	}

	private String getProgressValue(TorrentInfo info) {
		int num_available = info.getAvailablePieces().cardinality();
		int num_pieces = info.numPieces();
		return String.format("%.1f%%", (100.0 * num_available) / num_pieces);
	}

}
