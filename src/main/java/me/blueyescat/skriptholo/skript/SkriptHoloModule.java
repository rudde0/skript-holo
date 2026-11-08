package me.blueyescat.skriptholo.skript;

import me.blueyescat.skriptholo.skript.conditions.CondCanSeeHologram;
import me.blueyescat.skriptholo.skript.conditions.CondIsFollowingHologram;
import me.blueyescat.skriptholo.skript.conditions.CondIsHologramDeleted;
import me.blueyescat.skriptholo.skript.conditions.CondIsHologramLineTextItem;
import me.blueyescat.skriptholo.skript.conditions.CondIsHologramVisibleByDefault;
import me.blueyescat.skriptholo.skript.effects.EffAddHologramLine;
import me.blueyescat.skriptholo.skript.effects.EffCreateHologram;
import me.blueyescat.skriptholo.skript.effects.EffDeleteHologram;
import me.blueyescat.skriptholo.skript.effects.EffFollowingHologram;
import me.blueyescat.skriptholo.skript.effects.EffHologramVisibility;
import me.blueyescat.skriptholo.skript.effects.EffTeleportHologram;
import me.blueyescat.skriptholo.skript.events.EvtHologramLineClick;
import me.blueyescat.skriptholo.skript.expressions.ExprEntityFollowingHolograms;
import me.blueyescat.skriptholo.skript.expressions.ExprFollowedEntityByHologram;
import me.blueyescat.skriptholo.skript.expressions.ExprFollowingHologramOffset;
import me.blueyescat.skriptholo.skript.expressions.ExprHologramHeight;
import me.blueyescat.skriptholo.skript.expressions.ExprHologramLineContent;
import me.blueyescat.skriptholo.skript.expressions.ExprHologramLineNumber;
import me.blueyescat.skriptholo.skript.expressions.ExprHologramLineParent;
import me.blueyescat.skriptholo.skript.expressions.ExprHologramLines;
import me.blueyescat.skriptholo.skript.expressions.ExprLastCreatedHologram;
import org.skriptlang.skript.addon.AddonModule;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.registration.SyntaxRegistry;

/**
 * Registers every syntax of the addon.
 * <p>
 * This replaces the old package scan: the syntax classes no longer register themselves from a
 * static initializer, so the load order is explicit and nothing depends on class loading order.
 */
public class SkriptHoloModule implements AddonModule {

	@Override
	public String name() {
		return "skript-holo";
	}

	@Override
	public void load(SkriptAddon addon) {
		// The types have to exist before anything that refers to them in a pattern.
		Types.register();

		SyntaxRegistry registry = moduleRegistry(addon);

		EvtHologramLineClick.register(addon, registry);

		EffAddHologramLine.register(registry);
		EffCreateHologram.register(registry);
		EffDeleteHologram.register(registry);
		EffFollowingHologram.register(registry);
		EffHologramVisibility.register(registry);
		EffTeleportHologram.register(registry);

		CondCanSeeHologram.register(registry);
		CondIsFollowingHologram.register(registry);
		CondIsHologramDeleted.register(registry);
		CondIsHologramLineTextItem.register(registry);
		CondIsHologramVisibleByDefault.register(registry);

		ExprEntityFollowingHolograms.register(registry);
		ExprFollowedEntityByHologram.register(registry);
		ExprFollowingHologramOffset.register(registry);
		ExprHologramHeight.register(registry);
		ExprHologramLineContent.register(registry);
		ExprHologramLineNumber.register(registry);
		ExprHologramLineParent.register(registry);
		ExprHologramLines.register(registry);
		ExprLastCreatedHologram.register(registry);
	}

}
