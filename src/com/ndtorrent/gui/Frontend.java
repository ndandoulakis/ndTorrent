package com.ndtorrent.gui;

import java.awt.EventQueue;

import javax.swing.JFrame;

import com.ndtorrent.client.Client;

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ScrollPaneConstants;
import javax.swing.JInternalFrame;

import java.awt.GridLayout;

import javax.swing.BoxLayout;

public class Frontend {

	private JFrame frmNdtorrentAlpha;

	private Client client = new Client();
	private JTable connectionsTable;
	private JInternalFrame internalFrame;

	/**
	 * Launch the application.
	 */
	public static void main(String[] args) {
		EventQueue.invokeLater(new Runnable() {
			public void run() {
				try {
					Frontend window = new Frontend();
					window.frmNdtorrentAlpha.setVisible(true);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}

	/**
	 * Create the application.
	 */
	public Frontend() {
		initialize();
	}

	/**
	 * Initialize the contents of the frame.
	 */
	private void initialize() {
		frmNdtorrentAlpha = new JFrame();
		frmNdtorrentAlpha.setTitle("ndTorrent - alpha version");
		frmNdtorrentAlpha.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				frmNdtorrentAlpha.setVisible(false);
				client.close();
			}
		});
		frmNdtorrentAlpha.setBounds(100, 100, 450, 300);
		frmNdtorrentAlpha.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frmNdtorrentAlpha.getContentPane()
				.setLayout(new GridLayout(0, 1, 0, 0));

		internalFrame = new JInternalFrame("Connections");
		internalFrame.setBorder(null);
		internalFrame.setFrameIcon(null);
		frmNdtorrentAlpha.getContentPane().add(internalFrame);
		internalFrame.getContentPane()
				.setLayout(
						new BoxLayout(internalFrame.getContentPane(),
								BoxLayout.X_AXIS));

		JScrollPane scrollPane = new JScrollPane();
		internalFrame.getContentPane().add(scrollPane);
		scrollPane
				.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);

		connectionsTable = new JTable();
		connectionsTable.setFillsViewportHeight(true);
		scrollPane.setViewportView(connectionsTable);
		internalFrame.setVisible(true);

		ConnectionsModel connections = new ConnectionsModel();
		connectionsTable.setModel(connections);

		client.setServerPort(Client.DEFAULT_PORT);
		String info_hash = client.addTorrent("test_big.torrent");
		client.addStatusObserver(connections, info_hash);

	}

}
