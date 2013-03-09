package com.ndtorrent.gui;

import java.util.List;

import javax.swing.table.AbstractTableModel;

import com.ndtorrent.client.status.ConnectionInfo;

public final class ConnectionsModel extends AbstractTableModel {

	private static final long serialVersionUID = 1L;

	private String[] column_names = { "IP", "Client", "Flags", "Dn Speed",
			"Up Speed", "Downloaded", "Uploaded" };

	private List<ConnectionInfo> connections;

	public void setConnections(List<ConnectionInfo> connections) {
		this.connections = connections;
		fireTableDataChanged();
	}

	@Override
	public int getRowCount() {
		return connections == null ? 0 : connections.size();
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
		ConnectionInfo info = connections.get(rowIndex);
		switch (columnIndex) {
		case 0:
			return info.getIP();
		case 1:
			return info.getID();
		case 2:
			return getFlagsValue(info);
		case 3:
			return String.format("%.1f kB/s", info.getInputRate() / 1000);
		default:
			return null;
		}
	}

	private String getFlagsValue(ConnectionInfo info) {
		StringBuilder builder = new StringBuilder();
		if (info.amInterested() && !info.amChoked())
			builder.append('D');
		if (info.amInterested() && info.amChoked())
			builder.append('d');
		if (info.isInterested() && !info.isChoked())
			builder.append('U');
		if (info.isInterested() && info.isChoked())
			builder.append('u');
		if (info.isOptimistic())
			builder.append('O');
		if (info.amSnubbed())
			builder.append('S');
		if (info.isInitiator())
			builder.append('I');
		if (!info.amChoked() && !info.amInterested())
			builder.append('K');
		if (!info.isChoked() && !info.isInterested())
			builder.append('?');
		return builder.toString();
	}

}
