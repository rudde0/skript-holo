package me.blueyescat.skriptholo.skript.conditions;

import ch.njol.skript.doc.*;
import ch.njol.skript.lang.Condition;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.util.Kleenean;
import me.blueyescat.skriptholo.hologram.Hologram;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;

@Name("Is Hologram Visible by Default")
@Description("Checks whether the given hologram is visible by default.")
@Examples({"if {_hologram} is visible by default:",
		"if holograms {_list::*} are hidden by default:"})
@Since("1.0.0")
public class CondIsHologramVisibleByDefault extends Condition {

	public static void register(SyntaxRegistry registry) {
		registry.register(SyntaxRegistry.CONDITION, SyntaxInfo.builder(CondIsHologramVisibleByDefault.class)
				.addPatterns(
						"[holo[gram][s]] %holograms% (is|are) (visible|1¦(invisible|hidden)) by default",
						"[holo[gram][s]] %holograms% (isn't|is not|aren't|are not) (visible|1¦(invisible|hidden)) by default")
				.build());
	}

	private Expression<Hologram> holograms;

	@SuppressWarnings("unchecked")
	@Override
	public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult parseResult) {
		holograms = (Expression<Hologram>) exprs[0];
		setNegated(matchedPattern == 1 ^ parseResult.mark == 1);
		return true;
	}

	@Override
	public boolean check(Event e) {
		return holograms.check(e, Hologram::isVisibleByDefault, isNegated());
	}

	@Override
	public String toString(@Nullable Event e, boolean debug) {
		return holograms.toString(e, debug) + " is " + (isNegated() ? "not " : "") + "visible by default";
	}

}
