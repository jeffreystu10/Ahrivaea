package dev.ahrivaea.client;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;

public class CreatureRenderState extends LivingEntityRenderState {
	public Identifier texture;
	public boolean sitting;
	/** Colour the model is tinted (ARGB), or -1 for none. */
	public int tint = -1;
	/** The loaded model for this creature's form, or null to use the placeholder body. */
	public GeoCreatureModel geo;
	public float modelScale = 1f;
	/** Longest horizontal size of the model in blocks at this scale (used to shrink creatures to one square in battle). */
	public float extent = 1f;
}
