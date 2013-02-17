package com.ndtorrent.client.status;

import java.util.List;

public interface StatusObserver {

	// A Swing component could implement asyncMethods like this
	// { SwingUtilities.invokeLater(new Runnable() {set/draw status}); }

	void asyncConnections(final List<ConnectionInfo> connections, String info_hash);
	
	void asyncPieces(final List<PieceInfo> pieces, String info_hash);

	void asyncTorrentStatus(final TorrentInfo torrent, String info_hash);
}
