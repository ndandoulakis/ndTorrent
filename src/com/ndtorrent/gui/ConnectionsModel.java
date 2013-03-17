package com.ndtorrent.gui;

import java.util.List;

import javax.swing.table.AbstractTableModel;

import com.ndtorrent.client.status.ConnectionInfo;

public final class ConnectionsModel extends AbstractTableModel {

	private static final long serialVersionUID = 1L;

	private String[] column_names = { "IP", "Client", "Flags", "Dn Speed",
			"Up Speed", "Reqs", "Received", "Sent" };

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
			return getFormattedRate(info.getInputRate());
		case 4:
			return getFormattedRate(info.getOutputRate());
		case 5:
			return getFormattedRequests(info);
		case 6:
			return getFormattedTotal(info.getInputTotal());
		case 7:
			return getFormattedTotal(info.getOutputTotal());

		default:
			return null;
		}
	}

	private String getFormattedRate(double rate) {
		if (rate < 100)
			return null;
		else
			return String.format("%.1f KB/s", rate / 1000.0);
	}

	private String getFormattedTotal(double total) {
		if (total < 100)
			return null;
		else
			return String.format("%,.1f KB", total / 1000.0);
	}

	private String getFormattedRequests(ConnectionInfo info) {
		int reqs_in = info.numIncomingRequests();
		int reqs_out = info.numOutgoingRequests();
		if (reqs_in == 0 && reqs_out == 0)
			return null;
		else
			return String.format("%d | %d", reqs_out, reqs_in);
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
		if (info.isFormerOptimistic())
			builder.append('o');
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
