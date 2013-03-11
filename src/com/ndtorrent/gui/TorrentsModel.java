package com.ndtorrent.gui;

import javax.swing.table.AbstractTableModel;

import com.ndtorrent.client.status.TorrentInfo;

public class TorrentsModel extends AbstractTableModel {

	private static final long serialVersionUID = 1L;

	// Status: pieces availability
	// %: 0.1 (5/488)
	private String[] column_names = { "Name", "Size", "Status", "%",
			"Dn Speed", "Up Speed" };

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
		return columnIndex != 2 ? Object.class : BarRenderer.class;
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
		case 2:
			return info;

		default:
			return null;
		}
	}

}
