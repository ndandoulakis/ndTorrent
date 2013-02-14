package com.ndtorrent.client.status;

import java.util.List;

public interface StatusObserver {

	// A Swing component could implement asyncMethods like this
	// { SwingUtilities.invokeLater(new Runnable() {set/draw status}); }

	void asyncConnections(final List<ConnectionInfo> connections);
	
	void asyncPieces(final List<PieceInfo> pieces);

	void asyncTorrentStatus(final TorrentInfo torrent);
}
