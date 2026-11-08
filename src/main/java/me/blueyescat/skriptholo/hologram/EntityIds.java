package me.blueyescat.skriptholo.hologram;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.Bukkit;
import org.bukkit.UnsafeValues;
import org.bukkit.World;

/**
 * Entity ids for the client side entities, taken from the server counter so they never collide.
 * <p>
 * Paper up to 1.21.x has {@code UnsafeValues#nextEntityId()}. Paper 26.2 replaced it with
 * {@code UnsafeValues#nextEntityId(World)}; the counter behind it is still global
 * ({@code ServerLevel.ENTITY_COUNTER}), the world is only used to skip ids already taken in it,
 * so any loaded world gives an id that is unique on the whole server. Both are looked up
 * reflectively, so one jar runs on both lines.
 */
final class EntityIds {

	private EntityIds() {}

	/** {@code nextEntityId()} on old servers, otherwise null. */
	private static final MethodHandle NO_ARG;
	/** {@code nextEntityId(World)} on 26.2+, otherwise null. */
	private static final MethodHandle WITH_WORLD;
	/** Last resort: far above the ids a server hands out in practice. */
	private static final AtomicInteger FALLBACK = new AtomicInteger(1_500_000_000);

	static {
		MethodHandles.Lookup lookup = MethodHandles.publicLookup();
		MethodHandle noArg = null;
		MethodHandle withWorld = null;
		try {
			noArg = lookup.findVirtual(UnsafeValues.class, "nextEntityId", MethodType.methodType(int.class));
		} catch (ReflectiveOperationException ignored) {}
		try {
			withWorld = lookup.findVirtual(UnsafeValues.class, "nextEntityId", MethodType.methodType(int.class, World.class));
		} catch (ReflectiveOperationException ignored) {}
		NO_ARG = noArg;
		WITH_WORLD = withWorld;
		if (noArg == null && withWorld == null)
			Bukkit.getLogger().warning("[skript-holo] UnsafeValues#nextEntityId not found, using a private id range");
	}

	@SuppressWarnings("deprecation")
	static int next() {
		try {
			if (NO_ARG != null)
				return (int) NO_ARG.invoke(Bukkit.getUnsafe());
			if (WITH_WORLD != null) {
				List<World> worlds = Bukkit.getWorlds();
				if (!worlds.isEmpty())
					return (int) WITH_WORLD.invoke(Bukkit.getUnsafe(), worlds.get(0));
			}
		} catch (Throwable ignored) {}
		return FALLBACK.incrementAndGet();
	}

}
