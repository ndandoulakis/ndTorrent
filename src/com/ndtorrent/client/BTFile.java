package com.ndtorrent.client;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.SortedMap;

public final class BTFile implements Comparable<Long> {
	private RandomAccessFile file;
	private String name;
	private long length;
	private long offset;

	// boolean skip;

	public BTFile(String name, long length, long offset) {
		// Creates a BTFile instance without opening the file.
		// To open the file, use createFileAndPath method.

		this.name = name;
		this.length = length;
		this.offset = offset;
	}

	private BTFile(List<String> path, long length, long offset) {
		name = "";
		for (String s : path) {
			name += '/' + Bdecoder.utf8EncodedString(s);
		}
		name = name.substring(1);
		this.length = length;
		this.offset = offset;
	}

	public static BTFile[] fromMetaInfo(MetaInfo meta) {
		// Returns an array of initialized BTFile instances without creating
		// OS files. For the latter, call createFileAndPath() on each instance.

		if (!meta.areMultipleFiles()) {
			BTFile[] f = { new BTFile(meta.getName(), meta.getLength(), 0) };
			return f;
		}

		List<Object> info_files = meta.getFileList();
		BTFile[] files = new BTFile[info_files.size()];
		long offset = 0;
		for (int i = 0; i < files.length; i++) {
			Object o = info_files.get(i);

			@SuppressWarnings("unchecked")
			SortedMap<String, Object> m = (SortedMap<String, Object>) o;
			Long length = (Long) m.get("length");

			@SuppressWarnings("unchecked")
			List<String> path = (List<String>) m.get("path");

			files[i] = new BTFile(path, length, offset);
			offset += length.longValue();
		}

		return files;
	}

	public String getName() {
		return name;
	}

	public long getLength() {
		return length;
	}

	public long getOffset() {
		return offset;
	}

	public void createFileAndPath(String parent_path) throws IOException {
		// Create the file if doesn't exist, or has not the right length.
		// The file is opened for reading and for synchronous writing to
		// the underlying storage device.
		String parent = new File(name).getParent();
		new File(parent_path + "./" + (parent != null ? parent : "")).mkdirs();
		file = new RandomAccessFile(parent_path + "./" + name, "rwd");
		if (file.length() != length) {
			file.setLength(length);
		}
	}

	public void close() {
		try {
			file.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public int write(ByteBuffer src, long file_position) throws IOException {
		// Starting at buffer's current position, we write as many bytes
		// as possible at the given file position without growing the file.
		if (file_position >= length)
			return -1; // same as read()
		// We need to set and reset the limit() before and after the write().
		// The duplicate() helps, in the case of an exception, to not leave
		// the source buffer in an intermediate state.
		ByteBuffer bb = src.duplicate();
		int remaining = (int) Math.min(bb.remaining(), length - file_position);
		bb.limit(bb.position() + remaining);
		int n = file.getChannel().write(bb, file_position);
		src.position(bb.position());
		return n;
	}

	public int read(ByteBuffer dst, long file_position) throws IOException {
		// Starting at buffer's current position, we read as many bytes
		// as possible from the given file position.
		return file.getChannel().read(dst, file_position);
	}

	@Override
	public int compareTo(Long other_offset) {
		return Long.signum(offset - other_offset.longValue());
	}

}
