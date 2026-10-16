package dev.luxnet.winter.client.debug;

import dev.luxnet.winter.client.WinterConfig;
import dev.luxnet.winter.client.assistance.AimAssist;
import dev.luxnet.winter.client.visuals.EspBoxRenderer;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Draws the hitbox position currently used by Aim Assist's prediction. */
public final class DrawPredict {
	private static boolean enabled;

	private DrawPredict() { }

	public static boolean isEnabled() { return enabled; }
	public static void setEnabled(boolean value) {
		if (enabled == value) return;
		enabled = value;
		WinterConfig.save();
	}
	public static void toggle() { setEnabled(!enabled); }

	public static void registerWorldRender() {
		WorldRenderEvents.END_MAIN.register(DrawPredict::render);
	}

	private static void render(WorldRenderContext context) {
		if (!enabled) return;
		AimAssist.Prediction prediction = AimAssist.getPrediction();
		if (prediction == null || prediction.target().isRemoved()) return;
		var mob = prediction.target();
		Vec3 position = prediction.position();
		double halfWidth = mob.getBbWidth() * 0.5;
		AABB box = new AABB(position.x - halfWidth, position.y, position.z - halfWidth,
			position.x + halfWidth, position.y + mob.getBbHeight(), position.z + halfWidth);
		EspBoxRenderer.draw(context, box, 0xFF65DFFF, 3.0F);
	}
}
