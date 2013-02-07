package com.ndtorrent.client;

import java.io.FileInputStream;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

public final class MetaInfo {
	// Strings in torrent files are expected to be UTF-8 encoded.
	// Use Bdecoder.utf8EncodedString to convert a decoded binary
	// string to a proper String, if you access meta info structures
	// directly.

	Map<String, Object> meta;
	List<Object> announce_list;

	Map<String, Object> info;
	byte info_hash[];

	@SuppressWarnings("unchecked")
	public MetaInfo(String filename) {
		try {
			// Decode torrent file
			String text = null;
			FileInputStream fis = new FileInputStream(filename);
			text = new Scanner(fis, "ISO-8859-1").useDelimiter("^").next();
			if (text.startsWith("d")) {
				meta = (Map<String, Object>) Bdecoder.decode(text);
			}

			announce_list = (List<Object>) meta.get("announce-list");

			info = (Map<String, Object>) meta.get("info");
			MessageDigest m;
			m = MessageDigest.getInstance("SHA-1");
			info_hash = m.digest(Bencoder.encode(info).getBytes("ISO-8859-1"));
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	// Meta Dictionary //

	public String getAnnounce() {
		return Bdecoder.utf8EncodedString(meta.get("announce"));
	}

	public String getComment() {
		return Bdecoder.utf8EncodedString(meta.get("comment"));
	}

	public String getCreatedBy() {
		return Bdecoder.utf8EncodedString(meta.get("created by"));
	}

	public String getCreationDate() {
		if (meta.containsKey("creation date"))
			return new Date(((Long) meta.get("creation date")) * 1000)
					.toString();
		else
			return null;
	}

	public String getEncoding() {
		return Bdecoder.utf8EncodedString(meta.get("encoding"));
	}

	public String getInfoHash() {
		try {
			return new String(info_hash, "ISO-8859-1");
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	// Info Dictionary //

	public Long getPieceLength() {
		return (Long) info.get("piece length");
	}

	public byte[] getPieces() {
		try {
			return ((String) info.get("pieces")).getBytes("ISO-8859-1");
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
			return null;
		}
	}

	public Long getPrivate() {
		return (Long) info.get("private");
	}

	// Single-file mode
	public String getName() {
		return Bdecoder.utf8EncodedString(info.get("name"));
	}

	public Long getLength() {
		return (Long) info.get("length");
	}

	public String getMD5Sum() {
		return Bdecoder.utf8EncodedString(info.get("md5sum"));
	}

	// Multiple-file mode
	public boolean areMultipleFiles() {
		return info.get("files") != null;
	}

	@SuppressWarnings("unchecked")
	public List<Object> getFileList() {
		return (List<Object>) info.get("files");
	}

}
