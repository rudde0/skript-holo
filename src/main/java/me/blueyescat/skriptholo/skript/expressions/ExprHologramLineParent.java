package me.blueyescat.skriptholo.skript.expressions;

import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Examples;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.util.Kleenean;
import me.blueyescat.skriptholo.hologram.Hologram;
import me.blueyescat.skriptholo.hologram.HologramLine;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;

import java.util.ArrayList;
import java.util.List;

@Name("Parent Hologram of Hologram Line")
@Description("Returns the parent hologram of a hologram line.")
@Examples("set {_holo} to parent hologram of {_line}")
@Since("1.0.0")
public class ExprHologramLineParent extends SimpleExpression<Hologram> {

	public static void register(SyntaxRegistry registry) {
		registry.register(SyntaxRegistry.EXPRESSION, SyntaxInfo.Expression
				.builder(ExprHologramLineParent.class, Hologram.class)
				.priority(SyntaxInfo.SIMPLE)
				.addPatterns(
						"[the] [parent[s]] holo[gram][s] of [holo[gram] line[s]] %hologramlines%",
						"%hologramlines%'[s] [parent[s]] holo[gram][s]")
				.build());
	}

	private Expression<HologramLine> lines;

	@SuppressWarnings("unchecked")
	@Override
	public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult parseResult) {
		lines = (Expression<HologramLine>) exprs[0];
		return true;
	}

	@Override
	protected Hologram[] get(Event e) {
		List<Hologram> holograms = new ArrayList<>();
		for (HologramLine line : lines.getArray(e))
			holograms.add(line.getParent());
		return holograms.toArray(new Hologram[0]);
	}

	@Override
	public boolean isSingle() {
		return lines.isSingle();
	}

	@Override
	public Class<? extends Hologram> getReturnType() {
		return Hologram.class;
	}

	@Override
	public String toString(@Nullable Event e, boolean debug) {
		return "the parent hologram of " + lines.toString(e, debug);
	}

}
