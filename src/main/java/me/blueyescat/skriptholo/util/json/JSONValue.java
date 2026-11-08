package me.blueyescat.skriptholo.util.json;

/**
 * Serialization helpers shared by {@link JSONObject} and {@link JSONArray}.
 */
final class JSONValue {

	private JSONValue() {}

	static void write(StringBuilder builder, Object value) {
		if (value == null) {
			builder.append("null");
		} else if (value instanceof JSONObject || value instanceof JSONArray || value instanceof Number
				|| value instanceof Boolean) {
			builder.append(value);
		} else {
			writeString(builder, String.valueOf(value));
		}
	}

	static void writeString(StringBuilder builder, String value) {
		builder.append('"');
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			switch (c) {
				case '"':
					builder.append("\\\"");
					break;
				case '\\':
					builder.append("\\\\");
					break;
				case '\b':
					builder.append("\\b");
					break;
				case '\f':
					builder.append("\\f");
					break;
				case '\n':
					builder.append("\\n");
					break;
				case '\r':
					builder.append("\\r");
					break;
				case '\t':
					builder.append("\\t");
					break;
				case '/':
					builder.append("\\/");
					break;
				default:
					// Escape control characters, everything else is sent as UTF-8.
					if (c < 0x20 || (c >= 0x7F && c <= 0x9F)) {
						String hex = Integer.toHexString(c);
						builder.append("\\u");
						for (int pad = hex.length(); pad < 4; pad++)
							builder.append('0');
						builder.append(hex);
					} else {
						builder.append(c);
					}
			}
		}
		builder.append('"');
	}

}
