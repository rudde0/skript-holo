package me.blueyescat.skriptholo.skript.events;

import ch.njol.skript.Skript;
import ch.njol.skript.lang.Literal;
import ch.njol.skript.lang.SkriptEvent;
import ch.njol.skript.lang.SkriptParser;
import eu.decentsoftware.holograms.api.holograms.Hologram;
import eu.decentsoftware.holograms.event.HologramClickEvent;
import me.blueyescat.skriptholo.SkriptHolo;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.skriptlang.skript.bukkit.lang.eventvalue.EventValue;
import org.skriptlang.skript.bukkit.lang.eventvalue.EventValueRegistry;

public class EvtHologramLineClick extends SkriptEvent {

	static {
		Skript.registerEvent("Hologram Line Click", EvtHologramLineClick.class, HologramClickEvent.class,
				"holo[gram] [line] click")
				.description("Called when a player clicks on a hologram line. " +
						"See the `Make Hologram Line Click-able` effect.")
				.examples("on hologram click:",
						"\tif event-hologram-line is \"test\":")
				.since("1.0.0");

		EventValueRegistry eventValues = SkriptHolo.getAddonInstance().registry(EventValueRegistry.class);
		eventValues.register(EventValue.builder(HologramClickEvent.class, Player.class)
			.getter(HologramClickEvent::getPlayer)
			.build());
		eventValues.register(EventValue.builder(HologramClickEvent.class, Hologram.class)
			.getter(HologramClickEvent::getHologram)
			.build());
	}

	@Override
	public boolean init(Literal<?>[] args, int matchedPattern, SkriptParser.ParseResult parser) {
		return true;
	}

	@Override
	public boolean check(Event e) {
		return e instanceof HologramClickEvent;
	}

	@Override
	public String toString(Event e, boolean debug) {
		return "hologram line click";
	}

}
