package dev.luxnet.winter.client.visuals;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.gui.Font;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class EspBoxRenderer {
	private static final RenderPipeline THROUGH_WALL_LINES = RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
		.withLocation(Identifier.fromNamespaceAndPath("winter", "esp_box_lines"))
		.withVertexFormat(DefaultVertexFormat.POSITION_COLOR_LINE_WIDTH, VertexFormat.Mode.LINES)
		.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
		.withDepthWrite(false)
		.withBlend(BlendFunction.TRANSLUCENT)
		.build();
	private static final RenderType BOX_LINES = RenderType.create("winter_esp_box_lines",
		RenderSetup.builder(THROUGH_WALL_LINES).bufferSize(2048).createRenderSetup());

	private EspBoxRenderer() { }

	public static void draw(WorldRenderContext context, AABB worldBox, int color, float width) {
		Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().position();
		AABB box = worldBox.move(-camera.x, -camera.y, -camera.z).inflate(0.002);
		VertexConsumer lines = context.consumers().getBuffer(BOX_LINES);
		var pose = context.matrices().last();
		drawLine(lines, pose, box.minX, box.minY, box.minZ, box.maxX, box.minY, box.minZ, color, width);
		drawLine(lines, pose, box.maxX, box.minY, box.minZ, box.maxX, box.minY, box.maxZ, color, width);
		drawLine(lines, pose, box.maxX, box.minY, box.maxZ, box.minX, box.minY, box.maxZ, color, width);
		drawLine(lines, pose, box.minX, box.minY, box.maxZ, box.minX, box.minY, box.minZ, color, width);
		drawLine(lines, pose, box.minX, box.maxY, box.minZ, box.maxX, box.maxY, box.minZ, color, width);
		drawLine(lines, pose, box.maxX, box.maxY, box.minZ, box.maxX, box.maxY, box.maxZ, color, width);
		drawLine(lines, pose, box.maxX, box.maxY, box.maxZ, box.minX, box.maxY, box.maxZ, color, width);
		drawLine(lines, pose, box.minX, box.maxY, box.maxZ, box.minX, box.maxY, box.minZ, color, width);
		drawLine(lines, pose, box.minX, box.minY, box.minZ, box.minX, box.maxY, box.minZ, color, width);
		drawLine(lines, pose, box.maxX, box.minY, box.minZ, box.maxX, box.maxY, box.minZ, color, width);
		drawLine(lines, pose, box.maxX, box.minY, box.maxZ, box.maxX, box.maxY, box.maxZ, color, width);
		drawLine(lines, pose, box.minX, box.minY, box.maxZ, box.minX, box.maxY, box.maxZ, color, width);
	}

	static void drawLabel(WorldRenderContext context, Vec3 worldPosition, String text, int color) {
		var minecraft = Minecraft.getInstance();
		Vec3 camera = minecraft.gameRenderer.getMainCamera().position();
		var matrices = context.matrices();
		matrices.pushPose();
		matrices.translate(worldPosition.x - camera.x, worldPosition.y - camera.y, worldPosition.z - camera.z);
		matrices.mulPose(minecraft.gameRenderer.getMainCamera().rotation());
		matrices.scale(-0.025F, -0.025F, 0.025F);
		Font font = minecraft.font;
		font.drawInBatch(text, -font.width(text) / 2.0F, 0.0F, color, true, matrices.last().pose(),
			context.consumers(), Font.DisplayMode.SEE_THROUGH, 0x70000000, 0xF000F0);
		matrices.popPose();
	}

	private static void drawLine(VertexConsumer consumer, com.mojang.blaze3d.vertex.PoseStack.Pose pose,
							  double x1, double y1, double z1, double x2, double y2, double z2,
							  int color, float width) {
		float dx = (float) (x2 - x1);
		float dy = (float) (y2 - y1);
		float dz = (float) (z2 - z1);
		consumer.addVertex(pose, (float) x1, (float) y1, (float) z1)
			.setColor(color).setNormal(pose, dx, dy, dz).setLineWidth(width);
		consumer.addVertex(pose, (float) x2, (float) y2, (float) z2)
			.setColor(color).setNormal(pose, dx, dy, dz).setLineWidth(width);
	}
}
