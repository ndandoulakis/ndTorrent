package com.ndtorrent.client.status;

public interface StatusObserver<S> {

	void asyncUpdate(final S status);

	// A Swing component could implement asyncUpdate() like this
	// { SwingUtilities.invokeLater(new Runnable() {set/draw status}); }

}
