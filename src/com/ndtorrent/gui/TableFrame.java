package com.ndtorrent.gui;

import java.awt.BorderLayout;
import javax.swing.JInternalFrame;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ScrollPaneConstants;
import javax.swing.table.TableModel;

public class TableFrame extends JInternalFrame {

	private static final long serialVersionUID = 1L;
	private JTable table;

	/**
	 * Create the frame.
	 */
	public TableFrame() {
		this("TableFrame");
	}

	public TableFrame(String title) {
		setTitle(title);
		setFrameIcon(null);
		setBorder(null);
		setVisible(true);

		JScrollPane scrollPane = new JScrollPane();
		scrollPane
				.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
		getContentPane().add(scrollPane, BorderLayout.CENTER);

		table = new JTable();
		table.setShowGrid(false);
		table.setFillsViewportHeight(true);
		table.setDefaultRenderer(BarRenderer.class, new BarRenderer());
		table.setDefaultRenderer(IntervalRenderer.class, new IntervalRenderer());
		scrollPane.setViewportView(table);

	}

	public void setTableModel(TableModel dataModel) {
		table.setModel(dataModel);
	}

	public TableModel getTableModel() {
		return table.getModel();
	}

}
