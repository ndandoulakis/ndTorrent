package com.ndtorrent.client;

import java.util.Map;
import java.util.SortedMap;

public class Bencoder {

	public static String encode(Object object) {
		// Encodes String, Long, Iterable<Object> or SortedMap<String, Object>
		return encodeBenObject(object);
	}

	static String encodeBenObject(Object benObject) {
		String text = encodeBenString(benObject);
		
		if (text == null)
			text = encodeBenInteger(benObject);

		if (text == null)
			text = encodeBenList(benObject);

		if (text == null)
			text = encodeBenDict(benObject);

		return text;
	}

	@SuppressWarnings("unchecked")
	static String encodeBenList(Object benObject) {
		if (benObject instanceof Iterable<?>) {
			StringBuilder builder = new StringBuilder();
			builder.append("l");
			
			for (Object o: (Iterable<Object>) benObject) {
				String s = encodeBenObject(o);
				if (s != null)
					builder.append(s);
			}
			
			builder.append("e");
			return builder.toString();
		}
			
		return null; 
	}

	@SuppressWarnings("unchecked")
	static String encodeBenDict(Object benObject) {
		if (benObject instanceof SortedMap<?, ?>) {
			StringBuilder builder = new StringBuilder();
			builder.append("d");
			
			Map<String, Object> map = (Map<String, Object>) benObject;
			for (Map.Entry<String, Object> entry : map.entrySet()) {
				String k = encodeBenString(entry.getKey());
				String v = encodeBenObject(entry.getValue());
				if (k != null && v != null) {
					builder.append(k);
					builder.append(v);
				}
			}
			
			builder.append("e");
			return builder.toString();
		}

		return null;
	}

	static String encodeBenString(Object benObject) {
		if (benObject instanceof String) {
			String s = (String) benObject;
			return String.format("%d:%s", s.length(), s);
		}

		return null;
	}

	static String encodeBenInteger(Object benObject) {
		if (benObject instanceof Long) {
			return String.format("i%de", (Long) benObject);
		}

		return null;
	}

}
