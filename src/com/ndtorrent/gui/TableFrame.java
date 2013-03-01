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
		scrollPane.setViewportView(table);

	}

	public void setTableModel(TableModel dataModel) {
		table.setModel(dataModel);
	}

	public TableModel getTableModel() {
		return table.getModel();
	}

	public static String displayDeltaTime(int delta_secs) {
		// TODO move to a DeltaTimeRender
		// TODO human readable String
		return (delta_secs < 0 ? 0 : delta_secs) + "s";
	}

}
