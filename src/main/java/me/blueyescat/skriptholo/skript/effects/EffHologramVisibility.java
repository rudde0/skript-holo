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
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;

@Name("Hologram Visibility")
@Description("Changes visibility of a hologram for the given players or makes a hologram invisible/visible by default. " +
		"Resetting hologram visibility means setting the visibility to the default value (visible if not changed).")
@Examples({"hide the last created hologram from the player",
		"reveal holos {holograms::*} to {_players::*}",
		"reset the visibility of all holograms",
		"make the last holo invisible by default",
		"make all holograms visible by default"})
@Since("1.0.0")
public class EffHologramVisibility extends Effect {

	public static void register(SyntaxRegistry registry) {
		registry.register(SyntaxRegistry.EFFECT, SyntaxInfo.builder(EffHologramVisibility.class)
				.addPatterns(
						"hide [holo[gram][s]] %holograms% (from|for) %players%",
						"reveal [holo[gram][s]] %holograms% (to|for|from) %players%",
						"reset [the] visibilit(y|ies) of [holo[gram][s]] %holograms% [for %-players%]",
						"(make|let) [holo[gram][s]] %holograms% (invisible|hidden) by default",
						"(make|let) [holo[gram][s]] %holograms% visible by default")
				.build());
	}

	private enum Mode {
		HIDE, REVEAL, RESET, RESET_ALL, DEFAULT_INVISIBLE, DEFAULT_VISIBLE
	}

	private Expression<Hologram> holograms;
	@Nullable
	private Expression<Player> players;
	private Mode mode;

	@SuppressWarnings("unchecked")
	@Override
	public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult parseResult) {
		mode = switch (matchedPattern) {
			case 0 -> Mode.HIDE;
			case 1 -> Mode.REVEAL;
			case 2 -> exprs[1] == null ? Mode.RESET_ALL : Mode.RESET;
			case 3 -> Mode.DEFAULT_INVISIBLE;
			default -> Mode.DEFAULT_VISIBLE;
		};
		holograms = (Expression<Hologram>) exprs[0];
		if (matchedPattern < 3)
			players = (Expression<Player>) exprs[1];
		return true;
	}

	@Override
	protected void execute(Event e) {
		Hologram[] targets = holograms.getArray(e);
		switch (mode) {
			case HIDE, REVEAL, RESET -> {
				assert players != null;
				for (Player player : players.getArray(e)) {
					for (Hologram holo : targets) {
						if (mode == Mode.REVEAL)
							holo.show(player);
						else if (mode == Mode.HIDE)
							holo.hide(player);
						else
							holo.resetVisibility(player);
					}
				}
			}
			case RESET_ALL -> {
				for (Hologram holo : targets)
					holo.resetVisibility();
			}
			case DEFAULT_INVISIBLE, DEFAULT_VISIBLE -> {
				for (Hologram holo : targets)
					holo.setVisibleByDefault(mode == Mode.DEFAULT_VISIBLE);
			}
		}
	}

	@Override
	public String toString(@Nullable Event e, boolean debug) {
		return switch (mode) {
			case HIDE -> "hide " + holograms.toString(e, debug) + " from " + players.toString(e, debug);
			case REVEAL -> "show " + holograms.toString(e, debug) + " to " + players.toString(e, debug);
			case RESET -> "reset visibility of " + holograms.toString(e, debug) + " for " + players.toString(e, debug);
			case RESET_ALL -> "reset visibility of " + holograms.toString(e, debug);
			case DEFAULT_INVISIBLE -> "make " + holograms.toString(e, debug) + " invisible by default";
			case DEFAULT_VISIBLE -> "make " + holograms.toString(e, debug) + " visible by default";
		};
	}

}
