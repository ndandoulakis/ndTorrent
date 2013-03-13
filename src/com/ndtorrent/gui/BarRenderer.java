package com.ndtorrent.gui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
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

	private BufferedImage image;

	public BarRenderer() {
		setPreferredSize(new Dimension(0, 50));
	}

	public void setBits(BitSet bitmap, BitSet mask, int nbits) {
		if (image == null || image.getWidth() != nbits) {
			image = new BufferedImage(nbits, 1, BufferedImage.TYPE_INT_RGB);
		}

		// TODO draw range of bits instead
		for (int x = 0; x < nbits; x++) {
			Color c;
			if (mask != null && mask.get(x))
				c = background;
			else
				c = bitmap.get(x) ? color1 : color2;
			image.setRGB(x, 0, c.getRGB());
		}
	}

	@Override
	public void paintComponent(Graphics g) {
		if (image == null)
			return;

		Dimension size = getSize();
		int width = size.width;
		int height = size.height;

		if (width < image.getWidth()) {
			Graphics2D g2 = (Graphics2D) g;
			g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
					RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		}

		g.drawImage(image, 0, 0, width, height, null);
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
