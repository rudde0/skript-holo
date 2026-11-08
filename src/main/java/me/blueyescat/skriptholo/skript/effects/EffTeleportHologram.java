package me.blueyescat.skriptholo.skript.effects;

import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Examples;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.lang.Effect;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.skript.util.Direction;
import ch.njol.util.Kleenean;
import me.blueyescat.skriptholo.hologram.Hologram;
import org.bukkit.Location;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;

@Name("Teleport Hologram")
@Description("Teleports a hologram to a specific location. Has no effect on a hologram that follows an entity.")
@Examples({"teleport event-holo to the player",
		"teleport holograms {_holograms:*} to {_location}"})
@Since("1.0.0")
public class EffTeleportHologram extends Effect {

	public static void register(SyntaxRegistry registry) {
		registry.register(SyntaxRegistry.EFFECT, SyntaxInfo.builder(EffTeleportHologram.class)
				.addPattern("teleport [holo[gram][s]] %holograms% (to|%direction%) %location%")
				.build());
	}

	private Expression<Hologram> holograms;
	private Expression<Location> location;

	@SuppressWarnings("unchecked")
	@Override
	public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult parseResult) {
		holograms = (Expression<Hologram>) exprs[0];
		location = Direction.combine((Expression<Direction>) exprs[1], (Expression<Location>) exprs[2]);
		return true;
	}

	@Override
	protected void execute(Event e) {
		Location location = this.location.getSingle(e);
		if (location == null)
			return;
		for (Hologram holo : holograms.getArray(e))
			holo.teleport(location);
	}

	@Override
	public String toString(@Nullable Event e, boolean debug) {
		return "teleport " + holograms.toString(e, debug) + " to " + location.toString(e, debug);
	}

}
