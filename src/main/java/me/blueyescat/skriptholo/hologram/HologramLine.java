package me.blueyescat.skriptholo.hologram;

import net.kyori.adventure.text.Component;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * A single line of a {@link Hologram}: a client side text or item display, plus an interaction
 * entity when the line can be clicked.
 */
public final class HologramLine {

	public enum Type { TEXT, ITEM }

	static final double TEXT_HEIGHT = 0.3;
	static final double ITEM_HEIGHT = 0.6;
	/** Height of one rendered text row, a text display is 10 pixels of 1/40 block per row. */
	static final double TEXT_ROW = 0.25;
	/** Every line sits this much lower than the hologram location, like DecentHolograms lines did. */
	static final double PLACEMENT_OFFSET = -0.2;

	final Hologram parent;
	final int entityId;
	final UUID uuid = UUID.randomUUID();
	/** -1 when the line has no interaction entity. */
	int interactionId = -1;
	@Nullable
	UUID interactionUuid;

	Type type;
	String text = "";
	Component component = Component.empty();
	@Nullable
	ItemStack item;
	int rows = 1;

	/** Distance from the top of the hologram to the bottom of this line, always negative. */
	double bottom;
	boolean deleted;

	HologramLine(Hologram parent, Object content, boolean interactive) {
		this.parent = parent;
		this.entityId = EntityIds.next();
		if (interactive)
			makeInteractive();
		apply(content);
	}

	void makeInteractive() {
		if (interactionId != -1)
			return;
		interactionId = EntityIds.next();
		interactionUuid = UUID.randomUUID();
	}

	/** Stores the content, returns whether the line type changed. */
	boolean apply(Object content) {
		Type old = type;
		if (content instanceof ItemStack stack) {
			type = Type.ITEM;
			item = stack.clone();
			text = "";
			component = Component.empty();
			rows = 1;
		} else {
			type = Type.TEXT;
			item = null;
			text = content == null ? "" : content.toString();
			component = Text.parse(text);
			rows = Text.rows(text);
		}
		return old != null && old != type;
	}

	double height() {
		return type == Type.TEXT ? TEXT_HEIGHT + (rows - 1) * TEXT_ROW : ITEM_HEIGHT;
	}

	/** Y translation of the display, relative to the top of the hologram. */
	/**
	 * Center of the rendered content, relative to the top of the hologram. Shifted down by
	 * {@link #PLACEMENT_OFFSET} so script offsets made for DecentHolograms stay correct.
	 */
	double centerY() {
		double top = bottom + height();
		double center = type == Type.TEXT ? top - TEXT_ROW * rows / 2 : top - ITEM_HEIGHT / 2;
		return center + PLACEMENT_OFFSET;
	}

	/**
	 * Translation that puts the center of the content on the entity position. The displays face
	 * the camera on every axis, so they rotate around their position: a text display grows up
	 * from its position, centering it keeps the text in place when looked at from below.
	 */
	float pivotY() {
		return type == Type.TEXT ? (float) (-TEXT_ROW * rows / 2) : 0f;
	}

	/** Bottom of the interaction box, relative to the top of the hologram. */
	double interactionBottom() {
		return bottom + PLACEMENT_OFFSET;
	}

	float interactionWidth() {
		if (type == Type.ITEM)
			return 0.5f;
		// Rough width of the rendered text: 6 pixels per visible character.
		int visible = 0;
		String raw = text;
		for (int i = 0, length = raw.length(); i < length; i++) {
			char c = raw.charAt(i);
			if ((c == '&' || c == '§') && i + 1 < length) {
				i++;
				continue;
			}
			visible++;
		}
		return (float) Math.max(0.5, Math.min(visible * 0.15, 8));
	}

	int[] entityIds() {
		return interactionId == -1 ? new int[] {entityId} : new int[] {entityId, interactionId};
	}

	public Hologram getParent() {
		return parent;
	}

	public Type getType() {
		return type;
	}

	public boolean isText() {
		return type == Type.TEXT;
	}

	public boolean isItem() {
		return type == Type.ITEM;
	}

	/** The raw text as it was set, or null for an item line. */
	@Nullable
	public String getText() {
		return type == Type.TEXT ? text : null;
	}

	/** A copy of the item, or null for a text line. */
	@Nullable
	public ItemStack getItem() {
		return item == null ? null : item.clone();
	}

	public boolean isInteractive() {
		return interactionId != -1;
	}

	public boolean isDeleted() {
		return deleted;
	}

	/** Zero based index of this line, or -1 if it was deleted. */
	public int getIndex() {
		return deleted ? -1 : parent.lines.indexOf(this);
	}

	public void setText(String text) {
		parent.setContent(this, text);
	}

	public void setItem(ItemStack item) {
		parent.setContent(this, item);
	}

	public void delete() {
		parent.removeLine(this);
	}

	@Override
	public String toString() {
		return type == Type.TEXT ? "hologram line \"" + text + "\"" : "hologram item line " + item;
	}

}
