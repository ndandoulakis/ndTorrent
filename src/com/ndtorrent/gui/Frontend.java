package com.ndtorrent.gui;

import java.awt.EventQueue;

import javax.swing.JFrame;

import com.ndtorrent.client.Client;
import com.ndtorrent.client.status.ConnectionInfo;
import com.ndtorrent.client.status.PieceInfo;
import com.ndtorrent.client.status.StatusObserver;
import com.ndtorrent.client.status.TorrentInfo;

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.SwingUtilities;

import java.util.List;

import javax.swing.BoxLayout;

public class Frontend implements StatusObserver {

	private JFrame frmNdtorrentAlpha;

	private Client client = new Client();
	private TableFrame trackersFrame;
	private TableFrame piecesFrame;
	private TableFrame connectionsFrame;
	private BarRenderer torrentBar;

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
		frmNdtorrentAlpha.getContentPane().setLayout(
				new BoxLayout(frmNdtorrentAlpha.getContentPane(),
						BoxLayout.Y_AXIS));

		torrentBar = new BarRenderer();
		frmNdtorrentAlpha.getContentPane().add(torrentBar);

		trackersFrame = new TableFrame("Trackers");
		frmNdtorrentAlpha.getContentPane().add(trackersFrame);

		piecesFrame = new TableFrame("Pieces");
		piecesFrame.setTableModel(new PiecesModel());
		frmNdtorrentAlpha.getContentPane().add(piecesFrame);

		connectionsFrame = new TableFrame("Connections");
		connectionsFrame.setTableModel(new ConnectionsModel());
		frmNdtorrentAlpha.getContentPane().add(connectionsFrame);

		client.setServerPort(Client.DEFAULT_PORT);

		String info_hash = client.addTorrent("test_big.torrent");
		client.addStatusObserver(this, info_hash);

	}

	@Override
	public void asyncConnections(final List<ConnectionInfo> connnections) {
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				((ConnectionsModel) connectionsFrame.getTableModel())
						.setConnections(connnections);
			}
		});
	}

	@Override
	public void asyncPieces(final List<PieceInfo> pieces) {
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				((PiecesModel) piecesFrame.getTableModel()).setPieces(pieces);
			}
		});
	}

	@Override
	public void asyncTorrentStatus(final TorrentInfo torrent) {
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				torrentBar.setBits(torrent.getBitfield(), torrent.numOfPieces());
			}
		});
	}

}
