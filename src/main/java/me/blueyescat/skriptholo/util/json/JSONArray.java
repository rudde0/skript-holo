package me.blueyescat.skriptholo.util.json;

import java.util.ArrayList;

/**
 * Minimal drop-in replacement for {@code org.json.simple.JSONArray}.
 *
 * @see JSONObject
 */
public class JSONArray extends ArrayList<Object> {

	private static final long serialVersionUID = 1L;

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder(32).append('[');
		for (int i = 0; i < size(); i++) {
			if (i != 0)
				builder.append(',');
			JSONValue.write(builder, get(i));
		}
		return builder.append(']').toString();
	}

}
