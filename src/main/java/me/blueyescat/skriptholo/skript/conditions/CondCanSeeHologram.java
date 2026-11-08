package me.blueyescat.skriptholo.skript.conditions;

import ch.njol.skript.doc.*;
import ch.njol.skript.lang.Condition;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.util.Kleenean;
import me.blueyescat.skriptholo.hologram.Hologram;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;

@Name("Can See Hologram")
@Description("Checks whether the given players can see the given holograms. " +
		"Use the `Hologram Visibility` effect to hide/show holograms.")
@Examples({"if holo {_variable} is hidden from the player:",
		"if all players can see holograms {_list::*}:",
		"if hologram {_holo} is not visible for {_player}:",
		"if the player can see the holo {holograms::1}:"})
@Since("1.0.0")
public class CondCanSeeHologram extends Condition {

	public static void register(SyntaxRegistry registry) {
		registry.register(SyntaxRegistry.CONDITION, SyntaxInfo.builder(CondCanSeeHologram.class)
				.addPatterns(
						"[holo[gram][s]] %holograms% (is|are) (visible|1¦(invisible|hidden)) (for|to|from) %players%",
						"%players% can see [holo[gram][s]] %holograms%",
						"[holo[gram][s]] %holograms% (is|are)(n't| not) (visible|1¦(invisible|hidden)) (for|to|from) %players%",
						"%players% can('t| not) see [holo[gram][s]] %holograms%")
				.build());
	}

	private Expression<Player> players;
	private Expression<Hologram> holograms;

	@SuppressWarnings("unchecked")
	@Override
	public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult parseResult) {
		if (matchedPattern == 1 || matchedPattern == 3) {
			players = (Expression<Player>) exprs[0];
			holograms = (Expression<Hologram>) exprs[1];
		} else {
			players = (Expression<Player>) exprs[1];
			holograms = (Expression<Hologram>) exprs[0];
		}
		setNegated(matchedPattern > 1 ^ parseResult.mark == 1);
		return true;
	}

	@Override
	public boolean check(Event e) {
		return players.check(e, player ->
				holograms.check(e, holo ->
						holo.canSee(player)
				), isNegated());
	}

	@Override
	public String toString(@Nullable Event e, boolean debug) {
		return players.toString(e, debug) + " can " + (isNegated() ? "not " : "") + "see" + holograms.toString(e, debug);
	}

}
