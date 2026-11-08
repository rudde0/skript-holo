package me.blueyescat.skriptholo.util.json;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Minimal drop-in replacement for {@code org.json.simple.JSONObject}.
 * <p>
 * Metrics was the only consumer of json-simple, so shipping the whole library just to build a
 * handful of objects meant an extra dependency inside the plugin jar. Only {@code put} and
 * {@code toString} were ever used, both of which come for free from {@link LinkedHashMap}.
 */
public class JSONObject extends LinkedHashMap<String, Object> {

	private static final long serialVersionUID = 1L;

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder(32).append('{');
		boolean first = true;
		for (Map.Entry<String, Object> entry : entrySet()) {
			if (!first)
				builder.append(',');
			first = false;
			JSONValue.writeString(builder, String.valueOf(entry.getKey()));
			builder.append(':');
			JSONValue.write(builder, entry.getValue());
		}
		return builder.append('}').toString();
	}

}
