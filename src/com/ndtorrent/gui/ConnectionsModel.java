package com.ndtorrent.gui;

import java.util.List;

import javax.swing.table.AbstractTableModel;

import com.ndtorrent.client.status.ConnectionInfo;

public final class ConnectionsModel extends AbstractTableModel {

	private static final long serialVersionUID = 1L;

	private String[] column_names = { "IP", "Dn Speed", "Up Speed",
			"Downloaded", "Uploaded" };

	private List<ConnectionInfo> connections;

	public ConnectionsModel(List<ConnectionInfo> connections) {
		this.connections = connections;
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
		// TODO Auto-generated method stub
		return null;
	}

}
