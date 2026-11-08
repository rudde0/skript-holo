package me.blueyescat.skriptholo.skript.expressions;

import ch.njol.skript.aliases.ItemType;
import ch.njol.skript.classes.Changer.ChangeMode;
import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Examples;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.expressions.base.PropertyExpression;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.util.Kleenean;
import ch.njol.util.coll.CollectionUtils;
import me.blueyescat.skriptholo.hologram.Hologram;
import me.blueyescat.skriptholo.hologram.HologramLine;
import me.blueyescat.skriptholo.skript.Types;
import me.blueyescat.skriptholo.util.Utils;
import org.bukkit.event.Event;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.lang.comparator.Comparators;
import org.skriptlang.skript.lang.comparator.Relation;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;

import java.util.ArrayList;
import java.util.List;

@Name("Hologram Lines")
@Description({"Returns lines of a hologram. Can be changed, removing a text or an item means it will search lines " +
		"that match exactly with the input and remove them.",
		"",
		"It is possible to create new lines using the `set` changer, for example if the hologram has 3 lines and " +
		"you set line 5, the line 4 will be blank."})
@Examples({"loop lines of the last created hologram:",
		"\tif loop-hologram line is an item line:",
		"\t\tdelete loop-hologram line",
		"",
		"set line 5 of create hologram to \"test\""})
@Since("1.0.0")
public class ExprHologramLines extends SimpleExpression<HologramLine> {

	public static void register(SyntaxRegistry registry) {
		registry.register(SyntaxRegistry.EXPRESSION, SyntaxInfo.Expression
				.builder(ExprHologramLines.class, HologramLine.class)
				.priority(PropertyExpression.DEFAULT_PRIORITY)
				.addPatterns(
						"[all] [the] lines of [holo[gram][s]] %holograms%",
						"%holograms%'[s] [all] lines",
						"[the] [holo[gram][s]] line %number% of [holo[gram][s]] %holograms%",
						"[the] %number%(st|nd|rd|th) line[s] of [holo[gram][s]] %holograms%",
						"[the] (first|1¦last) line[s] of [holo[gram][s]] %holograms%")
				.build());
	}

	private Expression<Hologram> holograms;
	@Nullable
	private Expression<Number> line;
	private boolean firstLine, allLines, isSingle;

	@SuppressWarnings("unchecked")
	@Override
	public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult parseResult) {
		if (matchedPattern <= 1) {
			holograms = (Expression<Hologram>) exprs[0];
			allLines = true;
		} else if (matchedPattern <= 3) {
			line = (Expression<Number>) exprs[0];
			holograms = (Expression<Hologram>) exprs[1];
		} else {
			firstLine = parseResult.mark == 0;
			holograms = (Expression<Hologram>) exprs[0];
		}
		isSingle = !allLines && holograms.isSingle();
		return true;
	}

	@Override
	protected HologramLine[] get(Event e) {
		List<HologramLine> result = new ArrayList<>();
		Integer index = null;
		if (line != null) {
			Number number = line.getSingle(e);
			if (number == null)
				return new HologramLine[0];
			index = number.intValue() - 1;
		}
		for (Hologram holo : holograms.getArray(e)) {
			if (allLines) {
				result.addAll(holo.getLines());
			} else if (index != null) {
				HologramLine line = holo.getLine(index);
				if (line != null)
					result.add(line);
			} else if (holo.size() != 0) {
				result.add(holo.getLine(firstLine ? 0 : holo.size() - 1));
			}
		}
		return result.toArray(new HologramLine[0]);
	}

	@Override
	public Class<?>[] acceptChange(ChangeMode mode) {
		if (allLines) {
			return switch (mode) {
				case ADD, REMOVE, REMOVE_ALL, SET, DELETE, RESET -> CollectionUtils.array(String[].class, ItemType[].class);
				default -> null;
			};
		}
		if (mode == ChangeMode.SET || mode == ChangeMode.DELETE || mode == ChangeMode.RESET)
			return CollectionUtils.array(String.class, ItemType.class);
		return null;
	}

	@Override
	public void change(Event e, @Nullable Object[] delta, ChangeMode mode) {
		if (allLines) {
			changeAll(e, delta, mode);
			return;
		}
		// "line x" can create new lines with SET, everything else uses the hologram line changer.
		if (mode != ChangeMode.SET || line == null) {
			Types.HOLOGRAM_LINE_CHANGER.change(get(e), delta, mode);
			return;
		}
		if (delta == null || delta.length == 0)
			return;
		Number number = line.getSingle(e);
		Object content = Utils.toLineContent(delta[0]);
		if (number == null || content == null)
			return;
		int index = number.intValue() - 1;
		if (index < 0)
			return;
		for (Hologram holo : holograms.getArray(e)) {
			if (holo.isDeleted())
				continue;
			if (index < holo.size()) {
				holo.setLine(index, content);
				continue;
			}
			while (holo.size() < index)
				holo.addLine("", false);
			holo.addLine(content, false);
		}
	}

	private void changeAll(Event e, @Nullable Object[] delta, ChangeMode mode) {
		Hologram[] targets = holograms.getArray(e);
		switch (mode) {
			case ADD -> {
				if (delta == null)
					return;
				List<Object> contents = Utils.toLineContents(delta);
				for (Hologram holo : targets) {
					for (Object content : contents)
						holo.addLine(content, false);
				}
			}
			case REMOVE, REMOVE_ALL -> {
				if (delta == null)
					return;
				for (Hologram holo : targets) {
					for (HologramLine line : holo.getLines()) {
						if (matches(line, delta))
							line.delete();
					}
				}
			}
			case SET -> {
				if (delta == null)
					return;
				List<Object> contents = Utils.toLineContents(delta);
				for (Hologram holo : targets)
					holo.setLines(contents, false);
			}
			case DELETE, RESET -> {
				for (Hologram holo : targets)
					holo.clearLines();
			}
			default -> {}
		}
	}

	private static boolean matches(HologramLine line, Object[] delta) {
		for (Object o : delta) {
			if (o instanceof String text) {
				if (line.isText() && Comparators.compare(line.getText(), text).isImpliedBy(Relation.EQUAL))
					return true;
			} else if (o instanceof ItemType itemType && line.isItem()) {
				ItemStack lineItem = line.getItem();
				if (lineItem == null)
					continue;
				for (ItemStack item : itemType.getItem().getAll()) {
					if (lineItem.getType() == item.getType())
						return true;
				}
			}
		}
		return false;
	}

	@Override
	public boolean isSingle() {
		return isSingle;
	}

	@Override
	public Class<? extends HologramLine> getReturnType() {
		return HologramLine.class;
	}

	@Override
	public String toString(@Nullable Event e, boolean debug) {
		if (allLines)
			return "the lines of " + holograms.toString(e, debug);
		if (line == null)
			return "the " + (firstLine ? "first" : "last") + " line of " + holograms.toString(e, debug);
		return "the line " + line.toString(e, debug) + " of " + holograms.toString(e, debug);
	}

}
