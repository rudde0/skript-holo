package me.blueyescat.skriptholo.hologram;

import me.blueyescat.skriptholo.SkriptHolo;
import org.bukkit.Bukkit;

/** Keeps every hologram change on the main thread, where all of the hologram state lives. */
final class Sync {

	private Sync() {}

	/** Schedules the action on the main thread when called from another one. */
	static boolean defer(Runnable action) {
		if (Bukkit.isPrimaryThread())
			return false;
		Bukkit.getScheduler().runTask(SkriptHolo.getInstance(), action);
		return true;
	}

}
