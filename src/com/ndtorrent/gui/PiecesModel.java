package com.ndtorrent.gui;

import java.util.List;

import javax.swing.table.AbstractTableModel;

import com.ndtorrent.client.status.PieceInfo;

public final class PiecesModel extends AbstractTableModel {

	private static final long serialVersionUID = 1L;

	private String[] column_names = { "#", "Size", "Blocks", "Completed", "" };

	private List<PieceInfo> pieces;

	public void setPieces(List<PieceInfo> pieces) {
		this.pieces = pieces;
		fireTableDataChanged();
	}

	@Override
	public int getRowCount() {
		return pieces == null ? 0 : pieces.size();
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
		PieceInfo info = pieces.get(rowIndex);
		switch (columnIndex) {
		case 0:
			return info.getIndex();
		case 1:
			return String.format("%,.1f kB", info.getLength() / 1024.0);
		case 2:
			return info;
		case 3:
			return String.format("%d/%d", info.numAvailableBlocks(),
					info.numBlocks());
		case 4:
			return null;
		default:
			return null;
		}
	}

}
