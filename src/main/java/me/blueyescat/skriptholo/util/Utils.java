package me.blueyescat.skriptholo.util;

import ch.njol.skript.aliases.ItemType;
import ch.njol.skript.util.Direction;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class Utils {

	private Utils() {}

	public static Location offsetLocation(Location loc, Direction... directions) {
		for (Direction d : directions)
			loc = d.getRelative(loc);
		return loc;
	}

	/**
	 * Turns Skript values into line contents: texts stay texts, an item type becomes one line per
	 * item it stands for. Anything else is ignored.
	 */
	public static List<Object> toLineContents(Object[] values) {
		List<Object> contents = new ArrayList<>(values.length);
		for (Object value : values) {
			if (value instanceof String) {
				contents.add(value);
			} else if (value instanceof ItemType itemType) {
				for (ItemStack item : itemType.getItem().getAll())
					contents.add(item);
			}
		}
		return contents;
	}

	/** A single line content: a text, or one random item of an item type. Null if not supported. */
	public static Object toLineContent(Object value) {
		if (value instanceof String)
			return value;
		if (value instanceof ItemType itemType)
			return itemType.getItem().getRandom();
		return null;
	}

}
