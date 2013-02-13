package com.ndtorrent.gui;

import java.awt.EventQueue;

import javax.swing.JFrame;

import com.ndtorrent.client.Client;
import com.ndtorrent.client.status.ConnectionInfo;
import com.ndtorrent.client.status.PieceInfo;
import com.ndtorrent.client.status.StatusObserver;

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ScrollPaneConstants;
import javax.swing.JInternalFrame;
import javax.swing.SwingUtilities;

import java.awt.GridLayout;
import java.util.List;

import javax.swing.BoxLayout;
import java.awt.BorderLayout;
import java.awt.FlowLayout;

public class Frontend implements StatusObserver {

	private JFrame frmNdtorrentAlpha;

	private Client client = new Client();
	private JTable connectionsTable;
	private JInternalFrame connectionsFrame;
	private JInternalFrame piecesFrame;
	private JScrollPane scrollPane_1;
	private JTable piecesTable;
	private BarRenderer bar;

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
		frmNdtorrentAlpha.setBounds(100, 100, 402, 318);
		frmNdtorrentAlpha.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frmNdtorrentAlpha.getContentPane().setLayout(new BoxLayout(frmNdtorrentAlpha.getContentPane(), BoxLayout.Y_AXIS));
		
		bar = new BarRenderer();
		frmNdtorrentAlpha.getContentPane().add(bar);

		piecesFrame = new JInternalFrame("Pieces");
		piecesFrame.setBorder(null);
		piecesFrame.setFrameIcon(null);
		frmNdtorrentAlpha.getContentPane().add(piecesFrame);

		scrollPane_1 = new JScrollPane();
		scrollPane_1
				.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
		piecesFrame.getContentPane().add(scrollPane_1, BorderLayout.CENTER);

		piecesTable = new JTable();
		piecesTable.setShowGrid(false);
		piecesTable.setFillsViewportHeight(true);
		scrollPane_1.setViewportView(piecesTable);
		piecesFrame.setVisible(true);

		connectionsFrame = new JInternalFrame("Connections");
		connectionsFrame.setBorder(null);
		connectionsFrame.setFrameIcon(null);
		frmNdtorrentAlpha.getContentPane().add(connectionsFrame);
		connectionsFrame.getContentPane().setLayout(
				new BoxLayout(connectionsFrame.getContentPane(),
						BoxLayout.X_AXIS));

		JScrollPane scrollPane = new JScrollPane();
		connectionsFrame.getContentPane().add(scrollPane);
		scrollPane
				.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);

		connectionsTable = new JTable();
		connectionsTable.setShowGrid(false);
		connectionsTable.setFillsViewportHeight(true);
		scrollPane.setViewportView(connectionsTable);
		connectionsFrame.setVisible(true);

		// Associate tables with proper models.
		connectionsTable.setModel(new ConnectionsModel());
		piecesTable.setModel(new PiecesModel());

		client.setServerPort(Client.DEFAULT_PORT);
		String info_hash = client.addTorrent("test_big.torrent");
		client.addStatusObserver(this, info_hash);

	}

	@Override
	public void asyncConnections(final List<ConnectionInfo> connnections) {
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				((ConnectionsModel) connectionsTable.getModel())
						.setConnections(connnections);
			}
		});
	}

	@Override
	public void asyncPieces(final List<PieceInfo> pieces) {
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				((PiecesModel) piecesTable.getModel()).setPieces(pieces);
			}
		});
	}

}
