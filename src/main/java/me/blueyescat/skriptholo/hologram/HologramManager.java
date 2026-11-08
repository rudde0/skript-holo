package me.blueyescat.skriptholo.hologram;

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import com.destroystokyo.paper.event.player.PlayerPostRespawnEvent;
import com.destroystokyo.paper.event.player.PlayerUseUnknownEntityEvent;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerCommon;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetPassengers;
import io.papermc.paper.event.packet.PlayerChunkLoadEvent;
import io.papermc.paper.event.packet.PlayerChunkUnloadEvent;
import io.papermc.paper.event.player.PlayerTrackEntityEvent;
import io.papermc.paper.event.player.PlayerUntrackEntityEvent;
import me.blueyescat.skriptholo.SkriptHolo;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns every hologram and decides, from Paper's own events, who has to receive which hologram.
 * <p>
 * Nothing here polls players or scans holograms: chunk send/unload events drive the standing
 * holograms, entity track/untrack events drive the riding ones, and every lookup is a map access.
 * Everything is main thread only, except {@link #PASSENGERS} which is read from the netty threads.
 */
public final class HologramManager {

	/** How often the followers are checked for a vanished entity or a resized one, in ticks. */
	private static final long SWEEP_PERIOD = 100;

	private static final Set<Hologram> ALL = new LinkedHashSet<>();
	/** Standing holograms by world and chunk. */
	private static final Map<UUID, Map<Long, List<Hologram>>> INDEX = new HashMap<>();
	/** Following holograms by the uuid of their entity, which survives the entity being reloaded. */
	private static final Map<UUID, List<Hologram>> FOLLOWERS = new HashMap<>();
	/** Display ids riding each entity id, appended to the entity's own passenger packets. */
	private static final Map<Integer, int[]> PASSENGERS = new ConcurrentHashMap<>();
	private static final Map<Integer, HologramLine> INTERACTIONS = new HashMap<>();
	/** Holograms each player currently has, so leaving or changing world is cheap to clean up. */
	private static final Map<UUID, Set<Hologram>> VIEWING = new HashMap<>();
	/** Following holograms that can not ride their entity and are moved every tick. */
	private static final Set<Hologram> TICKING = new LinkedHashSet<>();
	/** Players that started tracking a followed entity this tick, spawned on the next tick. */
	private static final Map<Player, Set<Entity>> PENDING_TRACKS = new HashMap<>();

	@Nullable
	private static Hologram lastCreated;
	@Nullable
	private static BukkitTask tickTask;
	@Nullable
	private static BukkitTask sweepTask;
	private static boolean pendingFlushScheduled;
	@Nullable
	private static Listener listener;
	@Nullable
	private static PacketListenerCommon packetListener;

	private HologramManager() {}

	// ------------------------------------------------------------------------------------------
	// Lifecycle
	// ------------------------------------------------------------------------------------------

	public static void enable(SkriptHolo plugin) {
		listener = new Events();
		Bukkit.getPluginManager().registerEvents(listener, plugin);
		packetListener = PacketEvents.getAPI().getEventManager().registerListener(new PassengerListener());
		sweepTask = Bukkit.getScheduler().runTaskTimer(plugin, HologramManager::sweep, SWEEP_PERIOD, SWEEP_PERIOD);
	}

	public static void disable() {
		for (Hologram holo : new ArrayList<>(ALL))
			holo.delete();
		if (listener != null)
			HandlerList.unregisterAll(listener);
		listener = null;
		if (packetListener != null) {
			try {
				PacketEvents.getAPI().getEventManager().unregisterListener(packetListener);
			} catch (Throwable ignored) {
				// PacketEvents may already be disabled while the server stops.
			}
		}
		packetListener = null;
		if (sweepTask != null)
			sweepTask.cancel();
		sweepTask = null;
		stopTicking();
		ALL.clear();
		INDEX.clear();
		FOLLOWERS.clear();
		PASSENGERS.clear();
		INTERACTIONS.clear();
		VIEWING.clear();
		TICKING.clear();
		PENDING_TRACKS.clear();
		lastCreated = null;
	}

	// ------------------------------------------------------------------------------------------
	// Creation
	// ------------------------------------------------------------------------------------------

	/** Creates a standing hologram and shows it right away. */
	public static Hologram create(Location location, List<?> contents, boolean interactive) {
		Hologram holo = register(new Hologram(location), contents, interactive);
		index(holo);
		holo.refreshViewers();
		return holo;
	}

	/** Creates a hologram that follows an entity, it is only spawned once, already following. */
	public static Hologram create(Entity entity, @Nullable ch.njol.skript.util.Direction[] offset,
								  List<?> contents, boolean interactive) {
		Hologram holo = register(new Hologram(entity.getLocation()), contents, interactive);
		holo.follow(entity, offset);
		return holo;
	}

	private static Hologram register(Hologram holo, List<?> contents, boolean interactive) {
		for (Object content : contents) {
			HologramLine line = new HologramLine(holo, content, interactive);
			holo.lines.add(line);
			lineAdded(holo, line);
		}
		holo.layout();
		ALL.add(holo);
		lastCreated = holo;
		return holo;
	}

	@Nullable
	public static Hologram getLastCreated() {
		return lastCreated;
	}

	public static Set<Hologram> getAll() {
		return Collections.unmodifiableSet(ALL);
	}

	static void unregister(Hologram holo) {
		ALL.remove(holo);
		unindex(holo);
		if (lastCreated == holo)
			lastCreated = null;
	}

	/** The holograms following the given entity, in no particular order. */
	public static List<Hologram> getFollowers(Entity entity) {
		List<Hologram> followers = FOLLOWERS.get(entity.getUniqueId());
		return followers == null ? Collections.emptyList() : new ArrayList<>(followers);
	}

	// ------------------------------------------------------------------------------------------
	// Bookkeeping, called by Hologram
	// ------------------------------------------------------------------------------------------

	static void index(Hologram holo) {
		unindex(holo);
		if (holo.isDeleted() || holo.follow != null && holo.follow.passenger)
			return;
		World world = holo.getWorld();
		if (world == null)
			return;
		long chunk = holo.getChunkKey();
		INDEX.computeIfAbsent(world.getUID(), k -> new HashMap<>())
				.computeIfAbsent(chunk, k -> new ArrayList<>(2))
				.add(holo);
		holo.indexedWorld = world.getUID();
		holo.indexedChunk = chunk;
	}

	static void unindex(Hologram holo) {
		UUID world = holo.indexedWorld;
		if (world == null)
			return;
		holo.indexedWorld = null;
		Map<Long, List<Hologram>> chunks = INDEX.get(world);
		if (chunks == null)
			return;
		List<Hologram> holograms = chunks.get(holo.indexedChunk);
		if (holograms == null)
			return;
		holograms.remove(holo);
		if (holograms.isEmpty()) {
			chunks.remove(holo.indexedChunk);
			if (chunks.isEmpty())
				INDEX.remove(world);
		}
	}

	static void registerFollower(Hologram holo) {
		Hologram.Follow follow = holo.follow;
		if (follow == null)
			return;
		FOLLOWERS.computeIfAbsent(follow.uuid, k -> new ArrayList<>(1)).add(holo);
		if (follow.passenger) {
			updatePassengers(follow.entity);
		} else {
			TICKING.add(holo);
			startTicking();
		}
	}

	static void unregisterFollower(Hologram holo) {
		Hologram.Follow follow = holo.follow;
		if (follow == null)
			return;
		List<Hologram> followers = FOLLOWERS.get(follow.uuid);
		if (followers != null) {
			followers.remove(holo);
			if (followers.isEmpty())
				FOLLOWERS.remove(follow.uuid);
		}
		if (follow.passenger)
			updatePassengers(follow.entity);
		if (TICKING.remove(holo) && TICKING.isEmpty())
			stopTicking();
		unindex(holo);
	}

	/** Recomputes the risers riding an entity, one per hologram whatever its line count. */
	private static void updatePassengers(Entity vehicle) {
		List<Hologram> followers = FOLLOWERS.get(vehicle.getUniqueId());
		int count = 0;
		if (followers != null) {
			for (Hologram holo : followers) {
				if (holo.follow != null && holo.follow.passenger && holo.follow.entity == vehicle)
					count++;
			}
		}
		if (count == 0) {
			PASSENGERS.remove(vehicle.getEntityId());
			return;
		}
		int[] ids = new int[count];
		int i = 0;
		for (Hologram holo : followers) {
			if (holo.follow != null && holo.follow.passenger && holo.follow.entity == vehicle)
				ids[i++] = holo.riserId;
		}
		PASSENGERS.put(vehicle.getEntityId(), ids);
	}

	/** Real passengers of the entity followed by every display riding it. */
	static int[] passengersOf(Entity vehicle) {
		int[] displays = PASSENGERS.get(vehicle.getEntityId());
		if (displays == null)
			displays = new int[0];
		List<Entity> real = vehicle.getPassengers();
		if (real.isEmpty())
			return displays;
		int[] all = new int[real.size() + displays.length];
		for (int i = 0; i < real.size(); i++)
			all[i] = real.get(i).getEntityId();
		System.arraycopy(displays, 0, all, real.size(), displays.length);
		return all;
	}

	static void lineAdded(Hologram holo, HologramLine line) {
		if (line.isInteractive())
			INTERACTIONS.put(line.interactionId, line);
	}

	static void lineRemoved(Hologram holo, HologramLine line) {
		if (line.isInteractive())
			INTERACTIONS.remove(line.interactionId);
	}

	static void view(Player player, Hologram holo) {
		VIEWING.computeIfAbsent(player.getUniqueId(), k -> new HashSet<>()).add(holo);
	}

	static void unview(Player player, Hologram holo) {
		Set<Hologram> holograms = VIEWING.get(player.getUniqueId());
		if (holograms != null && holograms.remove(holo) && holograms.isEmpty())
			VIEWING.remove(player.getUniqueId());
	}

	/** The client of the player dropped every entity, forget what they had. */
	private static void forgetAll(Player player) {
		Set<Hologram> holograms = VIEWING.remove(player.getUniqueId());
		if (holograms == null)
			return;
		for (Hologram holo : holograms)
			holo.forgetViewer(player);
	}

	// ------------------------------------------------------------------------------------------
	// Tasks
	// ------------------------------------------------------------------------------------------

	private static void startTicking() {
		if (tickTask == null)
			tickTask = Bukkit.getScheduler().runTaskTimer(SkriptHolo.getInstance(), HologramManager::tick, 1, 1);
	}

	private static void stopTicking() {
		if (tickTask != null)
			tickTask.cancel();
		tickTask = null;
	}

	private static void tick() {
		if (TICKING.isEmpty()) {
			stopTicking();
			return;
		}
		for (Hologram holo : TICKING.toArray(new Hologram[0]))
			holo.tickFollow();
	}

	/**
	 * Safety net behind the entity events: deletes the holograms whose entity is gone without an
	 * event, binds the ones whose entity came back, and adjusts the riding ones to the entity size.
	 */
	private static void sweep() {
		if (FOLLOWERS.isEmpty())
			return;
		List<Hologram> followers = new ArrayList<>();
		for (List<Hologram> list : FOLLOWERS.values())
			followers.addAll(list);

		for (Hologram holo : followers) {
			Hologram.Follow follow = holo.follow;
			if (follow == null || holo.isDeleted())
				continue;
			Entity entity = follow.entity;
			if (entity instanceof Player player) {
				if (!player.isOnline() && Bukkit.getPlayer(follow.uuid) == null)
					holo.delete();
				else
					holo.checkVehicleSize();
				continue;
			}
			if (entity.isValid()) {
				holo.checkVehicleSize();
				continue;
			}
			// Still in the world: dying (death animation) or in an unloaded chunk.
			if (entity.isInWorld() && !follow.unloaded)
				continue;
			Entity current = Bukkit.getEntity(follow.uuid);
			if (current != null && current != entity && current.isValid()) {
				holo.rebind(current);
				continue;
			}
			if (!follow.unloaded)
				holo.delete();
		}
	}

	/** Spawns the riding holograms for the players that started tracking an entity last tick. */
	private static void flushPendingTracks() {
		pendingFlushScheduled = false;
		if (PENDING_TRACKS.isEmpty())
			return;
		Map<Player, Set<Entity>> pending = new HashMap<>(PENDING_TRACKS);
		PENDING_TRACKS.clear();
		for (Map.Entry<Player, Set<Entity>> entry : pending.entrySet()) {
			Player player = entry.getKey();
			if (!player.isOnline())
				continue;
			for (Entity entity : entry.getValue()) {
				List<Hologram> followers = FOLLOWERS.get(entity.getUniqueId());
				if (followers == null)
					continue;
				for (Hologram holo : followers.toArray(new Hologram[0])) {
					Hologram.Follow follow = holo.follow;
					if (follow == null)
						continue;
					if (follow.entity != entity && entity.isValid())
						holo.rebind(entity);
					// The client lost the old entities together with the entity, send them again.
					if (follow.passenger && holo.shouldSee(player))
						holo.spawnFor(player);
				}
			}
		}
	}

	// ------------------------------------------------------------------------------------------
	// Events
	// ------------------------------------------------------------------------------------------

	private static final class Events implements Listener {

		@EventHandler(priority = EventPriority.MONITOR)
		public void onChunkSent(PlayerChunkLoadEvent event) {
			if (INDEX.isEmpty())
				return;
			List<Hologram> holograms = holograms(event.getWorld(), event.getChunk());
			if (holograms == null)
				return;
			Player player = event.getPlayer();
			for (Hologram holo : holograms.toArray(new Hologram[0])) {
				if (!holo.viewers.contains(player) && holo.canSee(player))
					holo.spawnFor(player);
			}
		}

		@EventHandler(priority = EventPriority.MONITOR)
		public void onChunkUnsent(PlayerChunkUnloadEvent event) {
			if (INDEX.isEmpty())
				return;
			List<Hologram> holograms = holograms(event.getWorld(), event.getChunk());
			if (holograms == null)
				return;
			Player player = event.getPlayer();
			for (Hologram holo : holograms.toArray(new Hologram[0]))
				holo.despawnFor(player);
		}

		@Nullable
		private static List<Hologram> holograms(World world, Chunk chunk) {
			Map<Long, List<Hologram>> chunks = INDEX.get(world.getUID());
			return chunks == null ? null : chunks.get(Chunk.getChunkKey(chunk.getX(), chunk.getZ()));
		}

		@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
		public void onTrack(PlayerTrackEntityEvent event) {
			if (FOLLOWERS.isEmpty() || !FOLLOWERS.containsKey(event.getEntity().getUniqueId()))
				return;
			// The entity itself is only sent to the player after this event.
			PENDING_TRACKS.computeIfAbsent(event.getPlayer(), k -> new HashSet<>(2)).add(event.getEntity());
			if (!pendingFlushScheduled) {
				pendingFlushScheduled = true;
				Bukkit.getScheduler().runTask(SkriptHolo.getInstance(), HologramManager::flushPendingTracks);
			}
		}

		@EventHandler(priority = EventPriority.MONITOR)
		public void onUntrack(PlayerUntrackEntityEvent event) {
			if (FOLLOWERS.isEmpty())
				return;
			List<Hologram> followers = FOLLOWERS.get(event.getEntity().getUniqueId());
			if (followers == null)
				return;
			Player player = event.getPlayer();
			Set<Entity> pending = PENDING_TRACKS.get(player);
			if (pending != null)
				pending.remove(event.getEntity());
			for (Hologram holo : followers.toArray(new Hologram[0])) {
				if (holo.follow != null && holo.follow.passenger)
					holo.despawnFor(player);
			}
		}

		@EventHandler(priority = EventPriority.MONITOR)
		public void onAdd(EntityAddToWorldEvent event) {
			if (FOLLOWERS.isEmpty())
				return;
			List<Hologram> followers = FOLLOWERS.get(event.getEntity().getUniqueId());
			if (followers == null)
				return;
			for (Hologram holo : followers.toArray(new Hologram[0]))
				holo.rebind(event.getEntity());
		}

		@EventHandler(priority = EventPriority.MONITOR)
		public void onRemove(EntityRemoveEvent event) {
			if (FOLLOWERS.isEmpty())
				return;
			Entity entity = event.getEntity();
			List<Hologram> followers = FOLLOWERS.get(entity.getUniqueId());
			if (followers == null)
				return;
			EntityRemoveEvent.Cause cause = event.getCause();
			// Players are handled by the quit event: respawning also removes the old player entity.
			if (entity instanceof Player)
				return;
			for (Hologram holo : followers.toArray(new Hologram[0])) {
				Hologram.Follow follow = holo.follow;
				// Only the instance the hologram is bound to, a stale copy must not delete it.
				if (follow == null || follow.entity != entity)
					continue;
				if (cause == EntityRemoveEvent.Cause.UNLOAD) {
					// Saved with its chunk, it comes back as a new instance with the same uuid.
					follow.unloaded = true;
				} else if (cause == EntityRemoveEvent.Cause.DEATH) {
					holo.delete();
				} else {
					// Changing dimension removes the entity before its copy is added, so wait
					// a tick: if the same uuid came back the hologram was already bound to it.
					Bukkit.getScheduler().runTask(SkriptHolo.getInstance(), () -> {
						Hologram.Follow current = holo.follow;
						if (current == null || current.entity != entity)
							return;
						Entity copy = Bukkit.getEntity(current.uuid);
						if (copy != null && copy != entity && copy.isValid())
							holo.rebind(copy);
						else
							holo.delete();
					});
				}
			}
		}

		@EventHandler(priority = EventPriority.MONITOR)
		public void onQuit(PlayerQuitEvent event) {
			Player player = event.getPlayer();
			forgetAll(player);
			PENDING_TRACKS.remove(player);
			List<Hologram> followers = FOLLOWERS.get(player.getUniqueId());
			if (followers != null) {
				for (Hologram holo : followers.toArray(new Hologram[0]))
					holo.delete();
			}
		}

		@EventHandler(priority = EventPriority.MONITOR)
		public void onWorldChange(PlayerChangedWorldEvent event) {
			// The client starts from an empty world, chunk and track events send everything again.
			forgetAll(event.getPlayer());
			showOwnFollowers(event.getPlayer());
		}

		@EventHandler(priority = EventPriority.MONITOR)
		public void onRespawn(PlayerPostRespawnEvent event) {
			forgetAll(event.getPlayer());
			showOwnFollowers(event.getPlayer());
		}

		/** A player does not track themselves, the holograms riding them are sent separately. */
		private static void showOwnFollowers(Player player) {
			if (!FOLLOWERS.containsKey(player.getUniqueId()))
				return;
			Bukkit.getScheduler().runTaskLater(SkriptHolo.getInstance(), () -> {
				List<Hologram> followers = FOLLOWERS.get(player.getUniqueId());
				if (followers == null || !player.isOnline())
					return;
				for (Hologram holo : followers.toArray(new Hologram[0])) {
					holo.rebind(player);
					holo.refreshViewers();
				}
			}, 2);
		}

		@EventHandler(priority = EventPriority.MONITOR)
		public void onClick(PlayerUseUnknownEntityEvent event) {
			if (INTERACTIONS.isEmpty())
				return;
			HologramLine line = INTERACTIONS.get(event.getEntityId());
			if (line == null || line.deleted)
				return;
			// A right click arrives twice (interact at + interact) and once per hand, keep one.
			if (!event.isAttack() && (event.getHand() != EquipmentSlot.HAND || event.getClickedRelativePosition() != null))
				return;
			Player player = event.getPlayer();
			if (!line.parent.viewers.contains(player))
				return;
			Bukkit.getPluginManager().callEvent(new HologramLineClickEvent(player, line, event.isAttack()));
		}

	}

	/** Adds the riding displays to the passenger packets the server sends for an entity. */
	private static final class PassengerListener extends PacketListenerAbstract {

		PassengerListener() {
			super(PacketListenerPriority.HIGH);
		}

		@Override
		public void onPacketSend(PacketSendEvent event) {
			// Seen for every outgoing packet: nothing may be allocated before these checks.
			if (event.getPacketType() != PacketType.Play.Server.SET_PASSENGERS || PASSENGERS.isEmpty())
				return;
			WrapperPlayServerSetPassengers packet = new WrapperPlayServerSetPassengers(event);
			int[] displays = PASSENGERS.get(packet.getEntityId());
			if (displays == null)
				return;
			int[] passengers = packet.getPassengers();
			int[] all = Arrays.copyOf(passengers, passengers.length + displays.length);
			System.arraycopy(displays, 0, all, passengers.length, displays.length);
			packet.setPassengers(all);
			event.markForReEncode(true);
		}

	}

}
