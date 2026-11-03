package org.studioquire.oldfaithful.hardening.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A small strict JSON reader and string quoter, enough for Mojang's asset index and session responses. The loader
 * ships no public JSON library and the game has none, so this avoids depending on a jar the player may not have.
 * Objects become {@code Map<String, Object>}, arrays {@code List<Object>}, numbers {@code Double}.
 */
public final class Json {
	private static final int MAX_DEPTH = 64;

	private final String text;
	private int pos;

	private Json(String text) {
		this.text = text;
	}

	public static Object parse(String text) {
		Json json = new Json(text);
		json.skipWhitespace();
		Object value = json.value(0);
		json.skipWhitespace();
		if (json.pos != text.length()) throw json.error("trailing data");
		return value;
	}

	/** The object at {@code value}, or an exception naming what was expected. */
	@SuppressWarnings("unchecked")
	public static Map<String, Object> object(Object value) {
		if (!(value instanceof Map)) throw new IllegalArgumentException("expected a JSON object");
		return (Map<String, Object>) value;
	}

	public static String string(Map<String, Object> object, String key) {
		Object value = object.get(key);
		return value instanceof String ? (String) value : null;
	}

	public static String quote(String s) {
		StringBuilder sb = new StringBuilder(s.length() + 2).append('"');
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			switch (c) {
				case '"': sb.append("\\\""); break;
				case '\\': sb.append("\\\\"); break;
				case '\n': sb.append("\\n"); break;
				case '\r': sb.append("\\r"); break;
				case '\t': sb.append("\\t"); break;
				default:
					if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
					else sb.append(c);
			}
		}
		return sb.append('"').toString();
	}

	private Object value(int depth) {
		if (depth > MAX_DEPTH) throw error("nested too deeply");
		if (pos >= text.length()) throw error("unexpected end");
		char c = text.charAt(pos);
		switch (c) {
			case '{': return object(depth);
			case '[': return array(depth);
			case '"': return string();
			case 't': return literal("true", Boolean.TRUE);
			case 'f': return literal("false", Boolean.FALSE);
			case 'n': return literal("null", null);
			default:
				if (c == '-' || (c >= '0' && c <= '9')) return number();
				throw error("unexpected character");
		}
	}

	private Map<String, Object> object(int depth) {
		Map<String, Object> map = new LinkedHashMap<String, Object>();
		pos++;
		skipWhitespace();
		if (peek() == '}') {
			pos++;
			return map;
		}
		while (true) {
			skipWhitespace();
			if (peek() != '"') throw error("expected a key");
			String key = string();
			skipWhitespace();
			expect(':');
			skipWhitespace();
			map.put(key, value(depth + 1));
			skipWhitespace();
			char c = next();
			if (c == '}') return map;
			if (c != ',') throw error("expected , or }");
		}
	}

	private List<Object> array(int depth) {
		List<Object> list = new ArrayList<Object>();
		pos++;
		skipWhitespace();
		if (peek() == ']') {
			pos++;
			return list;
		}
		while (true) {
			skipWhitespace();
			list.add(value(depth + 1));
			skipWhitespace();
			char c = next();
			if (c == ']') return list;
			if (c != ',') throw error("expected , or ]");
		}
	}

	private String string() {
		expect('"');
		StringBuilder sb = new StringBuilder();
		while (true) {
			char c = next();
			if (c == '"') return sb.toString();
			if (c < 0x20) throw error("control character in string");
			if (c != '\\') {
				sb.append(c);
				continue;
			}
			char e = next();
			switch (e) {
				case '"': sb.append('"'); break;
				case '\\': sb.append('\\'); break;
				case '/': sb.append('/'); break;
				case 'b': sb.append('\b'); break;
				case 'f': sb.append('\f'); break;
				case 'n': sb.append('\n'); break;
				case 'r': sb.append('\r'); break;
				case 't': sb.append('\t'); break;
				case 'u':
					if (pos + 4 > text.length()) throw error("bad escape");
					try {
						sb.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
					} catch (NumberFormatException ex) {
						throw error("bad escape");
					}
					pos += 4;
					break;
				default: throw error("bad escape");
			}
		}
	}

	private Double number() {
		int start = pos;
		if (peek() == '-') pos++;
		while (pos < text.length() && "0123456789.eE+-".indexOf(text.charAt(pos)) >= 0) pos++;
		try {
			return Double.valueOf(text.substring(start, pos));
		} catch (NumberFormatException e) {
			throw error("bad number");
		}
	}

	private Object literal(String word, Object value) {
		if (!text.startsWith(word, pos)) throw error("unexpected word");
		pos += word.length();
		return value;
	}

	private void skipWhitespace() {
		while (pos < text.length()) {
			char c = text.charAt(pos);
			if (c != ' ' && c != '\t' && c != '\n' && c != '\r') return;
			pos++;
		}
	}

	private char peek() {
		if (pos >= text.length()) throw error("unexpected end");
		return text.charAt(pos);
	}

	private char next() {
		char c = peek();
		pos++;
		return c;
	}

	private void expect(char c) {
		if (next() != c) throw error("expected " + c);
	}

	private IllegalArgumentException error(String message) {
		return new IllegalArgumentException("Bad JSON at " + pos + ": " + message);
	}
}
