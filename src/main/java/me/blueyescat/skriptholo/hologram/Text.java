package me.blueyescat.skriptholo.hologram;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/**
 * Turns the raw line text into a component. Supports the formats scripts actually use: section
 * codes produced by Skript itself, {@code &} codes and {@code &#RRGGBB} hex colors.
 */
final class Text {

	private static final LegacyComponentSerializer SERIALIZER = LegacyComponentSerializer.builder()
			.character(LegacyComponentSerializer.SECTION_CHAR)
			.hexColors()
			.useUnusualXRepeatedCharacterHexFormat()
			.build();

	private Text() {}

	static Component parse(String raw) {
		if (raw.isEmpty())
			return Component.empty();
		return SERIALIZER.deserialize(translate(raw));
	}

	/** Replaces {@code &} codes with section codes, leaving every other ampersand untouched. */
	static String translate(String raw) {
		int first = raw.indexOf('&');
		if (first == -1)
			return raw;
		int length = raw.length();
		StringBuilder builder = new StringBuilder(length + 16);
		builder.append(raw, 0, first);
		for (int i = first; i < length; i++) {
			char c = raw.charAt(i);
			if (c == '&' && i + 1 < length) {
				char next = raw.charAt(i + 1);
				if (next == '#' && isHex(raw, i + 2, 6)) {
					builder.append(LegacyComponentSerializer.SECTION_CHAR).append('x');
					for (int j = i + 2; j < i + 8; j++)
						builder.append(LegacyComponentSerializer.SECTION_CHAR).append(raw.charAt(j));
					i += 7;
					continue;
				}
				if (isCode(next)) {
					builder.append(LegacyComponentSerializer.SECTION_CHAR).append(next);
					i++;
					continue;
				}
			}
			builder.append(c);
		}
		return builder.toString();
	}

	/** Number of rendered rows, a text display renders every line break as a new row. */
	static int rows(String raw) {
		int rows = 1;
		for (int i = 0, length = raw.length(); i < length; i++) {
			if (raw.charAt(i) == '\n')
				rows++;
		}
		return rows;
	}

	private static boolean isCode(char c) {
		return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F')
				|| (c >= 'k' && c <= 'o') || (c >= 'K' && c <= 'O') || c == 'r' || c == 'R'
				|| c == 'x' || c == 'X';
	}

	private static boolean isHex(String s, int from, int count) {
		if (from + count > s.length())
			return false;
		for (int i = from; i < from + count; i++) {
			char c = s.charAt(i);
			if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F')))
				return false;
		}
		return true;
	}

}
