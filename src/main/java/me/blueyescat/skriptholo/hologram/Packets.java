package me.blueyescat.skriptholo.hologram;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityType;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.util.Vector3f;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBundle;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityTeleport;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetPassengers;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Builds the packets of the client side display entities.
 * <p>
 * The metadata indices are the ones used since 1.20.2, they are unchanged up to 26.x.
 * A new wrapper is created for every receiver: a wrapper keeps its encoded buffer, and
 * players behind ViaVersion may need a different encoding.
 */
final class Packets {

	// Display
	private static final int POS_ROT_INTERPOLATION = 10;
	private static final int TRANSLATION = 11;
	private static final int BILLBOARD = 15;
	private static final int BRIGHTNESS = 16;
	// Text display
	private static final int TEXT = 23;
	private static final int LINE_WIDTH = 24;
	private static final int BACKGROUND = 25;
	// Item display
	private static final int ITEM = 23;
	private static final int ITEM_TRANSFORM = 24;
	// Interaction
	private static final int INTERACTION_WIDTH = 8;
	private static final int INTERACTION_HEIGHT = 9;
	private static final int INTERACTION_RESPONSIVE = 10;

	/** Faces the camera on every axis, so the text stays readable from below and above too. */
	private static final byte BILLBOARD_CENTER = 3;
	private static final byte TRANSFORM_GROUND = 7;
	private static final int FULL_BRIGHT = 15 << 4 | 15 << 20;
	private static final int DEFAULT_BACKGROUND = 0x40000000;
	/** Wide enough that a hologram line never wraps, like the old armor stand names. */
	private static final int NO_WRAP = 4096;

	private Packets() {}

	/** Packets of one receiver, written without flushing and sent with one flush at the end. */
	static final class Batch {

		@Nullable
		private final User user;
		private boolean bundled;
		private boolean written;

		Batch(Player player) {
			this.user = PacketEvents.getAPI().getPlayerManager().getUser(player);
		}

		boolean isValid() {
			return user != null;
		}

		/** Makes the client apply everything written until {@link #flush} in the same frame. */
		Batch bundle() {
			if (user != null && !bundled) {
				bundled = true;
				write(new WrapperPlayServerBundle());
			}
			return this;
		}

		void write(PacketWrapper<?> wrapper) {
			if (user == null)
				return;
			user.writePacketSilently(wrapper);
			written = true;
		}

		void flush() {
			if (user == null || !written)
				return;
			if (bundled)
				user.writePacketSilently(new WrapperPlayServerBundle());
			user.flushPackets();
			written = false;
			bundled = false;
		}

	}

	static void spawnLine(Batch batch, HologramLine line, double x, double y, double z, float translationY, int interpolation) {
		EntityType type = line.isText() ? EntityTypes.TEXT_DISPLAY : EntityTypes.ITEM_DISPLAY;
		batch.write(new WrapperPlayServerSpawnEntity(line.entityId, Optional.of(line.uuid), type,
				new Vector3d(x, y, z), 0, 0, 0, 0, Optional.empty()));
		batch.write(new WrapperPlayServerEntityMetadata(line.entityId, fullMetadata(line, translationY, interpolation)));
	}

	static void spawnInteraction(Batch batch, HologramLine line, double x, double y, double z) {
		batch.write(new WrapperPlayServerSpawnEntity(line.interactionId, Optional.of(line.interactionUuid),
				EntityTypes.INTERACTION, new Vector3d(x, y, z), 0, 0, 0, 0, Optional.empty()));
		List<EntityData<?>> data = new ArrayList<>(3);
		data.add(new EntityData<>(INTERACTION_WIDTH, EntityDataTypes.FLOAT, line.interactionWidth()));
		data.add(new EntityData<>(INTERACTION_HEIGHT, EntityDataTypes.FLOAT, (float) line.height()));
		data.add(new EntityData<>(INTERACTION_RESPONSIVE, EntityDataTypes.BOOLEAN, true));
		batch.write(new WrapperPlayServerEntityMetadata(line.interactionId, data));
	}

	/**
	 * The riser of a following hologram: an interaction entity without width, so it can never be
	 * hit, whose height puts its passenger seat at the top of the hologram.
	 */
	static void spawnRiser(Batch batch, int entityId, UUID uuid, double x, double y, double z, float height) {
		batch.write(new WrapperPlayServerSpawnEntity(entityId, Optional.of(uuid), EntityTypes.INTERACTION,
				new Vector3d(x, y, z), 0, 0, 0, 0, Optional.empty()));
		riserHeight(batch, entityId, height);
	}

	static void riserHeight(Batch batch, int entityId, float height) {
		List<EntityData<?>> data = new ArrayList<>(3);
		data.add(new EntityData<>(INTERACTION_WIDTH, EntityDataTypes.FLOAT, 0f));
		data.add(new EntityData<>(INTERACTION_HEIGHT, EntityDataTypes.FLOAT, height));
		data.add(new EntityData<>(INTERACTION_RESPONSIVE, EntityDataTypes.BOOLEAN, false));
		batch.write(new WrapperPlayServerEntityMetadata(entityId, data));
	}

	private static List<EntityData<?>> fullMetadata(HologramLine line, float translationY, int interpolation) {
		List<EntityData<?>> data = new ArrayList<>(8);
		if (interpolation > 0)
			data.add(new EntityData<>(POS_ROT_INTERPOLATION, EntityDataTypes.INT, interpolation));
		data.add(new EntityData<>(TRANSLATION, EntityDataTypes.VECTOR3F, new Vector3f(0, translationY, 0)));
		data.add(new EntityData<>(BILLBOARD, EntityDataTypes.BYTE, BILLBOARD_CENTER));
		data.add(new EntityData<>(BRIGHTNESS, EntityDataTypes.INT, FULL_BRIGHT));
		addContent(data, line);
		return data;
	}

	private static void addContent(List<EntityData<?>> data, HologramLine line) {
		if (line.isText()) {
			data.add(new EntityData<>(TEXT, EntityDataTypes.ADV_COMPONENT, line.component));
			data.add(new EntityData<>(LINE_WIDTH, EntityDataTypes.INT, NO_WRAP));
			// An empty line is a spacer: without this it would render as a small dark box.
			data.add(new EntityData<>(BACKGROUND, EntityDataTypes.INT, line.text.isEmpty() ? 0 : DEFAULT_BACKGROUND));
		} else {
			data.add(new EntityData<>(ITEM, EntityDataTypes.ITEMSTACK, SpigotConversionUtil.fromBukkitItemStack(line.item)));
			data.add(new EntityData<>(ITEM_TRANSFORM, EntityDataTypes.BYTE, TRANSFORM_GROUND));
		}
	}

	/** Metadata for a content change of a line whose type did not change. */
	static void content(Batch batch, HologramLine line) {
		List<EntityData<?>> data = new ArrayList<>(3);
		addContent(data, line);
		batch.write(new WrapperPlayServerEntityMetadata(line.entityId, data));
	}

	static void translation(Batch batch, HologramLine line, float translationY) {
		List<EntityData<?>> data = new ArrayList<>(1);
		data.add(new EntityData<>(TRANSLATION, EntityDataTypes.VECTOR3F, new Vector3f(0, translationY, 0)));
		batch.write(new WrapperPlayServerEntityMetadata(line.entityId, data));
	}

	static void interactionHeight(Batch batch, HologramLine line) {
		List<EntityData<?>> data = new ArrayList<>(2);
		data.add(new EntityData<>(INTERACTION_WIDTH, EntityDataTypes.FLOAT, line.interactionWidth()));
		data.add(new EntityData<>(INTERACTION_HEIGHT, EntityDataTypes.FLOAT, (float) line.height()));
		batch.write(new WrapperPlayServerEntityMetadata(line.interactionId, data));
	}

	static void teleport(Batch batch, int entityId, double x, double y, double z) {
		batch.write(new WrapperPlayServerEntityTeleport(entityId, new Vector3d(x, y, z), 0, 0, false));
	}

	static void passengers(Batch batch, int vehicleId, int[] passengers) {
		batch.write(new WrapperPlayServerSetPassengers(vehicleId, passengers));
	}

	static void destroy(Batch batch, int... entityIds) {
		if (entityIds.length != 0)
			batch.write(new WrapperPlayServerDestroyEntities(entityIds));
	}

}
