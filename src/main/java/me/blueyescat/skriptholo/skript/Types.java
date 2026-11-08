package me.blueyescat.skriptholo.skript;

import ch.njol.skript.aliases.ItemType;
import ch.njol.skript.classes.Changer;
import ch.njol.skript.classes.ClassInfo;
import ch.njol.skript.classes.Parser;
import ch.njol.skript.lang.ParseContext;
import ch.njol.skript.registrations.Classes;
import ch.njol.util.coll.CollectionUtils;
import me.blueyescat.skriptholo.hologram.Hologram;
import me.blueyescat.skriptholo.hologram.HologramLine;
import me.blueyescat.skriptholo.util.Utils;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.lang.converter.Converter;
import org.skriptlang.skript.lang.converter.Converters;

public class Types {

	/** Shared by the expressions that return single lines. */
	public static final Changer<HologramLine> HOLOGRAM_LINE_CHANGER = new Changer<>() {
		@Override
		public Class<?>[] acceptChange(ChangeMode mode) {
			if (mode == ChangeMode.DELETE || mode == ChangeMode.RESET || mode == ChangeMode.SET)
				return CollectionUtils.array(String.class, ItemType.class);
			return null;
		}

		@Override
		public void change(HologramLine[] lines, @Nullable Object[] delta, ChangeMode mode) {
			if (mode == ChangeMode.DELETE || mode == ChangeMode.RESET) {
				for (HologramLine line : lines)
					line.delete();
				return;
			}
			if (delta == null || delta.length == 0)
				return;
			Object content = Utils.toLineContent(delta[0]);
			if (content == null)
				return;
			for (HologramLine line : lines) {
				if (content instanceof ItemStack item)
					line.setItem(item);
				else
					line.setText((String) content);
			}
		}
	};

	public static void register() {
		Classes.registerClass(new ClassInfo<>(Hologram.class, "hologram")
				.user("holo(gram)?s?")
				.name("Hologram")
				.description("A client side hologram made of display entities. Can be deleted using the `delete/clear` changer or " +
						"can be reset using the `reset` changer. " +
						"When you delete a hologram that is stored in a variable, the hologram object will still " +
						"exist in the variable but will not be usable. You should delete the variable too in this case.")
				.since("1.0.0")
				.changer(new Changer<Hologram>() {
					@Override
					public Class<?>[] acceptChange(ChangeMode mode) {
						if (mode == ChangeMode.DELETE || mode == ChangeMode.RESET)
							return CollectionUtils.array();
						return null;
					}

					@Override
					public void change(Hologram[] holograms, @Nullable Object[] delta, ChangeMode mode) {
						for (Hologram holo : holograms) {
							if (mode == ChangeMode.DELETE)
								holo.delete();
							else
								holo.clearLines();
						}
					}
				})
				.parser(new Parser<>() {
					@Override
					public boolean canParse(ParseContext context) {
						return false;
					}

					@Override
					public String toString(Hologram holo, int flags) {
						return "hologram";
					}

					@Override
					public String toVariableNameString(Hologram holo) {
						return "hologram";
					}
				}));

		Converters.registerConverter(Hologram.class, Location.class, (Converter<Hologram, Location>) Hologram::getLocation);

		Classes.registerClass(new ClassInfo<>(HologramLine.class, "hologramline")
				.user("holo(gram)?( |-)?lines?")
				.name("Hologram Line")
				.description("A line of a hologram. Can be deleted using the 'delete/clear' changer.",
						"",
						"Has converters to `text` and `item type`. " +
						"Converters mean that you can use this type like the converted types. " +
						"For example you can do `give line 1 of hologram to player` and `if event-hologram-line is \"test\":`.")
				.since("1.0.0")
				.changer(HOLOGRAM_LINE_CHANGER)
				.parser(new Parser<>() {
					@Override
					public boolean canParse(ParseContext context) {
						return false;
					}

					@Override
					public String toString(HologramLine line, int flags) {
						return line.isItem() ? "hologram item line" : "hologram line";
					}

					@Override
					public String toVariableNameString(HologramLine line) {
						return line.isItem() ? "hologram item line" : "hologram line";
					}
				}));

		Converters.registerConverter(HologramLine.class, String.class, (Converter<HologramLine, String>) HologramLine::getText);
		Converters.registerConverter(HologramLine.class, ItemType.class, (Converter<HologramLine, ItemType>) line -> {
			ItemStack item = line.getItem();
			return item == null ? null : new ItemType(item);
		});
	}

}
