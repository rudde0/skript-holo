package me.blueyescat.skriptholo.skript.expressions;

import ch.njol.skript.doc.*;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.ExpressionType;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.util.Kleenean;
import me.blueyescat.skriptholo.hologram.Hologram;
import me.blueyescat.skriptholo.hologram.HologramManager;
import org.bukkit.entity.Entity;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;

import java.util.ArrayList;
import java.util.List;

@Name("Following Holograms of Entity")
@Description("Returns the following holograms of an entity. " +
		"Use the `Following Hologram` effect to make an exiting hologram start/stop following an entity.")
@Examples({"delete the following holograms of targeted entity",
		"set {_holograms::*} to all holograms that follows the player"})
@Since("1.0.0")
public class ExprEntityFollowingHolograms extends SimpleExpression<Hologram> {

	public static void register(SyntaxRegistry registry) {
		registry.register(SyntaxRegistry.EXPRESSION, SyntaxInfo.Expression
				.builder(ExprEntityFollowingHolograms.class, Hologram.class)
				.priority(SyntaxInfo.SIMPLE)
				.addPatterns(
						"[all] [the] following holo[gram]s of %entities%",
						"[all] [the] holo[gram]s (following|that follows) %entities%")
				.build());
	}

	private Expression<Entity> entities;

	@SuppressWarnings("unchecked")
	@Override
	public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult parseResult) {
		entities = (Expression<Entity>) exprs[0];
		return true;
	}

	@Override
	protected Hologram[] get(Event e) {
		List<Hologram> holograms = new ArrayList<>();
		for (Entity entity : entities.getArray(e))
			holograms.addAll(HologramManager.getFollowers(entity));
		return holograms.toArray(new Hologram[0]);
	}

	@Override
	public boolean isSingle() {
		return false;
	}

	@Override
	public Class<? extends Hologram> getReturnType() {
		return Hologram.class;
	}

	@Override
	public String toString(@Nullable Event e, boolean debug) {
		return "the holograms following " + entities.toString(e, debug);
	}

}
