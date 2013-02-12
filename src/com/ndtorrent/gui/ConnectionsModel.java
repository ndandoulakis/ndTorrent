package com.ndtorrent.gui;

import java.util.List;

import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;

import com.ndtorrent.client.status.ConnectionInfo;
import com.ndtorrent.client.status.StatusObserver;

public final class ConnectionsModel extends AbstractTableModel implements
		StatusObserver {

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
		case 1 :
			return info.getID();
		case 3:
			return String.format("%.2f kB/s", info.getInputRate() / 1000);
		default:
			return null;
		}
	}

	@Override
	public void asyncUpdate(final List<ConnectionInfo> status) {
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				setConnections(status);
			}
		});

	}

}
