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
import org.bukkit.entity.Entity;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;

@Name("Following Hologram")
@Description({"Makes a hologram start/stop following an entity.",
		"With a vertical offset (or none) the hologram rides the entity, so it moves perfectly smooth and costs " +
		"nothing on the server. Other offsets, or click-able lines, move the hologram every tick."})
@Examples({"set {_directions::*} to 1.5 meters horizontally infront, 1 meter above and 0.5 meters right",
		"make the last created hologram follow player with offset {_directions::*}",
		"",
		"make all holograms stop following",
		"let hologram {variable} start following event-entity"})
@Since("1.0.0")
public class EffFollowingHologram extends Effect {

	public static void register(SyntaxRegistry registry) {
		registry.register(SyntaxRegistry.EFFECT, SyntaxInfo.builder(EffFollowingHologram.class)
				.addPatterns(
						"(make|let) [holo[gram][s]] %holograms% (start following|follow) %entity% [[with] offset [(of|by|in|to)] [[the] direction] %-directions%]",
						"(make|let) [holo[gram][s]] %holograms% (stop following|unfollow)")
				.build());
	}

	private Expression<Hologram> holograms;
	@Nullable
	private Expression<Entity> entity;
	@Nullable
	private Expression<Direction> offset;
	private boolean negative;

	@SuppressWarnings("unchecked")
	@Override
	public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult parseResult) {
		holograms = (Expression<Hologram>) exprs[0];
		negative = matchedPattern == 1;
		if (!negative) {
			entity = (Expression<Entity>) exprs[1];
			offset = (Expression<Direction>) exprs[2];
		}
		return true;
	}

	@Override
	protected void execute(Event e) {
		if (negative) {
			for (Hologram holo : holograms.getArray(e))
				holo.unfollow();
			return;
		}
		assert entity != null;
		Entity entity = this.entity.getSingle(e);
		if (entity == null)
			return;
		Direction[] offset = this.offset != null ? this.offset.getArray(e) : null;
		for (Hologram holo : holograms.getArray(e))
			holo.follow(entity, offset);
	}

	@Override
	public String toString(@Nullable Event e, boolean debug) {
		if (negative)
			return "make " + holograms.toString(e, debug) + " stop following";
		assert entity != null;
		return "make " + holograms.toString(e, debug) + " start following " + entity.toString(e, debug) +
				(offset != null ? " with offset " + offset.toString(e, debug) : "");
	}

}
