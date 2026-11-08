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
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;

@Name("Delete Hologram")
@Description({"Deletes a hologram. The hologram type has the delete changer already, but this effect exists for variables. " +
		"For example `delete {_holo}` would delete the variable but you can use `delete hologram {_holo}` to avoid that.",
		"",
		"When you delete a hologram that is stored in a variable, the hologram object " +
		"will still exist in the variable but will not be usable. You should delete the variable too in this case."})
@Examples({"delete holo {_var}",
		"remove holograms {_holograms::*}"})
@Since("1.0.0")
public class EffDeleteHologram extends Effect {

	public static void register(SyntaxRegistry registry) {
		registry.register(SyntaxRegistry.EFFECT, SyntaxInfo.builder(EffDeleteHologram.class)
				.addPattern("(delete|remove|clear) holo[gram][s] %holograms%")
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
	protected void execute(Event e) {
		for (Hologram holo : holograms.getArray(e))
			holo.delete();
	}

	@Override
	public String toString(@Nullable Event e, boolean debug) {
		return "delete " + holograms.toString(e, debug);
	}

}
