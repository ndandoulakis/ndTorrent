package com.ndtorrent.gui;

import javax.swing.table.DefaultTableCellRenderer;

public class IntervalRenderer extends DefaultTableCellRenderer {

	private static final long serialVersionUID = 1L;

	public void setValue(Object value) {
		if (value instanceof Long) {
			setText(getFormattedInterval((Long) value));
			return;
		}

		setText("");
	}

	private String getFormattedInterval(Long interval) {
		long value = interval.longValue();
		if (value < 0)
			return null;

		// w d h m s
		long w = value / (7 * 24 * 60 * 60);
		value %= (7 * 24 * 60 * 60);
		long d = value / (24 * 60 * 60);
		value %= (24 * 60 * 60);
		long h = value / (60 * 60);
		value %= (60 * 60);
		long m = value / (60);
		long s = value % (60);

		StringBuilder builder = new StringBuilder();
		builder.append(w);
		builder.append('w');
		builder.append(' ');
		builder.append(d);
		builder.append('d');
		builder.append(' ');
		builder.append(h);
		builder.append('h');
		builder.append(' ');
		builder.append(m);
		builder.append('m');
		builder.append(' ');
		builder.append(s);
		builder.append('s');

		// Examples
		// 0w 0d 2h 0m 0s -> 2h 0m 0s
		// 0s -> 0s
		while (builder.length() > 2 && builder.charAt(0) == '0') {
			builder.delete(0, 3);
		}

		return builder.toString();
	}
}
