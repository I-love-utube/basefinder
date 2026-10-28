package com.basefinder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * Draws translucent boxes with bright edge bars around clusters, visible through walls.
 *
 * <p>Follows the structure of Fabric's "Rendering in the World" reference example (custom filled-box
 * pipeline with the depth test removed, extraction phase then drawing phase). Outlines are built from
 * 12 thin filled boxes per cluster, so only the documented filled-box pipeline is needed.
 */
public final class HighlightRenderer {
	private static final RenderPipeline THROUGH_WALLS = RenderPipelines.register(
			RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
					.withLocation(Identifier.fromNamespaceAndPath(BaseFinderClient.MOD_ID, "pipeline/highlight_through_walls"))
					.withDepthStencilState(Optional.empty())
					.build());

	private static final Vector4f COLOR_MODULATOR = new Vector4f(1f, 1f, 1f, 1f);
	private static final Vector3f MODEL_OFFSET = new Vector3f();
	private static final Matrix4f TEXTURE_MATRIX = new Matrix4f();
	private static final StagedVertexBuffer BUFFER =
			new StagedVertexBuffer(() -> "BaseFinder Highlights", RenderType.SMALL_BUFFER_SIZE);

	private static final float FILL_ALPHA = 0.10f;
	private static final float EDGE_ALPHA = 0.95f;
	private static final float EDGE_THICKNESS = 0.06f;
	private static final float PAD = 0.15f;
	private static final int MAX_INDIVIDUAL_BLOCKS = 600;

	/** Immutable render state: built during extraction, read during drawing. */
	private record BoxState(float x0, float y0, float z0, float x1, float y1, float z1,
							float r, float g, float b, float a) {
	}

	private static volatile List<BoxState> boxes = List.of();
	private static long lastKey = Long.MIN_VALUE;
	private static int frames;

	private HighlightRenderer() {
	}

	public static void register() {
		LevelExtractionEvents.END_EXTRACTION.register(HighlightRenderer::extract);
		LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register(HighlightRenderer::draw);
	}

	public static void close() {
		BUFFER.close();
	}

	// ---- extraction phase -------------------------------------------------------------------

	private static void extract(LevelExtractionContext context) {
		Minecraft mc = Minecraft.getInstance();
		Config cfg = Config.get();
		if (!cfg.enabled || mc.player == null || mc.level == null) {
			boxes = List.of();
			lastKey = Long.MIN_VALUE;
			return;
		}

		// Rebuild when something changed, and at least every 30 frames so "nearest N" follows the player.
		long key = Store.version * 31 + Tracker.VERSION.get() * 17 + Config.version;
		if (key == lastKey && ++frames % 30 != 0) {
			return;
		}
		lastKey = key;

		String dimension = mc.level.dimension().toString();
		double px = mc.player.getX();
		double py = mc.player.getY();
		double pz = mc.player.getZ();

		List<Cluster> visible = new ArrayList<>();
		for (Cluster c : Store.current()) {
			if (!c.ignored && c.dimension.equals(dimension) && cfg.isEnabled(c.dominant())) {
				visible.add(c);
			}
		}
		visible.sort(Comparator.comparingDouble(c -> c.distanceTo(px, py, pz)));
		if (visible.size() > cfg.maxRenderedClusters) {
			visible = new ArrayList<>(visible.subList(0, cfg.maxRenderedClusters));
		}

		List<BoxState> out = new ArrayList<>();
		for (Cluster c : visible) {
			int rgb = cfg.color(c.dominant());
			addCluster(out, c, rgb);
		}

		if (cfg.markIndividualBlocks) {
			int budget = MAX_INDIVIDUAL_BLOCKS;
			for (Hit h : Tracker.HITS.values()) {
				if (budget <= 0) {
					break;
				}
				if (!cfg.isEnabled(h.category())) {
					continue;
				}
				for (Cluster c : visible) {
					if (contains(c, h)) {
						float[] rgb = rgb(cfg.color(h.category()));
						out.add(new BoxState(h.x() - 0.04f, h.y() - 0.04f, h.z() - 0.04f,
								h.x() + 1.04f, h.y() + 1.04f, h.z() + 1.04f, rgb[0], rgb[1], rgb[2], 0.35f));
						budget--;
						break;
					}
				}
			}
		}
		boxes = out;
	}

	private static boolean contains(Cluster c, Hit h) {
		return h.x() >= c.minX && h.x() <= c.maxX
				&& h.y() >= c.minY && h.y() <= c.maxY
				&& h.z() >= c.minZ && h.z() <= c.maxZ;
	}

	private static float[] rgb(int rgb) {
		return new float[] {((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f};
	}

	private static void addCluster(List<BoxState> out, Cluster c, int color) {
		float[] col = rgb(color);
		float r = col[0], g = col[1], b = col[2];
		float x0 = c.minX - PAD, y0 = c.minY - PAD, z0 = c.minZ - PAD;
		float x1 = c.maxX + 1 + PAD, y1 = c.maxY + 1 + PAD, z1 = c.maxZ + 1 + PAD;
		float t = EDGE_THICKNESS;

		out.add(new BoxState(x0, y0, z0, x1, y1, z1, r, g, b, FILL_ALPHA));

		// 4 edges along X, 4 along Y, 4 along Z.
		for (float y : new float[] {y0, y1}) {
			for (float z : new float[] {z0, z1}) {
				out.add(new BoxState(x0, y - t, z - t, x1, y + t, z + t, r, g, b, EDGE_ALPHA));
			}
		}
		for (float x : new float[] {x0, x1}) {
			for (float z : new float[] {z0, z1}) {
				out.add(new BoxState(x - t, y0, z - t, x + t, y1, z + t, r, g, b, EDGE_ALPHA));
			}
		}
		for (float x : new float[] {x0, x1}) {
			for (float y : new float[] {y0, y1}) {
				out.add(new BoxState(x - t, y - t, z0, x + t, y + t, z1, r, g, b, EDGE_ALPHA));
			}
		}
	}

	// ---- drawing phase ----------------------------------------------------------------------

	private static void draw(LevelRenderContext context) {
		List<BoxState> list = boxes;
		if (list.isEmpty()) {
			return;
		}

		RenderPipeline pipeline = THROUGH_WALLS;
		VertexFormat formatBinding = pipeline.getVertexFormatBinding(0);
		assert formatBinding != null;

		PrimitiveTopology primitive = pipeline.getPrimitiveTopology();
		StagedVertexBuffer.Draw draw = BUFFER.appendDraw(formatBinding, primitive,
				primitive == PrimitiveTopology.QUADS ? RenderSystem.getProjectionType().vertexSorting() : null);

		PoseStack matrices = context.poseStack();
		Vec3 camera = context.levelState().cameraRenderState.pos;
		matrices.pushPose();
		matrices.translate(-camera.x, -camera.y, -camera.z);
		VertexConsumer builder = BUFFER.getVertexBuilder(draw);
		for (BoxState b : list) {
			filledBox(matrices.last().pose(), builder, b);
		}
		matrices.popPose();

		BUFFER.upload();
		StagedVertexBuffer.ExecuteInfo info = BUFFER.getExecuteInfo(draw);
		if (info != null) {
			execute(Minecraft.getInstance(), info, pipeline);
		}
		BUFFER.endFrame();
	}

	private static void filledBox(Matrix4fc m, VertexConsumer v, BoxState b) {
		float minX = b.x0(), minY = b.y0(), minZ = b.z0(), maxX = b.x1(), maxY = b.y1(), maxZ = b.z1();
		float r = b.r(), g = b.g(), bl = b.b(), a = b.a();

		// Front
		v.addVertex(m, minX, minY, maxZ).setColor(r, g, bl, a);
		v.addVertex(m, maxX, minY, maxZ).setColor(r, g, bl, a);
		v.addVertex(m, maxX, maxY, maxZ).setColor(r, g, bl, a);
		v.addVertex(m, minX, maxY, maxZ).setColor(r, g, bl, a);
		// Back
		v.addVertex(m, maxX, minY, minZ).setColor(r, g, bl, a);
		v.addVertex(m, minX, minY, minZ).setColor(r, g, bl, a);
		v.addVertex(m, minX, maxY, minZ).setColor(r, g, bl, a);
		v.addVertex(m, maxX, maxY, minZ).setColor(r, g, bl, a);
		// Left
		v.addVertex(m, minX, minY, minZ).setColor(r, g, bl, a);
		v.addVertex(m, minX, minY, maxZ).setColor(r, g, bl, a);
		v.addVertex(m, minX, maxY, maxZ).setColor(r, g, bl, a);
		v.addVertex(m, minX, maxY, minZ).setColor(r, g, bl, a);
		// Right
		v.addVertex(m, maxX, minY, maxZ).setColor(r, g, bl, a);
		v.addVertex(m, maxX, minY, minZ).setColor(r, g, bl, a);
		v.addVertex(m, maxX, maxY, minZ).setColor(r, g, bl, a);
		v.addVertex(m, maxX, maxY, maxZ).setColor(r, g, bl, a);
		// Top
		v.addVertex(m, minX, maxY, maxZ).setColor(r, g, bl, a);
		v.addVertex(m, maxX, maxY, maxZ).setColor(r, g, bl, a);
		v.addVertex(m, maxX, maxY, minZ).setColor(r, g, bl, a);
		v.addVertex(m, minX, maxY, minZ).setColor(r, g, bl, a);
		// Bottom
		v.addVertex(m, minX, minY, minZ).setColor(r, g, bl, a);
		v.addVertex(m, maxX, minY, minZ).setColor(r, g, bl, a);
		v.addVertex(m, maxX, minY, maxZ).setColor(r, g, bl, a);
		v.addVertex(m, minX, minY, maxZ).setColor(r, g, bl, a);
	}

	private static void execute(Minecraft client, StagedVertexBuffer.ExecuteInfo info, RenderPipeline pipeline) {
		GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms()
				.writeTransform(RenderSystem.getModelViewMatrixCopy(), COLOR_MODULATOR, MODEL_OFFSET, TEXTURE_MATRIX);

		RenderTarget mainTarget = client.gameRenderer.mainRenderTarget();
		GpuTextureView colorTexture = mainTarget.getColorTextureView();
		assert colorTexture != null;

		try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
				() -> BaseFinderClient.MOD_ID + " highlights", colorTexture, Optional.empty(),
				mainTarget.getDepthTextureView(), OptionalDouble.empty())) {
			pass.setPipeline(pipeline);
			RenderSystem.bindDefaultUniforms(pass);
			pass.setUniform("DynamicTransforms", dynamicTransforms);
			pass.setVertexBuffer(0, info.vertexBuffer().slice());
			pass.setIndexBuffer(info.indexBuffer(), info.indexType());
			pass.drawIndexed(info.indexCount(), 1, info.firstIndex(), info.baseVertex(), 0);
		}
	}
}
