package me.blueyescat.skriptholo.skript.effects;

import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Examples;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.lang.Effect;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.util.Kleenean;
import me.blueyescat.skriptholo.hologram.Hologram;
import me.blueyescat.skriptholo.util.Utils;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;

import java.util.List;

@Name("Add Hologram Line")
@Description("Adds new lines to a hologram. " +
		"`insert` means adding after the specified line and moving the next lines below. " +
		"Click-able, touchable and interactive lines fire the `Hologram Line Click` event.")
@Examples({"append a stone to {_holo}",
		"prepend \"<light green>Test\" to the created hologram",
		"insert lines \"&cRedstone\" and a redstone in holo at line 2",
		"insert every diamond armor into the hologram at the 5th line"})
@Since("1.0.0")
public class EffAddHologramLine extends Effect {

	public static void register(SyntaxRegistry registry) {
		registry.register(SyntaxRegistry.EFFECT, SyntaxInfo.builder(EffAddHologramLine.class)
				.addPatterns(
						"append [((1¦click|2¦touch|3¦interact)[-]able|3¦interactive)] [line[s]] %-strings/itemtypes% to [holo[gram][s]] %holograms%",
						"prepend [((1¦click|2¦touch|3¦interact)[-]able|3¦interactive)] [line[s]] %-strings/itemtypes% to [holo[gram][s]] %holograms%",
						"insert [((1¦click|2¦touch|3¦interact)[-]able|3¦interactive)] [line[s]] %-strings/itemtypes% in[to] [holo[gram][s]] %holograms% at line %number%",
						"insert [((1¦click|2¦touch|3¦interact)[-]able|3¦interactive)] [line[s]] %-strings/itemtypes% in[to] [holo[gram][s]] %holograms% at [the] %number%(st|nd|rd|th) line")
				.build());
	}

	private enum Mode {
		APPEND, PREPEND, INSERT
	}

	private Mode mode;
	private boolean interactive;
	private Expression<?> lines;
	private Expression<Hologram> holograms;
	@Nullable
	private Expression<Number> line;

	@SuppressWarnings("unchecked")
	@Override
	public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult parseResult) {
		mode = matchedPattern == 0 ? Mode.APPEND : matchedPattern == 1 ? Mode.PREPEND : Mode.INSERT;
		if (mode == Mode.INSERT)
			line = (Expression<Number>) exprs[2];
		interactive = parseResult.mark != 0;
		lines = exprs[0];
		holograms = (Expression<Hologram>) exprs[1];
		return true;
	}

	@Override
	protected void execute(Event e) {
		List<Object> contents = Utils.toLineContents(lines.getArray(e));
		if (contents.isEmpty())
			return;
		int insertAt = 0;
		if (mode == Mode.INSERT) {
			assert line != null;
			Number number = line.getSingle(e);
			if (number == null)
				return;
			insertAt = number.intValue();
		}
		for (Hologram holo : holograms.getArray(e)) {
			if (holo.isDeleted())
				continue;
			if (mode == Mode.APPEND) {
				for (Object content : contents)
					holo.addLine(content, interactive);
				continue;
			}
			int index = mode == Mode.PREPEND ? 0 : insertAt;
			if (index < 0 || index > holo.size())
				continue;
			for (Object content : contents)
				holo.insertLine(index++, content, interactive);
		}
	}

	@Override
	public String toString(@Nullable Event e, boolean debug) {
		String lines = (interactive ? "interactive lines " : "") + this.lines.toString(e, debug);
		return switch (mode) {
			case APPEND -> "append " + lines + " to " + holograms.toString(e, debug);
			case PREPEND -> "prepend " + lines + " to " + holograms.toString(e, debug);
			case INSERT -> "insert " + lines + " into " + holograms.toString(e, debug) +
					" at line " + (line == null ? "" : line.toString(e, debug));
		};
	}

}
