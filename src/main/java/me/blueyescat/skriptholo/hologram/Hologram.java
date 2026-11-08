package me.blueyescat.skriptholo.hologram;

import ch.njol.skript.util.Direction;
import me.blueyescat.skriptholo.util.Utils;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * A hologram made of client side display entities. Nothing exists in the world: every line is
 * only sent as packets to the players that can see it.
 * <p>
 * Who can see a hologram is decided by Paper itself:
 * <ul>
 *     <li>a standing hologram is shown to the players that received its chunk
 *     ({@code PlayerChunkLoadEvent}, {@link Player#isChunkSent(long)})</li>
 *     <li>a hologram riding an entity is shown to the players that track that entity
 *     ({@code PlayerTrackEntityEvent}, {@link Entity#getTrackedBy()})</li>
 * </ul>
 * A hologram that follows an entity with a vertical offset rides it as a passenger (through an
 * invisible riser, see {@link #riserId}): the client moves it together with the entity, so
 * following costs nothing on the server. Other offsets, or
 * clickable lines, fall back to moving the hologram once per tick.
 * <p>
 * Every method must be called on the main thread. Calls coming from another thread are moved to
 * the main thread.
 */
public final class Hologram {

	static final double EPSILON = 1.0E-4;
	/** Teleport interpolation of the holograms moved every tick, so they glide instead of stepping. */
	private static final int TICK_INTERPOLATION = 2;
	private static final Direction[] NO_OFFSET = new Direction[0];

	final List<HologramLine> lines = new ArrayList<>(4);
	private final List<HologramLine> linesView = Collections.unmodifiableList(lines);
	private final HologramPage page = new HologramPage(this, linesView);
	/** Players that currently have the entities of this hologram. */
	final Set<Player> viewers = new HashSet<>();
	/**
	 * Invisible, zero width interaction entity the lines ride while following an entity. Its
	 * height lifts the lines to the offset, so the translation of the displays stays tiny: a
	 * display facing the camera rotates its translation with it, a big one would swing around.
	 */
	final int riserId = EntityIds.next();
	private final UUID riserUuid = UUID.randomUUID();

	/** Top of the hologram. While riding an entity this is only refreshed when needed. */
	private Location location;
	private boolean deleted;
	private boolean visibleByDefault = true;
	/** Per player visibility, overrides {@link #visibleByDefault}. */
	@Nullable
	private Map<UUID, Boolean> visibility;
	@Nullable
	Follow follow;

	/** Where the hologram is indexed, see {@link HologramManager}. */
	@Nullable
	UUID indexedWorld;
	long indexedChunk;

	Hologram(Location location) {
		this.location = location.clone();
	}

	// ------------------------------------------------------------------------------------------
	// Lines
	// ------------------------------------------------------------------------------------------

	/** A snapshot of the lines. */
	public List<HologramLine> getLines() {
		return new ArrayList<>(lines);
	}

	@Nullable
	public HologramLine getLine(int index) {
		return index >= 0 && index < lines.size() ? lines.get(index) : null;
	}

	public int size() {
		return lines.size();
	}

	/** DecentHolograms compatibility, see {@link HologramPage}. */
	@Nullable
	public HologramPage getPage(int index) {
		return index == 0 ? page : null;
	}

	public void addLine(Object content, boolean interactive) {
		insertLine(lines.size(), content, interactive);
	}

	/** Inserts a line, the content is a text or an {@link ItemStack}. */
	public void insertLine(int index, Object content, boolean interactive) {
		if (deleted || index < 0 || index > lines.size())
			return;
		if (Sync.defer(() -> insertLine(index, content, interactive)))
			return;

		HologramLine line = new HologramLine(this, content, interactive);
		lines.add(index, line);
		layout();
		HologramManager.lineAdded(this, line);

		if (follow != null && follow.passenger && line.isInteractive()) {
			// An interaction entity can not be offset while riding, switch to per tick following.
			refollow();
			return;
		}
		if (viewers.isEmpty())
			return;
		for (Player player : viewers) {
			Packets.Batch batch = new Packets.Batch(player);
			if (follow != null && follow.passenger)
				batch.bundle();
			writeSpawnLine(batch, line);
			writeLayout(batch, index + 1);
			if (follow != null && follow.passenger)
				writeRiderPassengers(batch);
			batch.flush();
		}
	}

	/** Zero based. Returns false when there is no such line. */
	public boolean setLine(int index, Object content) {
		HologramLine line = getLine(index);
		if (line == null)
			return false;
		setContent(line, content);
		return true;
	}

	void setContent(HologramLine line, Object content) {
		if (deleted || line.deleted || line.parent != this)
			return;
		if (Sync.defer(() -> setContent(line, content)))
			return;
		if (isSameContent(line, content))
			return;

		double oldHeight = line.height();
		boolean typeChanged = line.apply(content);
		boolean heightChanged = Math.abs(oldHeight - line.height()) > EPSILON;
		int index = lines.indexOf(line);
		if (heightChanged)
			layout();
		if (viewers.isEmpty())
			return;

		boolean passenger = follow != null && follow.passenger;
		for (Player player : viewers) {
			Packets.Batch batch = new Packets.Batch(player);
			if (typeChanged) {
				// A display can not change its type, it has to be spawned again.
				if (passenger)
					batch.bundle();
				Packets.destroy(batch, line.entityId);
				writeSpawnLine(batch, line);
				if (passenger)
					writeRiderPassengers(batch);
			} else {
				Packets.content(batch, line);
			}
			if (heightChanged) {
				writeLayout(batch, index);
				if (line.isInteractive())
					Packets.interactionHeight(batch, line);
			}
			batch.flush();
		}
	}

	private static boolean isSameContent(HologramLine line, Object content) {
		if (content instanceof ItemStack stack)
			return line.isItem() && Objects.equals(line.item, stack);
		return line.isText() && line.text.equals(content == null ? "" : content.toString());
	}

	void removeLine(HologramLine line) {
		if (deleted || line.deleted || line.parent != this)
			return;
		if (Sync.defer(() -> removeLine(line)))
			return;

		int index = lines.indexOf(line);
		lines.remove(index);
		line.deleted = true;
		layout();
		HologramManager.lineRemoved(this, line);

		if (viewers.isEmpty())
			return;
		int[] ids = line.entityIds();
		for (Player player : viewers) {
			Packets.Batch batch = new Packets.Batch(player);
			Packets.destroy(batch, ids);
			writeLayout(batch, index);
			batch.flush();
		}
	}

	/** Replaces every line at once, sending as little as possible. */
	public void setLines(List<?> contents, boolean interactive) {
		if (deleted)
			return;
		if (Sync.defer(() -> setLines(contents, interactive)))
			return;
		despawnAll();
		clearLinesSilently();
		for (Object content : contents) {
			HologramLine line = new HologramLine(this, content, interactive);
			lines.add(line);
			HologramManager.lineAdded(this, line);
		}
		layout();
		if (follow != null)
			refollow();
		else
			refreshViewers();
	}

	public void clearLines() {
		setLines(Collections.emptyList(), false);
	}

	private void clearLinesSilently() {
		for (HologramLine line : lines) {
			line.deleted = true;
			HologramManager.lineRemoved(this, line);
		}
		lines.clear();
	}

	void layout() {
		double y = 0;
		for (HologramLine line : lines) {
			y -= line.height();
			line.bottom = y;
		}
	}

	public double getHeight() {
		double height = 0;
		for (HologramLine line : lines)
			height += line.height();
		return height;
	}

	// ------------------------------------------------------------------------------------------
	// Location
	// ------------------------------------------------------------------------------------------

	/** The current top location, following the entity if there is one. */
	public Location getLocation() {
		if (follow != null) {
			Entity entity = follow.resolve();
			if (entity != null)
				return follow.target(entity);
		}
		return location.clone();
	}

	World getWorld() {
		return location.getWorld();
	}

	long getChunkKey() {
		return Chunk.getChunkKey(location.getBlockX() >> 4, location.getBlockZ() >> 4);
	}

	/** Teleports the hologram. Does nothing while it follows an entity. */
	public void teleport(Location target) {
		if (deleted || follow != null || target.getWorld() == null)
			return;
		if (Sync.defer(() -> teleport(target)))
			return;
		relocate(target.clone());
	}

	/** Moves the standing hologram and updates who can see it. */
	private void relocate(Location target) {
		World oldWorld = location.getWorld();
		long oldChunk = getChunkKey();
		location = target;

		boolean sameWorld = target.getWorld() == oldWorld;
		boolean sameChunk = sameWorld && getChunkKey() == oldChunk;
		if (!sameChunk)
			HologramManager.index(this);
		if (!sameWorld) {
			despawnAll();
			refreshViewers();
			return;
		}

		for (Player player : viewers) {
			Packets.Batch batch = new Packets.Batch(player);
			writeTeleport(batch);
			batch.flush();
		}
		if (!sameChunk)
			refreshViewers();
	}

	// ------------------------------------------------------------------------------------------
	// Following
	// ------------------------------------------------------------------------------------------

	/** Makes the hologram follow an entity. A hologram follows at most one entity. */
	public void follow(Entity entity, @Nullable Direction[] offset) {
		if (deleted)
			return;
		if (Sync.defer(() -> follow(entity, offset)))
			return;

		despawnAll();
		if (follow != null)
			HologramManager.unregisterFollower(this);
		else
			HologramManager.unindex(this);

		follow = new Follow(entity, offset == null || offset.length == 0 ? NO_OFFSET : offset.clone());
		startFollowing();
	}

	public void unfollow() {
		if (deleted || follow == null)
			return;
		if (Sync.defer(this::unfollow))
			return;

		Location target = getLocation();
		despawnAll();
		HologramManager.unregisterFollower(this);
		follow = null;
		location = target;
		HologramManager.index(this);
		refreshViewers();
	}

	/** Re-evaluates the follow mode, e.g. after a clickable line was added. */
	private void refollow() {
		if (follow == null)
			return;
		despawnAll();
		HologramManager.unregisterFollower(this);
		startFollowing();
	}

	private void startFollowing() {
		Follow follow = this.follow;
		assert follow != null;
		follow.passenger = follow.vertical != null && !hasInteractiveLine();
		follow.refreshBase();
		location = follow.target(follow.entity);
		HologramManager.registerFollower(this);
		if (!follow.passenger)
			HologramManager.index(this);
		refreshViewers();
	}

	private boolean hasInteractiveLine() {
		for (HologramLine line : lines) {
			if (line.isInteractive())
				return true;
		}
		return false;
	}

	public boolean isFollowing() {
		return follow != null;
	}

	@Nullable
	public Entity getFollowedEntity() {
		return follow == null ? null : follow.entity;
	}

	public Direction[] getFollowOffset() {
		return follow == null ? NO_OFFSET : follow.offset.clone();
	}

	public void setFollowOffset(@Nullable Direction[] offset) {
		if (deleted || follow == null)
			return;
		if (Sync.defer(() -> setFollowOffset(offset)))
			return;
		Direction[] newOffset = offset == null || offset.length == 0 ? NO_OFFSET : offset.clone();
		Follow old = follow;
		Follow updated = new Follow(old.entity, newOffset);
		if (old.passenger && updated.vertical != null) {
			// Still riding: only the translation changes.
			old.offset = newOffset;
			old.vertical = updated.vertical;
			old.refreshBase();
			writeRiserToViewers();
			return;
		}
		old.offset = newOffset;
		old.vertical = updated.vertical;
		refollow();
	}

	/** Called every tick for holograms that can not ride their entity. */
	void tickFollow() {
		Follow follow = this.follow;
		if (follow == null || follow.passenger)
			return;
		Entity entity = follow.resolve();
		if (entity == null)
			return;
		Location target = follow.target(entity);
		if (target.getWorld() == location.getWorld() && target.distanceSquared(location) < EPSILON * EPSILON)
			return;
		relocate(target);
	}

	/** The followed entity got a new entity instance, after being loaded again or changing world. */
	void rebind(Entity entity) {
		Follow follow = this.follow;
		if (follow == null)
			return;
		follow.unloaded = false;
		if (follow.entity == entity)
			return;
		despawnAll();
		HologramManager.unregisterFollower(this);
		follow.entity = entity;
		startFollowing();
	}

	/** Re-sends the translations when the size of the ridden entity changed, e.g. a baby grew up. */
	void checkVehicleSize() {
		Follow follow = this.follow;
		if (follow == null || !follow.passenger || !follow.entity.isValid())
			return;
		if (follow.refreshBase())
			writeRiserToViewers();
	}

	private void writeRiserToViewers() {
		Follow follow = this.follow;
		if (follow == null || !follow.passenger)
			return;
		for (Player player : viewers) {
			Packets.Batch batch = new Packets.Batch(player);
			Packets.riserHeight(batch, riserId, follow.riserHeight);
			writeLayout(batch, 0);
			batch.flush();
		}
	}

	/** State of a following hologram. */
	static final class Follow {

		final UUID uuid;
		Entity entity;
		Direction[] offset;
		/** The offset when it only goes up or down whatever the entity faces, otherwise null. */
		@Nullable
		Double vertical;
		boolean passenger;
		/** Height of the riser: from the passenger seat of the entity to the top of the hologram. */
		float riserHeight;
		/** What the riser can not cover when the offset is below the seat. Zero or negative. */
		float shift;
		/** Set while the entity is unloaded with its chunk, it will come back with a new instance. */
		boolean unloaded;

		Follow(Entity entity, Direction[] offset) {
			this.uuid = entity.getUniqueId();
			this.entity = entity;
			this.offset = offset;
			this.vertical = verticalOffset(offset);
		}

		/** Returns whether the base changed. */
		boolean refreshBase() {
			if (vertical == null)
				return false;
			// Passengers sit on top of the entity's bounding box.
			float base = (float) (vertical - entity.getHeight());
			float riserHeight = Math.max(0, base);
			float shift = base - riserHeight;
			if (Math.abs(riserHeight - this.riserHeight) < EPSILON && Math.abs(shift - this.shift) < EPSILON)
				return false;
			this.riserHeight = riserHeight;
			this.shift = shift;
			return true;
		}

		Location target(Entity entity) {
			Location location = entity.getLocation();
			return offset.length == 0 ? location : Utils.offsetLocation(location, offset);
		}

		@Nullable
		Entity resolve() {
			if (entity.isValid() || entity instanceof Player player && player.isOnline())
				return entity;
			return null;
		}

		@Nullable
		private static Double verticalOffset(Direction[] offset) {
			if (offset.length == 0)
				return 0.0;
			// Evaluated for two different facings: a relative direction like "in front" changes.
			Location a = Utils.offsetLocation(new Location(null, 0, 0, 0, 0, 0), offset);
			Location b = Utils.offsetLocation(new Location(null, 0, 0, 0, 137, 33), offset);
			if (Math.abs(a.getX()) > EPSILON || Math.abs(a.getZ()) > EPSILON
					|| Math.abs(b.getX()) > EPSILON || Math.abs(b.getZ()) > EPSILON
					|| Math.abs(a.getY() - b.getY()) > EPSILON)
				return null;
			return a.getY();
		}

	}

	// ------------------------------------------------------------------------------------------
	// Visibility
	// ------------------------------------------------------------------------------------------

	public boolean isVisibleByDefault() {
		return visibleByDefault;
	}

	public void setVisibleByDefault(boolean visible) {
		if (deleted || visibleByDefault == visible)
			return;
		if (Sync.defer(() -> setVisibleByDefault(visible)))
			return;
		visibleByDefault = visible;
		refreshViewers();
	}

	/** Whether the player is allowed to see the hologram, wherever they are. */
	public boolean canSee(Player player) {
		if (visibility != null) {
			Boolean visible = visibility.get(player.getUniqueId());
			if (visible != null)
				return visible;
		}
		return visibleByDefault;
	}

	public void show(Player player) {
		setVisibility(player, Boolean.TRUE);
	}

	public void hide(Player player) {
		setVisibility(player, Boolean.FALSE);
	}

	public void resetVisibility(Player player) {
		setVisibility(player, null);
	}

	public void resetVisibility() {
		if (deleted || visibility == null)
			return;
		if (Sync.defer(this::resetVisibility))
			return;
		visibility = null;
		refreshViewers();
	}

	private void setVisibility(Player player, @Nullable Boolean visible) {
		if (deleted)
			return;
		if (Sync.defer(() -> setVisibility(player, visible)))
			return;
		if (visible == null) {
			if (visibility == null || visibility.remove(player.getUniqueId()) == null)
				return;
		} else {
			if (visibility == null)
				visibility = new HashMap<>(4);
			if (visible.equals(visibility.put(player.getUniqueId(), visible)))
				return;
		}
		updateViewer(player);
	}

	// ------------------------------------------------------------------------------------------
	// Viewers
	// ------------------------------------------------------------------------------------------

	/** Whether the player should currently have the entities of this hologram. */
	boolean shouldSee(Player player) {
		if (deleted || !player.isOnline() || !canSee(player))
			return false;
		Follow follow = this.follow;
		if (follow != null && follow.passenger) {
			Entity entity = follow.resolve();
			if (entity == null || entity.getWorld() != player.getWorld())
				return false;
			return entity == player || entity.getTrackedBy().contains(player);
		}
		return player.getWorld() == location.getWorld() && player.isChunkSent(getChunkKey());
	}

	void updateViewer(Player player) {
		boolean should = shouldSee(player);
		boolean has = viewers.contains(player);
		if (should && !has)
			spawnFor(player);
		else if (!should && has)
			despawnFor(player);
	}

	/** Spawns the hologram for every player that should see it and removes it for the others. */
	void refreshViewers() {
		if (deleted)
			return;
		Set<Player> audience = audience();
		if (!viewers.isEmpty()) {
			int[] ids = null;
			for (Iterator<Player> it = viewers.iterator(); it.hasNext(); ) {
				Player viewer = it.next();
				if (audience.contains(viewer))
					continue;
				it.remove();
				HologramManager.unview(viewer, this);
				if (ids == null)
					ids = allEntityIds();
				Packets.Batch batch = new Packets.Batch(viewer);
				Packets.destroy(batch, ids);
				batch.flush();
			}
		}
		for (Player player : audience) {
			if (!viewers.contains(player))
				spawnFor(player);
		}
	}

	/** Every player that should have the entities, same rules as {@link #shouldSee}. */
	private Set<Player> audience() {
		Set<Player> audience = new HashSet<>();
		Follow follow = this.follow;
		if (follow != null && follow.passenger) {
			Entity entity = follow.resolve();
			if (entity == null)
				return audience;
			World world = entity.getWorld();
			for (Player player : entity.getTrackedBy()) {
				if (player.getWorld() == world && canSee(player))
					audience.add(player);
			}
			if (entity instanceof Player self && self.isOnline() && canSee(self))
				audience.add(self);
			return audience;
		}
		World world = location.getWorld();
		if (world == null)
			return audience;
		long chunkKey = getChunkKey();
		for (Player player : world.getPlayers()) {
			if (player.isChunkSent(chunkKey) && canSee(player))
				audience.add(player);
		}
		return audience;
	}

	/** Sends the hologram to a player, again if they already have it. */
	void spawnFor(Player player) {
		if (deleted)
			return;
		Packets.Batch batch = new Packets.Batch(player);
		if (!batch.isValid())
			return;
		Follow follow = this.follow;
		boolean passenger = follow != null && follow.passenger;
		if (passenger) {
			batch.bundle();
			Location at = follow.entity.getLocation();
			Packets.spawnRiser(batch, riserId, riserUuid, at.getX(), at.getY(), at.getZ(), follow.riserHeight);
		}
		for (HologramLine line : lines)
			writeSpawnLine(batch, line);
		if (passenger) {
			writeVehiclePassengers(batch);
			writeRiderPassengers(batch);
		}
		batch.flush();
		if (viewers.add(player))
			HologramManager.view(player, this);
	}

	void despawnFor(Player player) {
		if (!viewers.remove(player))
			return;
		HologramManager.unview(player, this);
		Packets.Batch batch = new Packets.Batch(player);
		Packets.destroy(batch, allEntityIds());
		batch.flush();
	}

	void despawnAll() {
		if (viewers.isEmpty())
			return;
		int[] ids = allEntityIds();
		for (Player player : viewers) {
			HologramManager.unview(player, this);
			Packets.Batch batch = new Packets.Batch(player);
			Packets.destroy(batch, ids);
			batch.flush();
		}
		viewers.clear();
	}

	/** Forgets a viewer without sending anything, their client already dropped the entities. */
	void forgetViewer(Player player) {
		viewers.remove(player);
	}

	/** Every client side entity of the hologram, the riser included. */
	int[] allEntityIds() {
		int count = 1;
		for (HologramLine line : lines)
			count += line.isInteractive() ? 2 : 1;
		int[] ids = new int[count];
		ids[0] = riserId;
		int i = 1;
		for (HologramLine line : lines) {
			ids[i++] = line.entityId;
			if (line.isInteractive())
				ids[i++] = line.interactionId;
		}
		return ids;
	}

	/** Ids of the displays riding the riser. */
	int[] displayEntityIds() {
		int[] ids = new int[lines.size()];
		for (int i = 0; i < ids.length; i++)
			ids[i] = lines.get(i).entityId;
		return ids;
	}

	private void writeSpawnLine(Packets.Batch batch, HologramLine line) {
		Follow follow = this.follow;
		if (follow != null && follow.passenger) {
			Location at = follow.entity.getLocation();
			Packets.spawnLine(batch, line, at.getX(), at.getY(), at.getZ(), riderTranslation(follow, line), 0);
			return;
		}
		double x = location.getX(), y = location.getY(), z = location.getZ();
		Packets.spawnLine(batch, line, x, y + line.centerY(), z, line.pivotY(), follow != null ? TICK_INTERPOLATION : 0);
		if (line.isInteractive())
			Packets.spawnInteraction(batch, line, x, y + line.interactionBottom(), z);
	}

	/** Sends the position of every line starting from the given index. */
	private void writeLayout(Packets.Batch batch, int from) {
		Follow follow = this.follow;
		boolean passenger = follow != null && follow.passenger;
		double x = location.getX(), y = location.getY(), z = location.getZ();
		for (int i = from; i < lines.size(); i++) {
			HologramLine line = lines.get(i);
			if (passenger) {
				Packets.translation(batch, line, riderTranslation(follow, line));
				continue;
			}
			Packets.teleport(batch, line.entityId, x, y + line.centerY(), z);
			Packets.translation(batch, line, line.pivotY());
			if (line.isInteractive())
				Packets.teleport(batch, line.interactionId, x, y + line.interactionBottom(), z);
		}
	}

	private void writeTeleport(Packets.Batch batch) {
		double x = location.getX(), y = location.getY(), z = location.getZ();
		for (HologramLine line : lines) {
			Packets.teleport(batch, line.entityId, x, y + line.centerY(), z);
			if (line.isInteractive())
				Packets.teleport(batch, line.interactionId, x, y + line.interactionBottom(), z);
		}
	}

	/** Translation of a display riding the riser, which already sits at the top of the hologram. */
	private static float riderTranslation(Follow follow, HologramLine line) {
		return (float) (follow.shift + line.centerY() + line.pivotY());
	}

	/** The full passenger list of the followed entity: its real passengers plus every riser. */
	private void writeVehiclePassengers(Packets.Batch batch) {
		Entity vehicle = follow.entity;
		Packets.passengers(batch, vehicle.getEntityId(), HologramManager.passengersOf(vehicle));
	}

	private void writeRiderPassengers(Packets.Batch batch) {
		Packets.passengers(batch, riserId, displayEntityIds());
	}

	// ------------------------------------------------------------------------------------------
	// Lifecycle
	// ------------------------------------------------------------------------------------------

	public boolean isDeleted() {
		return deleted;
	}

	public void delete() {
		if (deleted)
			return;
		if (Sync.defer(this::delete))
			return;
		despawnAll();
		if (follow != null) {
			HologramManager.unregisterFollower(this);
			follow = null;
		}
		clearLinesSilently();
		deleted = true;
		HologramManager.unregister(this);
	}

	@Override
	public String toString() {
		return "hologram";
	}

}
