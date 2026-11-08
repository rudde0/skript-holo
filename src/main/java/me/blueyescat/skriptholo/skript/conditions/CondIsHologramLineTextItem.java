package me.blueyescat.skriptholo.skript.conditions;

import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Examples;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.lang.Condition;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.util.Kleenean;
import me.blueyescat.skriptholo.hologram.HologramLine;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;

@Name("Is Text/Item Line")
@Description("Checks whether the given hologram line is a text line or an item line. " +
		"The `Hologram Line Content` expression can be used to get the text or the item.")
@Examples({"if event-holo-line is a text line:",
		"if line 3 of holo {_variable} is an item line:",
		"if lines of event-holo aren't string lines:"})
@Since("1.0.0")
public class CondIsHologramLineTextItem extends Condition {

	public static void register(SyntaxRegistry registry) {
		registry.register(SyntaxRegistry.CONDITION, SyntaxInfo.builder(CondIsHologramLineTextItem.class)
				.addPatterns(
						"[holo[gram] line[s]] %hologramlines% (is|are) [a[n]] ((text|string)|1¦item) line[s]",
						"[holo[gram] line[s]] %hologramlines% (isn't|is not|aren't|are not) [a[n]] ((text|string)|1¦item) line[s]")
				.build());
	}

	private Expression<HologramLine> lines;
	private boolean isText;

	@SuppressWarnings("unchecked")
	@Override
	public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult parseResult) {
		lines = (Expression<HologramLine>) exprs[0];
		isText = parseResult.mark == 0;
		setNegated(matchedPattern == 1);
		return true;
	}

	@Override
	public boolean check(Event e) {
		if (isText)
			return lines.check(e, HologramLine::isText, isNegated());
		else
			return lines.check(e, HologramLine::isItem, isNegated());
	}

	@Override
	public String toString(@Nullable Event e, boolean debug) {
		return lines.toString(e, debug) + " is " + (isNegated() ? "not " : "") + (isText ? "text" : "item") + " line";
	}

}
