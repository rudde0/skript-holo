package me.blueyescat.skriptholo.skript.effects;

import ch.njol.skript.Skript;
import ch.njol.skript.classes.Changer.ChangeMode;
import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Examples;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.lang.Effect;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.skript.lang.Variable;
import ch.njol.skript.util.Direction;
import ch.njol.util.Kleenean;
import ch.njol.util.coll.CollectionUtils;
import me.blueyescat.skriptholo.hologram.Hologram;
import me.blueyescat.skriptholo.hologram.HologramManager;
import me.blueyescat.skriptholo.util.Utils;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;

import java.util.Collections;
import java.util.List;

@Name("Create Hologram")
@Description({"Creates a new hologram.",
		"",
		"Besides the `store in %variable%`, the `Last Created Hologram` expression can be used to get the created hologram.",
		"A hologram that follows an entity is deleted when the entity dies or is removed, " +
		"and keeps following it when the entity is unloaded and loaded again with its chunk."})
@Examples({"create a new hologram with line \"test\" and store in {test}",
		"create new holo with lines \"line 1\", \"line 2\" and \"line 3\"",
		"create new hologram with line \"test\" that follows player offset by direction 2.3 meters above",
		"spawn a holo with click-able line \"Click\" that follows the spawned entity with offset (1.75 meters above and 1 meter infront)",
		"create a hologram with lines \"&bDiamond Armor\" and every diamond armor and store it in {holograms::1}",
		"spawn hologram with touchable line stone at {_location}"})
@Since("1.0.0")
public class EffCreateHologram extends Effect {

	public static void register(SyntaxRegistry registry) {
		registry.register(SyntaxRegistry.EFFECT, SyntaxInfo.builder(EffCreateHologram.class)
				.addPatterns(
						"(create|spawn) [a] [new] holo[gram] [with [((1¦click|2¦touch|3¦interact)[-]able|3¦interactive)] line[s] %-strings/itemtypes%] " +
								"[%direction% %location%] [and store [it] in [[the] variable] %-objects%]",
						"(create|spawn) [a] [new] holo[gram] [with [((1¦click|2¦touch|3¦interact)[-]able|3¦interactive)] line[s] %-strings/itemtypes%] " +
								"that follows %entity% [[with] offset [(of|by|in|to)] [[the] direction] %-directions%] [and store [it] in [[the] variable] %-objects%]")
				.build());
	}

	@Nullable
	private Expression<?> lines;
	private Expression<Location> location;
	private Expression<Entity> entity;
	@Nullable
	private Expression<Direction> offset;
	@Nullable
	private Variable<?> variable;
	private boolean isFollowing, interactive;

	@SuppressWarnings("unchecked")
	@Override
	public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult parseResult) {
		interactive = parseResult.mark != 0;
		lines = exprs[0];
		if (matchedPattern == 0) {
			if (exprs[2] == null) {
				Skript.error("A location is required to create a hologram");
				return false;
			}
			location = Direction.combine((Expression<Direction>) exprs[1], (Expression<Location>) exprs[2]);
		} else {
			isFollowing = true;
			entity = (Expression<Entity>) exprs[1];
			offset = (Expression<Direction>) exprs[2];
		}
		if (exprs[3] != null) {
			if (exprs[3] instanceof Variable<?> var) {
				variable = var;
			} else {
				Skript.error(exprs[3] + " is not a variable");
				return false;
			}
		}
		return true;
	}

	@Override
	protected void execute(Event e) {
		List<Object> contents = Collections.emptyList();
		if (lines != null) {
			Object[] values = lines.getArray(e);
			if (values.length == 0)
				return;
			contents = Utils.toLineContents(values);
		}

		Hologram holo;
		if (isFollowing) {
			Entity entity = this.entity.getSingle(e);
			if (entity == null)
				return;
			Direction[] offset = null;
			if (this.offset != null) {
				offset = this.offset.getArray(e);
				if (offset.length == 0)
					return;
			}
			holo = HologramManager.create(entity, offset, contents, interactive);
		} else {
			Location location = this.location.getSingle(e);
			if (location == null || location.getWorld() == null)
				return;
			holo = HologramManager.create(location, contents, interactive);
		}

		if (variable != null)
			variable.change(e, CollectionUtils.array(holo), variable.isList() ? ChangeMode.ADD : ChangeMode.SET);
	}

	@Override
	public String toString(@Nullable Event e, boolean debug) {
		String lines = this.lines == null ? "" : " with " + (interactive ? "interactive " : "") + "lines " + this.lines.toString(e, debug);
		String store = variable == null ? "" : " and store it in " + variable.toString(e, debug);
		if (!isFollowing)
			return "create a hologram" + lines + " " + location.toString(e, debug) + store;
		return "create a hologram" + lines + " that follows " + entity.toString(e, debug) +
				(offset != null ? " with offset " + offset.toString(e, debug) : "") + store;
	}

}
