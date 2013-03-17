package com.ndtorrent.gui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;

import javax.swing.JComponent;

import com.ndtorrent.client.RollingTotal;

public class Statistics extends JComponent {

	private static final long serialVersionUID = 1L;

	// TODO use a timer to repaint

	private Color background = new Color(0xE3EADA);
	private Color color1 = new Color(0x3C6304);

	private RollingTotal total = new RollingTotal(5 * 60);

	public Statistics() {
		setPreferredSize(new Dimension(0, 80));
	}

	public void addInputRate(double rate) {
		long now = System.nanoTime();
		total.roll(now);
		total.add(rate);

		repaint();
	}

	@Override
	public void paintComponent(Graphics g) {
		Dimension size = getSize();
		int width = size.width;
		int height = size.height;

		g.setColor(background);
		g.fillRect(0, 0, width, height);

		double[] rates = total.array();
		double max_rate = 0.001;
		for (double r : rates) {
			if (r > max_rate)
				max_rate = r;
		}

		g.setColor(color1);
		int slots = rates.length;
		for (int i = 0; i < slots - 1; i++) {
			int x1 = width - (i * width) / slots;
			int x2 = width - ((i + 1) * width) / slots;
			int y1 = (int) (height - (0.5 + (rates[i] * height) / max_rate));
			int y2 = (int) (height - (0.5 + (rates[i + 1] * height) / max_rate));
			g.drawLine(x1, y1, x2, y2);
		}

		g.drawString(String.format("%,.1f", max_rate / 1000), 2, 12);
	}
}
