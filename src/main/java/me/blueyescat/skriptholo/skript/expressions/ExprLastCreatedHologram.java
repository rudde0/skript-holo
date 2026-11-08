package me.blueyescat.skriptholo.skript.expressions;

import ch.njol.skript.classes.Changer.ChangeMode;
import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Examples;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.util.Kleenean;
import ch.njol.util.coll.CollectionUtils;
import me.blueyescat.skriptholo.hologram.Hologram;
import me.blueyescat.skriptholo.hologram.HologramManager;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;

@Name("Last Created Hologram")
@Description({"Holds the hologram that was created most recently with the `Create Hologram` effect.",
		"Can be deleted using the `delete/clear` changer which means the hologram will be " +
		"removed from the world and this expression will be empty."})
@Examples("set {_holo} to the created hologram")
@Since("1.0.0")
public class ExprLastCreatedHologram extends SimpleExpression<Hologram> {

	public static void register(SyntaxRegistry registry) {
		registry.register(SyntaxRegistry.EXPRESSION, SyntaxInfo.Expression
				.builder(ExprLastCreatedHologram.class, Hologram.class)
				.priority(SyntaxInfo.SIMPLE)
				.addPattern("[the] [last[ly]] [(created|spawned)] holo[gram]")
				.build());
	}

	@Override
	public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult parseResult) {
		return true;
	}

	@Override
	protected Hologram[] get(Event e) {
		Hologram holo = HologramManager.getLastCreated();
		return holo == null ? new Hologram[0] : new Hologram[] {holo};
	}

	@Override
	public Class<?>[] acceptChange(ChangeMode mode) {
		if (mode == ChangeMode.DELETE || mode == ChangeMode.RESET)
			return CollectionUtils.array();
		return null;
	}

	@Override
	public void change(Event e, @Nullable Object[] delta, ChangeMode mode) {
		Hologram holo = HologramManager.getLastCreated();
		if (holo != null)
			holo.delete();
	}

	@Override
	public boolean isSingle() {
		return true;
	}

	@Override
	public Class<? extends Hologram> getReturnType() {
		return Hologram.class;
	}

	@Override
	public String toString(@Nullable Event e, boolean debug) {
		return "the last created hologram";
	}

}
