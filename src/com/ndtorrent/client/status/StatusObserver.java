package com.ndtorrent.client.status;

import java.util.List;

public interface StatusObserver {

	void asyncUpdate(final List<ConnectionInfo> status);

	// A Swing component could implement asyncUpdate() like this
	// { SwingUtilities.invokeLater(new Runnable() {set/draw status}); }

}
