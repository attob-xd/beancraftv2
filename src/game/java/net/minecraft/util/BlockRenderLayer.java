package net.minecraft.util;

public enum BlockRenderLayer {
	SOLID("Solid"), CUTOUT_MIPPED("Mipped Cutout"), CUTOUT("Cutout"), TRANSLUCENT("Translucent"),
	/** Added for the deferred pipeline, matching the 1.8 client. */
	REALISTIC_WATER("EaglerShaderWater"), GLASS_HIGHLIGHTS("EaglerShaderGlassHighlights");

	private final String layerName;

	public static final BlockRenderLayer[] _VALUES = values();

	private BlockRenderLayer(String layerNameIn) {
		this.layerName = layerNameIn;
	}

	public String toString() {
		return this.layerName;
	}
}
