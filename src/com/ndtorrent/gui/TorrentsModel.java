package com.ndtorrent.gui;

import java.util.List;

import javax.swing.table.AbstractTableModel;

import com.ndtorrent.client.status.TorrentInfo;

public class TorrentsModel extends AbstractTableModel {

	private static final long serialVersionUID = 1L;

	// Status: pieces availability
	// %: 0.1 (5/488)
	private String[] column_names = { "Name", "Size", "Status", "%",
			"Dn Speed", "Up Speed" };

	private List<TorrentInfo> torrents;

	public void setTorrents(List<TorrentInfo> torrents) {
		this.torrents = torrents;
		fireTableDataChanged();
	}

	@Override
	public int getRowCount() {
		return torrents == null ? 0 : torrents.size();
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
		TorrentInfo info = torrents.get(rowIndex);
		switch (columnIndex) {

		default:
			return null;
		}
	}

}
