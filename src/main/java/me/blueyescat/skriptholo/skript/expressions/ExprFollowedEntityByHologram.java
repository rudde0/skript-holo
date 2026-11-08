package me.blueyescat.skriptholo.skript.expressions;

import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Examples;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.ExpressionType;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.util.Kleenean;
import me.blueyescat.skriptholo.hologram.Hologram;
import org.bukkit.entity.Entity;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;

import java.util.ArrayList;
import java.util.List;

@Name("Followed Entity of Hologram")
@Description("Returns the followed entity of a hologram. " +
		"Use the `Following Hologram` effect to make an exiting hologram start/stop following an entity.")
@Examples({"kill the followed entity of last hologram",
		"set {_entities::*} to the entities followed by holograms {_list::*}"})
@Since("1.0.0")
public class ExprFollowedEntityByHologram extends SimpleExpression<Entity> {

	public static void register(SyntaxRegistry registry) {
		registry.register(SyntaxRegistry.EXPRESSION, SyntaxInfo.Expression
				.builder(ExprFollowedEntityByHologram.class, Entity.class)
				.priority(SyntaxInfo.SIMPLE)
				.addPatterns(
						"[the] followed entit(y|ies) (of|by) [holo[gram][s]] %holograms%",
						"[the] entit(y|ies) followed by [holo[gram][s]] %holograms%")
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
	protected Entity[] get(Event e) {
		List<Entity> entities = new ArrayList<>();
		for (Hologram holo : holograms.getArray(e)) {
			Entity entity = holo.getFollowedEntity();
			if (entity != null)
				entities.add(entity);
		}
		return entities.toArray(new Entity[0]);
	}

	@Override
	public boolean isSingle() {
		return holograms.isSingle();
	}

	@Override
	public Class<? extends Entity> getReturnType() {
		return Entity.class;
	}

	@Override
	public String toString(@Nullable Event e, boolean debug) {
		return "the followed entity by " + holograms.toString(e, debug);
	}

}
