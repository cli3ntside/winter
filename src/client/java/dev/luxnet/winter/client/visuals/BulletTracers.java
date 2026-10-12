package dev.luxnet.winter.client.visuals;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.luxnet.winter.client.WeaponCatalog;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.Iterator;

public final class BulletTracers {
	private static final long SIGNAL_TIMEOUT_NANOS = 2_000_000_000L;
	private static final double MAX_DISTANCE = 128.0;
	private static final double SIGNAL_DISTANCE = 5.0;
	private static final double START_DROP = 0.20;
	private static final int MAX_PENDING = 32;
	private static final int MAX_TRACERS = 32;
	private static final int MIN_LIFETIME_MS = 200;
	private static final int MAX_LIFETIME_MS = 3_000;
	private static final float MIN_THICKNESS = 0.08F;
	private static final float MAX_THICKNESS = 0.64F;
	private static final ArrayDeque<PendingShot> PENDING = new ArrayDeque<>();
	private static final ArrayDeque<Trace> TRACES = new ArrayDeque<>();
	private static boolean enabled;
	private static int lifetimeMs = 1_000;
	private static float thickness = 0.32F;

	private BulletTracers() { }

	public static void registerWorldRender() {
		WorldRenderEvents.END_MAIN.register(BulletTracers::render);
	}

	public static boolean isEnabled() { return enabled; }
	public static int getLifetimeMs() { return lifetimeMs; }
	public static float getThickness() { return thickness; }

	public static void setLifetimeMs(int value) {
		lifetimeMs = Math.max(MIN_LIFETIME_MS, Math.min(MAX_LIFETIME_MS, value));
	}

	public static void setThickness(float value) {
		thickness = Math.max(MIN_THICKNESS, Math.min(MAX_THICKNESS, value));
	}

	public static synchronized void toggle() {
		enabled = !enabled;
		if (!enabled) TRACES.clear();
	}

	public static synchronized void onOutgoingPacket(Packet<?> packet) {
		if (!(packet instanceof ServerboundUseItemPacket useItem)) return;
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null || minecraft.level == null || minecraft.screen != null) return;
		ItemStack item = minecraft.player.getItemInHand(useItem.getHand());
		String signal = WeaponCatalog.detectionSignal(item).orElse(null);
		if (signal == null) return;

		Vec3 start = minecraft.player.getEyePosition();
		Vec3 direction = minecraft.player.getViewVector(1.0F).normalize();
		HitResult hit = minecraft.player.pick(MAX_DISTANCE, 1.0F, false);
		double distance = hit.getType() == HitResult.Type.MISS ? MAX_DISTANCE : start.distanceTo(hit.getLocation());
		while (PENDING.size() >= MAX_PENDING) PENDING.removeFirst();
		PENDING.addLast(new PendingShot(WeaponCatalog.normalizedName(item), signal, start, direction, distance,
			System.nanoTime() + SIGNAL_TIMEOUT_NANOS));
	}

	/** Returns the weapon name when a pending local shot matches this sound or particle. */
	public static synchronized String observeSignal(String kind, String identifier, Vec3 position) {
		long now = System.nanoTime();
		Iterator<PendingShot> iterator = PENDING.iterator();
		while (iterator.hasNext()) {
			PendingShot shot = iterator.next();
			if (now > shot.expiresAt) {
				iterator.remove();
				continue;
			}
			String expected = shot.signal.substring(shot.signal.indexOf(':') + 1);
			boolean matches = shot.signal.startsWith(kind + ":")
				&& (expected.equals(identifier) || expected.startsWith(identifier + "|")
				|| expected.contains("|" + identifier + "|") || expected.endsWith("|" + identifier));
			if (!matches || distanceToSegment(position, shot.origin, shot.origin.add(shot.direction.scale(shot.length))) > SIGNAL_DISTANCE) continue;
			iterator.remove();
			if (enabled) {
				while (TRACES.size() >= MAX_TRACERS) TRACES.removeFirst();
				Vec3 start = shot.origin.add(0.0, -START_DROP, 0.0);
				Vec3 end = shot.origin.add(shot.direction.scale(shot.length));
				TRACES.addLast(new Trace(start, end, now));
			}
			return shot.weaponName;
		}
		return null;
	}

	private static void render(WorldRenderContext context) {
		if (!enabled || TRACES.isEmpty()) return;
		long now = System.nanoTime();
		TRACES.removeIf(trace -> now - trace.createdAt >= lifetimeMs * 1_000_000L);
		if (TRACES.isEmpty()) return;
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null || minecraft.level == null) return;
		Vec3 camera = minecraft.gameRenderer.getMainCamera().position();
		VertexConsumer quads = context.consumers().getBuffer(RenderTypes.debugQuads());
		for (Trace trace : TRACES) {
			float fade = Math.max(0.0F, 1.0F - (float) (now - trace.createdAt) / (lifetimeMs * 1_000_000.0F));
			Vec3 from = trace.start.subtract(camera);
			Vec3 to = trace.end.subtract(camera);
			Vec3 direction = to.subtract(from).normalize();
			drawGlow(quads, context, from, to, direction, fade);
		}
	}

	private static void drawGlow(VertexConsumer quads, WorldRenderContext context, Vec3 from, Vec3 to,
							 Vec3 direction, float fade) {
		Vec3 axis = Math.abs(direction.dot(new Vec3(0.0, 1.0, 0.0))) > 0.98
			? new Vec3(1.0, 0.0, 0.0) : new Vec3(0.0, 1.0, 0.0);
		Vec3 right = direction.cross(axis).normalize();
		Vec3 up = right.cross(direction).normalize();
		drawPrism(quads, context, from, to, right, up, thickness * 0.95F, 0x3377DFFF, 0x33FFFFFF, fade);
		drawPrism(quads, context, from, to, right, up, thickness * 0.58F, 0xAA9BE8FF, 0xAAFFFFFF, fade);
		drawPrism(quads, context, from, to, right, up, thickness * 0.25F, 0xFFFFFFFF, 0xFFFFFFFF, fade);
	}

	private static void drawPrism(VertexConsumer quads, WorldRenderContext context, Vec3 from, Vec3 to,
							   Vec3 right, Vec3 up, float size, int startColor, int endColor, float fade) {
		double half = size * 0.5;
		Vec3 r = right.scale(half);
		Vec3 u = up.scale(half);
		Vec3[] start = {from.add(r).add(u), from.subtract(r).add(u), from.subtract(r).subtract(u), from.add(r).subtract(u)};
		Vec3[] end = {to.add(r).add(u), to.subtract(r).add(u), to.subtract(r).subtract(u), to.add(r).subtract(u)};
		quad(quads, context, start[0], start[1], end[1], end[0], startColor, endColor, fade);
		quad(quads, context, start[3], start[2], end[2], end[3], startColor, endColor, fade);
		quad(quads, context, start[0], start[3], end[3], end[0], startColor, endColor, fade);
		quad(quads, context, start[2], start[1], end[1], end[2], startColor, endColor, fade);
		quad(quads, context, start[0], start[1], start[2], start[3], startColor, startColor, fade);
		quad(quads, context, end[3], end[2], end[1], end[0], endColor, endColor, fade);
	}

	private static void quad(VertexConsumer quads, WorldRenderContext context, Vec3 a, Vec3 b, Vec3 c, Vec3 d,
						  int startColor, int endColor, float fade) {
		var pose = context.matrices().last();
		vertex(quads, pose, a, startColor, fade);
		vertex(quads, pose, b, startColor, fade);
		vertex(quads, pose, c, endColor, fade);
		vertex(quads, pose, d, endColor, fade);
	}

	private static void vertex(VertexConsumer consumer, com.mojang.blaze3d.vertex.PoseStack.Pose pose,
						   Vec3 point, int color, float fade) {
		consumer.addVertex(pose, (float) point.x, (float) point.y, (float) point.z).setColor(withFade(color, fade));
	}

	private static double distanceToSegment(Vec3 point, Vec3 start, Vec3 end) {
		Vec3 segment = end.subtract(start);
		double lengthSqr = segment.lengthSqr();
		if (lengthSqr == 0.0) return point.distanceTo(start);
		double t = Math.max(0.0, Math.min(1.0, point.subtract(start).dot(segment) / lengthSqr));
		return point.distanceTo(start.add(segment.scale(t)));
	}

	private static int withFade(int color, float fade) {
		int alpha = Math.round(((color >>> 24) & 0xFF) * fade);
		return (alpha << 24) | (color & 0x00FFFFFF);
	}

	private record PendingShot(String weaponName, String signal, Vec3 origin, Vec3 direction, double length, long expiresAt) { }
	private record Trace(Vec3 start, Vec3 end, long createdAt) { }
}
