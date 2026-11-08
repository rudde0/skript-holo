package me.blueyescat.skriptholo;

import ch.njol.skript.Skript;
import com.github.retrooper.packetevents.PacketEvents;
import me.blueyescat.skriptholo.hologram.HologramManager;
import me.blueyescat.skriptholo.skript.SkriptHoloModule;
import me.blueyescat.skriptholo.util.Metrics;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.skriptlang.skript.addon.SkriptAddon;

public class SkriptHolo extends JavaPlugin {

	private static SkriptHolo instance;
	private static SkriptAddon addonInstance;

	public SkriptHolo() {
		if (instance == null)
			instance = this;
		else
			throw new IllegalStateException();
	}

	@Override
	public void onEnable() {
		if (!Skript.isAcceptRegistrations()) {
			getServer().getPluginManager().disablePlugin(this);
			getLogger().severe("skript-holo can't be loaded when the server is already loaded! Plugin is disabled.");
			return;
		}
		if (PacketEvents.getAPI() == null || !PacketEvents.getAPI().isLoaded()) {
			getServer().getPluginManager().disablePlugin(this);
			getLogger().severe("skript-holo needs PacketEvents to be installed and enabled! Plugin is disabled.");
			return;
		}

		HologramManager.enable(this);

		addonInstance = Skript.instance().registerAddon(SkriptHolo.class, "skript-holo");
		addonInstance.localizer().setSourceDirectories("lang", getDataFolder().getAbsolutePath() + "/lang");
		addonInstance.loadModules(new SkriptHoloModule());

		startMetrics();
		getLogger().info("Finished loading!");
	}

	@Override
	public void onDisable() {
		HologramManager.disable();
	}

	private void startMetrics() {
		Metrics metrics = new Metrics(this);
		metrics.addCustomChart(new Metrics.SimplePie("skript_version", () ->
				Skript.getVersion().toString()));
		metrics.addCustomChart(new Metrics.SimplePie("packetevents_version", () -> {
			Plugin packetEvents = getServer().getPluginManager().getPlugin("packetevents");
			return packetEvents == null ? "unknown" : packetEvents.getPluginMeta().getVersion();
		}));
	}

	public static SkriptAddon getAddonInstance() {
		if (addonInstance == null)
			throw new IllegalStateException("SkriptHolo addon is not registered yet");
		return addonInstance;
	}

	public static SkriptHolo getInstance() {
		if (instance == null)
			throw new IllegalStateException();
		return instance;
	}

}
