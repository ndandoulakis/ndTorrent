package com.ndtorrent.client;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

final class DecodedResult {
	Object value;
	String remainder;
}

public final class Bdecoder {
	// Parsing Expression Grammar

	// BenObject ::= BenString | BenInteger | BenList | BenDict
	// BenList ::= l BenObject* e
	// BenDict ::= d (BenString BenObject)* e
	// BenString ::= length : bytes{length}
	// BenInteger ::= i "0|-?[1-9]\\d*" e

	// Various methods currently exploit the O(1) String.substring
	// implementation; the overall runtime complexity is O(n).

	public static Object decode(String text) {
		// Object will be an instance of String, Long,
		// List<Object>, SortedMap<String, Object> or null
		return decodeBenObject(text).value;
	}

	public static String utf8EncodedString(Object binaryText) {
		if (binaryText != null)
			try {
				return new String(((String) binaryText).getBytes("ISO-8859-1"),
						"UTF-8");
			} catch (Exception e) {
				e.printStackTrace();
			}
		return null;
	}

	static DecodedResult decodeBenObject(String text) {
		DecodedResult result = decodeBenString(text);

		if (result.value == null)
			result = decodeBenInteger(text);

		if (result.value == null)
			result = decodeBenList(text);

		if (result.value == null)
			result = decodeBenDict(text);

		return result;
	}

	static DecodedResult decodeBenList(String text) {
		DecodedResult result = new DecodedResult();

		if (text == null)
			return result;

		List<Object> members = new ArrayList<Object>();

		if (text.startsWith("le")) {
			result.value = members;
			result.remainder = text.substring(2);
		} else if (text.startsWith("l")) {
			DecodedResult m = decodeBenObject(text.substring(1));

			while (m.value != null) {
				members.add(m.value);

				if (m.remainder.startsWith("e")) {
					result.value = members;
					result.remainder = m.remainder.substring(1);
					break;
				}

				m = decodeBenObject(m.remainder);
			}
		}

		return result;
	}

	static DecodedResult decodeBenDict(String text) {
		DecodedResult result = new DecodedResult();

		if (text == null)
			return result;

		TreeMap<String, Object> members = new TreeMap<String, Object>();

		if (text.startsWith("de")) {
			result.value = members;
			result.remainder = text.substring(2);
		} else if (text.startsWith("d")) {
			DecodedResult k = decodeBenString(text.substring(1));
			DecodedResult v = decodeBenObject(k.remainder);

			while (k.value != null && v.value != null) {
				members.put((String) k.value, v.value);

				if (v.remainder.startsWith("e")) {
					result.value = members;
					result.remainder = v.remainder.substring(1);
					break;
				}

				k = decodeBenString(v.remainder);
				v = decodeBenObject(k.remainder);
			}
		}

		return result;
	}

	static DecodedResult decodeBenString(String text) {
		DecodedResult result = new DecodedResult();

		if (text == null)
			return result;

		String ts[] = text.split(":", 2);

		if (ts.length == 2 && validBenInteger(ts[0])) {
			int length = Integer.parseInt(ts[0]);
			if (length >= 0 && length <= ts[1].length()) {
				result.value = ts[1].substring(0, length);
				result.remainder = ts[1].substring(length);
			}
		}

		return result;
	}

	static DecodedResult decodeBenInteger(String text) {
		DecodedResult result = new DecodedResult();

		if (text == null)
			return result;

		if (text.startsWith("i")) {
			int e = text.indexOf('e', 1);
			if (e >= 0) {
				String t = text.substring(1, e);
				if (validBenInteger(t)) {
					result.value = Long.parseLong(t);
					result.remainder = text.substring(e + 1);
				}
			}
		}

		return result;
	}

	static boolean validBenInteger(String text) {
		return text.matches("0|-?[1-9]\\d*");
	}

}
