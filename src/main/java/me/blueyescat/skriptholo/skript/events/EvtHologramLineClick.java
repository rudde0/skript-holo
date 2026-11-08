package me.blueyescat.skriptholo.skript.events;

import ch.njol.skript.lang.Literal;
import ch.njol.skript.lang.SkriptEvent;
import ch.njol.skript.lang.SkriptParser;
import me.blueyescat.skriptholo.hologram.Hologram;
import me.blueyescat.skriptholo.hologram.HologramLine;
import me.blueyescat.skriptholo.hologram.HologramLineClickEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.addon.SkriptAddon;
import org.skriptlang.skript.bukkit.lang.eventvalue.EventValue;
import org.skriptlang.skript.bukkit.lang.eventvalue.EventValueRegistry;
import org.skriptlang.skript.bukkit.registration.BukkitSyntaxInfos;
import org.skriptlang.skript.registration.SyntaxRegistry;

public class EvtHologramLineClick extends SkriptEvent {

	public static void register(SkriptAddon addon, SyntaxRegistry registry) {
		registry.register(BukkitSyntaxInfos.Event.KEY, BukkitSyntaxInfos.Event
				.builder(EvtHologramLineClick.class, "Hologram Line Click")
				.addEvent(HologramLineClickEvent.class)
				// fixPattern applies the same transformation the old registerEvent did.
				.addPattern(BukkitSyntaxInfos.fixPattern("holo[gram] [line] [(1¦left|2¦right)] click"))
				.addDescription("Called when a player clicks on a click-able, touchable or interactive hologram line. " +
						"See the `Add Hologram Line` and `Create Hologram` effects.")
				.addExamples("on hologram click:",
						"\tif event-hologram-line is \"test\":",
						"on hologram right click:")
				.addSince("1.0.0")
				.build());

		EventValueRegistry eventValues = addon.registry(EventValueRegistry.class);
		eventValues.register(EventValue.builder(HologramLineClickEvent.class, Player.class)
				.getter(HologramLineClickEvent::getPlayer)
				.build());
		eventValues.register(EventValue.builder(HologramLineClickEvent.class, Hologram.class)
				.getter(HologramLineClickEvent::getHologram)
				.build());
		eventValues.register(EventValue.builder(HologramLineClickEvent.class, HologramLine.class)
				.getter(HologramLineClickEvent::getLine)
				.build());
	}

	/** 0 for any click, 1 for left clicks, 2 for right clicks. */
	private int click;

	@Override
	public boolean init(Literal<?>[] args, int matchedPattern, SkriptParser.ParseResult parser) {
		click = parser.mark;
		return true;
	}

	@Override
	public boolean check(Event e) {
		if (!(e instanceof HologramLineClickEvent event))
			return false;
		return click == 0 || (click == 1) == event.isLeftClick();
	}

	@Override
	public String toString(@Nullable Event e, boolean debug) {
		return "hologram line " + (click == 1 ? "left " : click == 2 ? "right " : "") + "click";
	}

}
