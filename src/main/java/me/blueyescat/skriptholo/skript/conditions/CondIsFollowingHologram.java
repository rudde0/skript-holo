package me.blueyescat.skriptholo.skript.conditions;

import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Examples;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.lang.Condition;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.util.Kleenean;
import me.blueyescat.skriptholo.hologram.Hologram;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;

@Name("Is Following Hologram")
@Description("Checks whether the given hologram is a following hologram.")
@Examples("if {_holo} is a following hologram:")
@Since("1.0.0")
public class CondIsFollowingHologram extends Condition {

	public static void register(SyntaxRegistry registry) {
		registry.register(SyntaxRegistry.CONDITION, SyntaxInfo.builder(CondIsFollowingHologram.class)
				.addPatterns(
						"[holo[gram][s]] %holograms% (is|are) [a] following [holo[gram][s]]",
						"[holo[gram][s]] %holograms% (isn't|is not|aren't|are not) [a] following [holo[gram][s]]")
				.build());
	}

	private Expression<Hologram> holograms;

	@SuppressWarnings("unchecked")
	@Override
	public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult parseResult) {
		holograms = (Expression<Hologram>) exprs[0];
		setNegated(matchedPattern == 1);
		return true;
	}

	@Override
	public boolean check(Event e) {
		return holograms.check(e, Hologram::isFollowing, isNegated());
	}

	@Override
	public String toString(@Nullable Event e, boolean debug) {
		return holograms.toString(e, debug) + " is " + (isNegated() ? "not " : "") + "following";
	}

}
