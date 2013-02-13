package com.ndtorrent.gui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;

import javax.swing.JComponent;
import javax.swing.JTable;
import javax.swing.table.TableCellRenderer;

public class BarRenderer extends JComponent implements TableCellRenderer {

	private static final long serialVersionUID = 1L;
	
	public BarRenderer() {
		setPreferredSize(new Dimension(0, 50));
	}

	@Override
	public void paintComponent(Graphics g) {
		Dimension size = getSize();
		g.setColor(Color.RED);
		// g.fillRect(0, 0, 100, 20);
		g.drawRect(0, 0, size.width - 1, size.height - 1);
	}

	@Override
	public Component getTableCellRendererComponent(JTable table, Object value,
			boolean isSelected, boolean hasFocus, int row, int column) {
		// TODO Auto-generated method stub
		return this;
	}

}
