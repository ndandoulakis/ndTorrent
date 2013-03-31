package com.ndtorrent.client;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

public final class BTSocket {
	public static final int MAX_DATA_SIZE = 1 + 8 + 32 * 1024;
	public static final int MAX_HANDSHAKE_SECONDS = 25;
	public static final int TCP_SEND_BUFFER_SIZE = 4 * 1024;

	private SocketChannel channel;

	private boolean is_closed;

	private long created_at;
	private long joined_at;
	private long last_input_at;
	private long last_output_at;

	private HandshakeMsg input_handshake = HandshakeMsg.newEmptyHandshake();
	private HandshakeMsg output_handshake;

	private boolean is_input_error; // input stream has closed or MAX_DATA_SIZE
	private ByteBuffer input_data;
	private ByteBuffer input_prefix = ByteBuffer.allocate(4);

	private boolean is_output_error;
	private ByteBuffer output_data;
	private ByteBuffer output_prefix = ByteBuffer.allocate(4);

	private long input_total;
	private long output_total;

	private RollingTotal input_rate = new RollingTotal(5);
	private RollingTotal output_rate = new RollingTotal(5);
	// ? use ByteBuffer limit to control the rate

	private long blocks_input_total;

	public BTSocket(SocketChannel channel) {
		this.channel = channel;

		try {
			channel.configureBlocking(false);
			channel.socket().setTcpNoDelay(true);
			channel.socket().setSendBufferSize(TCP_SEND_BUFFER_SIZE);
		} catch (IOException e) {
			e.printStackTrace();
		}

		long now = System.nanoTime();
		created_at = now;
		joined_at = now;
		last_input_at = now;
		last_output_at = now;
	}

	public BTSocket(SocketAddress bindpoint) throws IOException {
		// Bind-point must be reusable.

		this(SocketChannel.open());
		channel.socket().setReuseAddress(true);
		channel.socket().bind(bindpoint);
	}

	public boolean hasOutputHandshake() {
		return output_handshake != null;
	}

	public boolean hasInputHandshake() {
		return !input_handshake.getData().hasRemaining();
	}

	public boolean isHandshakeDone() {
		return hasInputHandshake() && hasOutputHandshake()
				&& !output_handshake.getData().hasRemaining();
	}

	public boolean isHandshakeSuccessful() {
		return isHandshakeDone()
				&& input_handshake.getInfoHash().equals(
						output_handshake.getInfoHash());
	}

	public boolean isHandshakeExpired() {
		long now = System.nanoTime();
		return !isHandshakeDone()
				&& now - created_at > MAX_HANDSHAKE_SECONDS * 1e9;
	}

	public void processHandshakeMessages() {
		processInputHandshake();
		processOutputHandshake();
	}

	private void processInputHandshake() {
		if (is_input_error)
			return;
		try {
			is_input_error = channel.read(input_handshake.getData()) < 0;
		} catch (IOException e) {
			is_input_error = true;
		}
	}

	private void processOutputHandshake() {
		if (!hasOutputHandshake() || is_output_error)
			return;
		try {
			channel.write(output_handshake.getData());
		} catch (IOException e) {
			is_output_error = true;
		}
	}

	public HandshakeMsg getInputHandshake() {
		return hasInputHandshake() ? input_handshake : null;
	}

	public void setOutputHandshake(HandshakeMsg m) {
		if (m == null)
			throw new IllegalArgumentException("null handshake!");
		if (hasOutputHandshake())
			throw new IllegalStateException("handshake cannot be set twice!");
		output_handshake = m;
		output_handshake.getData().rewind();
	}

	public void processInput() {
		if (is_input_error)
			return;
		try {
			// When a ByteBuffer is full no bytes are read.
			is_input_error = readInput(input_prefix) < 0;
			if (!input_prefix.hasRemaining()) {
				if (input_data == null) {
					int length = input_prefix.getInt(0);
					if (length < 0 || length > MAX_DATA_SIZE) {
						is_input_error = true;
						return;
					}
					input_data = ByteBuffer.allocate(length);
				}
				is_input_error = readInput(input_data) < 0;
			}
		} catch (IOException e) {
			is_input_error = true;
			// Can a network outage raise an exception?
		}
	}

	private int readInput(ByteBuffer dst) throws IOException {
		int n = channel.read(dst);
		if (n > 0) {
			input_total += n;
			input_rate.add(n);
			if (input_data != null && input_data.position() > 0)
				if (input_data.get(0) == Message.PIECE)
					blocks_input_total += n;
		}
		return n;
	}

	public void processOutput() {
		if (!hasOutputMessage() || is_output_error)
			return;
		try {
			// When the Socket buffer is full no bytes are written.
			writeOutput(output_prefix);
			if (!output_prefix.hasRemaining()) {
				writeOutput(output_data);
				if (!output_data.hasRemaining())
					output_data = null;
			}
		} catch (IOException e) {
			is_output_error = true;
		}
	}

	private int writeOutput(ByteBuffer src) throws IOException {
		int n = channel.write(src);
		if (n > 0) {
			output_total += n;
			output_rate.add(n);
		}
		return n;
	}

	public boolean hasPartialInputMesssage() {
		return input_data != null && input_data.hasRemaining();
	}

	public boolean hasInputMessage() {
		return input_data != null && !input_data.hasRemaining();
	}

	public Message takeInputMessage() {
		if (!hasInputMessage())
			return null;

		Message m = Message.wrap(input_data);
		input_prefix.rewind();
		input_data.rewind();
		input_data = null;
		last_input_at = System.nanoTime();
		return m;
	}

	public boolean hasOutputMessage() {
		return output_data != null;
	}

	public boolean setOutputMessage(Message m) {
		if (hasOutputMessage() || m == null || is_output_error)
			return false;

		output_prefix.putInt(0, m.getLength());
		output_prefix.rewind();
		output_data = m.getData();
		output_data.rewind();
		last_output_at = System.nanoTime();
		return true;
	}

	public SelectionKey register(Selector sel, int ops, Object att)
			throws ClosedChannelException {

		return channel.register(sel, ops, att);
	}

	public long getInputTotal() {
		return input_total;
	}

	public long getOutputTotal() {
		return output_total;
	}

	public void clearInputTotal() {
		input_total = 0;
	}

	public void clearOutputTotal() {
		output_total = 0;
	}

	public void rollTotals() {
		input_rate.roll();
		output_rate.roll();
	}

	public double inputPerSec() {
		return input_rate.average();
	}

	public double outputPerSec() {
		return output_rate.average();
	}

	public long blocksInputTotal() {
		return blocks_input_total;
	}

	public void clearBlocksInputTotal() {
		blocks_input_total = 0;
	}

	public long createdAt() {
		return created_at;
	}

	public long joinedAt() {
		return joined_at;
	}

	public long lastInputMessageAt() {
		return last_input_at;
	}

	public long lastOutputMessageAt() {
		return last_output_at;
	}

	public int getLocalPort() {
		return channel.socket().getLocalPort();
	}

	public String getRemoteIP() {
		return channel.socket().getInetAddress().getHostAddress();
	}

	public int getRemotePort() {
		return channel.socket().getPort();
	}

	public boolean isOpen() {
		// SocketChannel.isOpen() returns true even if the other end
		// point has closed the connection.
		// SocketChannel.isConnected() on the other hand seems to
		// reflect the termination if at least one end point is closed.
		return !is_closed
				&& (channel.isConnectionPending() || channel.isConnected());
	}

	public void connect(SocketAddress remote) throws IOException {
		if (channel.connect(remote)) {
			channel.finishConnect();
		}
	}

	public boolean finishConnect() {
		try {
			return channel.finishConnect();
		} catch (IOException e) {
		}
		return false;
	}

	public void close() {
		// When a connection gets closed, buffered data may not have been sent.
		is_closed = true;
		try {
			channel.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public boolean isError() {
		return is_input_error || is_output_error;
	}

	public boolean isInputError() {
		return is_input_error;
	}

	public boolean isOutputError() {
		return is_output_error;
	}

}
