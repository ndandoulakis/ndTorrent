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

	private final Color color1 = new Color(0x87D12E); // ON
	private final Color color2 = new Color(0xE3EADA); // OFF
	private final Color background = new Color(0xFF7563);

	// background color, ON color, OFF color
	private BitSet bitmap;
	private BitSet mask; // which bits to draw
	private int nbits;

	public BarRenderer() {
		setPreferredSize(new Dimension(0, 50));
	}

	public void setBits(BitSet bitmap, BitSet mask, int nbits) {
		this.bitmap = bitmap;
		this.mask = mask;
		this.nbits = nbits;
	}

	@Override
	public void paintComponent(Graphics g) {
		if (bitmap == null)
			return;
		Dimension size = getSize();
		int width = size.width;
		int height = size.height;
		// TODO draw range of bits instead
		for (int i = 0; i < nbits; i++) {
			int x = (i * width) / nbits;
			int x_next = ((i + 1) * width) / nbits;
			if (mask != null && mask.get(i))
				g.setColor(background);
			else
				g.setColor(bitmap.get(i) ? color1 : color2);
			g.fillRect(x, 0, x_next - x, height);
		}
	}

	@Override
	public Component getTableCellRendererComponent(JTable table, Object value,
			boolean isSelected, boolean hasFocus, int row, int column) {

		if (value instanceof TorrentInfo) {
			TorrentInfo info = (TorrentInfo) value;
			setBits(info.getAvailablePieces(), info.getMissingPieces(),
					info.numPieces());
		} else if (value instanceof PieceInfo) {
			PieceInfo info = (PieceInfo) value;
			setBits(info.getAvailableBlocks(), null, info.numBlocks());
		}

		return this;
	}

}
