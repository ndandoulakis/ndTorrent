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
		if (value >= 0)
			return String.format("%ds", value);
		else
			return null;
	}

}
