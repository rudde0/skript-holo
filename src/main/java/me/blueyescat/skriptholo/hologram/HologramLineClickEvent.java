package me.blueyescat.skriptholo.hologram;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

/** Called when a player clicks a click-able hologram line. */
public class HologramLineClickEvent extends PlayerEvent {

	private static final HandlerList HANDLERS = new HandlerList();

	private final HologramLine line;
	private final boolean leftClick;

	public HologramLineClickEvent(Player player, HologramLine line, boolean leftClick) {
		super(player);
		this.line = line;
		this.leftClick = leftClick;
	}

	public HologramLine getLine() {
		return line;
	}

	public Hologram getHologram() {
		return line.getParent();
	}

	public boolean isLeftClick() {
		return leftClick;
	}

	@Override
	public @NotNull HandlerList getHandlers() {
		return HANDLERS;
	}

	public static HandlerList getHandlerList() {
		return HANDLERS;
	}

}
