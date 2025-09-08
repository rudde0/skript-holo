package me.blueyescat.skriptholo.util;

import ch.njol.skript.util.Direction;
import eu.decentsoftware.holograms.api.holograms.Hologram;
import eu.decentsoftware.holograms.api.holograms.HologramLine;
import me.blueyescat.skriptholo.SkriptHolo;
import me.blueyescat.skriptholo.skript.effects.EffCreateHologram;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.util.*;

public class Utils {

	public static boolean hasPlugin(String name) {
		return Bukkit.getServer().getPluginManager().isPluginEnabled(name);
	}

	public static Plugin getPlugin(String name) {
		return Bukkit.getServer().getPluginManager().getPlugin(name);
	}

	public static List<HologramLine> getHologramLines(Hologram holo) {
		List<HologramLine> lines = new ArrayList<>();
		for (int l = 0; l < holo.getPage(0).getLines().size(); l++)
			lines.add(holo.getPage(0).getLine(l));
		return lines;
	}

	@SuppressWarnings("unchecked")
	public static void deleteHologram(Integer entityID, Hologram... holograms) {
		for (Hologram holo : holograms) {
			if (!holo.isDisabled())
				holo.delete();
			if (holo.equals(EffCreateHologram.lastCreated))
				EffCreateHologram.lastCreated = null;
			if (isFollowingHologram(holo)) {
				Iterator it;
				it = SkriptHolo.followingHologramsEntities.entrySet().iterator();
				while (it.hasNext()) {
					Map.Entry entry = (Map.Entry) it.next();
					List<Hologram> holoList = (List<Hologram>) entry.getValue();
					holoList.removeIf(holo2 -> holo2.equals(holo));
					if (holoList.isEmpty())
						it.remove();
				}
				if (entityID == null) {
					it = SkriptHolo.followingHolograms.entrySet().iterator();
					while (it.hasNext()) {
						Map.Entry entry = (Map.Entry) it.next();
						Map<Hologram, Direction[]> holoMap = (Map<Hologram, Direction[]>) entry.getValue();
						for (Object o2 : holoMap.entrySet()) {
							Map.Entry entry2 = (Map.Entry) o2;
							if (entry2.getKey().equals(holo)) {
								it.remove();
							}
						}
					}
				} else {
					SkriptHolo.followingHolograms.remove(entityID);
				}
			}
			SkriptHolo.followingHologramsList.remove(holo);
		}
	}

	public static void deleteHologram(Hologram... holograms) {
		deleteHologram(null, holograms);
	}

	public static void deleteFollowingHolograms(int entityID) {
		Map<Hologram, Direction[]> holoMap = SkriptHolo.followingHolograms.get(entityID);
		if (holoMap == null || holoMap.isEmpty())
			return;
		for (Map.Entry<Hologram, Direction[]> o : holoMap.entrySet()) {
            Hologram holo = o.getKey();
			Utils.deleteHologram(entityID, holo);
		}
	}

	public static void cleanFollowingHolograms() {
		for (Map.Entry<Entity, List<Hologram>> o : SkriptHolo.followingHologramsEntities.entrySet()) {
            Entity entity = o.getKey();
			if (!entity.isValid()) {
				for (Hologram holo : o.getValue()) {
					if (!holo.isDisabled())
						holo.delete();
					if (holo.equals(EffCreateHologram.lastCreated))
						EffCreateHologram.lastCreated = null;
					SkriptHolo.followingHologramsList.remove(holo);
				}
				SkriptHolo.followingHologramsEntities.remove(entity);
				SkriptHolo.followingHolograms.remove(entity.getEntityId());
			}
		}
	}

	public static Location offsetLocation(Location loc, Direction... directions) {
		for (Direction d : directions)
			loc = d.getRelative(loc);
		return loc;
	}

	public static void makeHologramStartFollowing(Hologram holo, Entity entity, Direction[] offset) {
		SkriptHolo.followingHologramsList.add(holo);

		Map<Hologram, Direction[]> holoMap;
		int entityID = entity.getEntityId();
		holoMap = SkriptHolo.followingHolograms.get(entityID);
		if (holoMap == null)
			holoMap = new HashMap<>();
		holoMap.put(holo, offset);
		SkriptHolo.followingHolograms.put(entityID, holoMap);

		List<Hologram> holoList;
		holoList = SkriptHolo.followingHologramsEntities.get(entity);
		if (holoList == null)
			holoList = new ArrayList<>();
		holoList.add(holo);
		SkriptHolo.followingHologramsEntities.put(entity, holoList);

		Location location = entity.getLocation().clone();
		if (holo.getLocation().getWorld() == location.getWorld())
			holo.setLocation(offset != null ? offsetLocation(location, offset) : location);
	}

	public static void makeHologramStopFollowing(Hologram holo) {
		Iterator<Map.Entry<Integer, Map<Hologram, Direction[]>>> it = SkriptHolo.followingHolograms.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<Integer, Map<Hologram, Direction[]>> entry = it.next();
			Map<Hologram, Direction[]> holoMap = entry.getValue();
			Iterator<Map.Entry<Hologram, Direction[]>> it2 = holoMap.entrySet().iterator();
			while (it2.hasNext()) {
				Hologram holo2 = it2.next().getKey();
				if (holo2.equals(holo)) {
                    it2.remove();
                }
			}

			it.remove();
		}

        for (Map.Entry<Entity, List<Hologram>> entry : SkriptHolo.followingHologramsEntities.entrySet()) {
            List<Hologram> holoList = entry.getValue();
            holoList.removeIf(holo2 -> holo2.equals(holo));
            if (holoList.isEmpty()) {
                it.remove();
            }
        }
		SkriptHolo.followingHologramsList.remove(holo);
	}

	public static boolean isFollowingHologram(Hologram holo) {
		return SkriptHolo.followingHologramsList.contains(holo);
	}

}