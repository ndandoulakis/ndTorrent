package com.ndtorrent.gui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.geom.Rectangle2D;

import javax.swing.JComponent;

import com.ndtorrent.client.RollingTotal;

public final class SpeedGraph extends JComponent {

	private static final long serialVersionUID = 1L;

	// TODO use a timer to repaint

	private Color bg_color = new Color(0xE3EADA);
	private Color color1 = new Color(0xFF8800);
	private Color color2 = new Color(0x1F8800);
	private Color color3 = new Color(0x8B8E87);

	private RollingTotal input_rate = new RollingTotal(5 * 60);
	private RollingTotal output_rate = new RollingTotal(5 * 60);

	public SpeedGraph() {
		setPreferredSize(new Dimension(0, 80));
	}

	public void addInputRate(double rate) {
		input_rate.roll();
		input_rate.add(rate);

		repaint();
	}

	public void addOutputRate(double rate) {
		output_rate.roll();
		output_rate.add(rate);

		repaint();
	}

	@Override
	public void paintComponent(Graphics g) {
		g.setColor(bg_color);
		g.fillRect(0, 0, getWidth(), getHeight());

		double max_rate = Math.max(maxRate(input_rate), maxRate(output_rate));

		g.setColor(color1);
		drawRate(g, output_rate, max_rate);

		g.setColor(color2);
		drawRate(g, input_rate, max_rate);

		drawString(g, String.format("%,.1f", max_rate / 1000), 2, 12);
	}

	private void drawString(Graphics g, String s, int x, int y) {
		FontMetrics fm = g.getFontMetrics();
		Rectangle2D rect = fm.getStringBounds(s, g);

		g.setColor(bg_color);
		g.fillRect(x, y - fm.getAscent(), (int) rect.getWidth(),
				(int) rect.getHeight());

		g.setColor(color3);
		g.drawString(s, x, y);
	}

	private void drawRate(Graphics g, RollingTotal rate, double max_rate) {
		double[] rates = rate.array();
		int slots = rates.length;
		int width = getWidth();
		int height = getHeight();
		for (int i = 0; i < slots - 1; i++) {
			int x1 = width - (i * width) / slots;
			int x2 = width - ((i + 1) * width) / slots;
			int y1 = (int) (height - (0.5 + (rates[i] * height) / max_rate));
			int y2 = (int) (height - (0.5 + (rates[i + 1] * height) / max_rate));
			g.drawLine(x1, y1, x2, y2);
		}
	}

	private double maxRate(RollingTotal rate) {
		double max = 0.001;
		for (double r : rate.array()) {
			if (r > max)
				max = r;
		}
		return max;
	}
}
