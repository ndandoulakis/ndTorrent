package com.ndtorrent.gui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.util.BitSet;

import javax.swing.JComponent;
import javax.swing.JTable;
import javax.swing.table.TableCellRenderer;

import com.ndtorrent.client.status.PieceInfo;
import com.ndtorrent.client.status.TorrentInfo;

public class BarRenderer extends JComponent implements TableCellRenderer {

	private static final long serialVersionUID = 1L;

	// background color, ON color, OFF color
	// BitSet mask; // bits to draw
	private BitSet bits;
	private int nbits;

	public BarRenderer() {
		setPreferredSize(new Dimension(0, 50));
	}

	public void setBits(BitSet bits, int nbits) {
		this.bits = bits;
		this.nbits = nbits;
	}

	@Override
	public void paintComponent(Graphics g) {
		if (bits == null)
			return;
		Dimension size = getSize();
		int width = size.width;
		int height = size.height;
		// TODO draw range of bits instead
		for (int i = 0; i < nbits; i++) {
			int x = (i * width) / nbits;
			int x_next = ((i + 1) * width) / nbits;
			g.setColor(bits.get(i) ? Color.BLUE : Color.RED);
			g.fillRect(x, 0, x_next - x, height);
		}
	}

	@Override
	public Component getTableCellRendererComponent(JTable table, Object value,
			boolean isSelected, boolean hasFocus, int row, int column) {

		if (value instanceof TorrentInfo) {
			TorrentInfo info = (TorrentInfo) value;
			setBits(info.getAvailablePieces(), info.numPieces());
		} else if (value instanceof PieceInfo) {
			PieceInfo info = (PieceInfo) value;
			setBits(info.getAvailableBlocks(), info.numBlocks());
		}

		return this;
	}

}
