package me.blueyescat.skriptholo.hologram;

import java.util.List;

/**
 * Compatibility view for scripts that used to reach into the DecentHolograms objects through
 * skript-reflect, e.g. {@code ({_holo}).getPage(0)} followed by {@code ({_page}).setLine(0, "text")}
 * or {@code ({_page}).lines.size()}. A hologram only has this single page.
 */
public final class HologramPage {

	private final Hologram parent;
	/** Live, read only view of the lines. Named like the DecentHolograms field on purpose. */
	public final List<HologramLine> lines;

	HologramPage(Hologram parent, List<HologramLine> lines) {
		this.parent = parent;
		this.lines = lines;
	}

	public Hologram getParent() {
		return parent;
	}

	public List<HologramLine> getLines() {
		return lines;
	}

	public HologramLine getLine(int index) {
		return parent.getLine(index);
	}

	public int size() {
		return lines.size();
	}

	public double getHeight() {
		return parent.getHeight();
	}

	/** Zero based, like DecentHolograms. Returns false when there is no such line. */
	public boolean setLine(int index, String content) {
		return parent.setLine(index, content);
	}

	public void addLine(String content) {
		parent.addLine(content, false);
	}

	public void removeLine(int index) {
		HologramLine line = parent.getLine(index);
		if (line != null)
			line.delete();
	}

}
