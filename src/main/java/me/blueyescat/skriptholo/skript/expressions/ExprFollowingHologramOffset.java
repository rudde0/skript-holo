package me.blueyescat.skriptholo.skript.expressions;

import ch.njol.skript.classes.Changer.ChangeMode;
import ch.njol.skript.doc.*;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.ExpressionType;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.skript.util.Direction;
import ch.njol.util.Kleenean;
import ch.njol.util.coll.CollectionUtils;
import me.blueyescat.skriptholo.hologram.Hologram;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Name("Following Hologram Offset")
@Description("Returns offset (directions) of a following hologram. Can be set.")
@Examples({"delete offset of last holo",
		"set following offset of {_hologram} to 0.5 meters east and 2.5 meters above"})
@Since("1.0.0")
public class ExprFollowingHologramOffset extends SimpleExpression<Direction> {

	public static void register(SyntaxRegistry registry) {
		registry.register(SyntaxRegistry.EXPRESSION, SyntaxInfo.Expression
				.builder(ExprFollowingHologramOffset.class, Direction.class)
				.priority(SyntaxInfo.SIMPLE)
				.addPatterns(
						"[the] [following] offset[s] of [holo[gram][s]] %holograms%",
						"%holograms%'[s] [following] offset[s]")
				.build());
	}

	private Expression<Hologram> holograms;

	@SuppressWarnings("unchecked")
	@Override
	public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult parseResult) {
		holograms = (Expression<Hologram>) exprs[0];
		return true;
	}

	@Override
	protected Direction[] get(Event e) {
		List<Direction> offsets = new ArrayList<>();
		for (Hologram holo : holograms.getArray(e))
			offsets.addAll(Arrays.asList(holo.getFollowOffset()));
		return offsets.toArray(new Direction[0]);
	}

	@Override
	public Class<?>[] acceptChange(ChangeMode mode) {
		if (mode == ChangeMode.SET || mode == ChangeMode.DELETE || mode == ChangeMode.RESET)
			return CollectionUtils.array(Direction[].class);
		return null;
	}

	@Override
	public void change(Event e, @Nullable Object[] delta, ChangeMode mode) {
		Direction[] offset = null;
		if (mode == ChangeMode.SET && delta != null) {
			offset = new Direction[delta.length];
			for (int i = 0; i < delta.length; i++)
				offset[i] = (Direction) delta[i];
		}
		for (Hologram holo : holograms.getArray(e))
			holo.setFollowOffset(offset);
	}

	@Override
	public boolean isSingle() {
		return false;
	}

	@Override
	public Class<? extends Direction> getReturnType() {
		return Direction.class;
	}

	@Override
	public String toString(@Nullable Event e, boolean debug) {
		return "the offset of " + holograms.toString(e, debug);
	}

}
