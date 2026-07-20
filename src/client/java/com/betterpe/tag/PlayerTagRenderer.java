package com.betterpe.tag;

import com.betterpe.api.PlanetEarthApi;
import com.betterpe.api.model.Resident;
import com.betterpe.config.Config;
import com.betterpe.config.ConfigManager;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.text.Text;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Feature 2: shows [국가 | 마을] above nearby players. By default (tagCrosshairOnly) it
 * only tags whoever the crosshair is aimed at; with that off it tags everyone within
 * tagNearbyRadius instead. The name -> {@link Resident} cache is shared with
 * {@link com.betterpe.headcount.HeadcountManager} so both features reuse the same
 * rate-limit-friendly /resident lookups instead of querying twice.
 */
public final class PlayerTagRenderer {

	private static final Resident UNRESOLVED = new Resident();

	// name(lowercase) -> resolved resident; UNRESOLVED sentinel = looked up, no nation/town.
	private static final Map<String, Resident> RESIDENT_CACHE = new ConcurrentHashMap<>();
	private static final Set<String> PENDING = ConcurrentHashMap.newKeySet();

	private PlayerTagRenderer() {
	}

	public static void init() {
		WorldRenderEvents.AFTER_ENTITIES.register(PlayerTagRenderer::onRender);
	}

	private static void onRender(WorldRenderContext ctx) {
		Config cfg = ConfigManager.get();
		if (!cfg.modEnabled || !cfg.tagEnabled) {
			return;
		}
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player == null || mc.world == null) {
			return;
		}

		if (cfg.tagCrosshairOnly) {
			PlayerEntity target = raycastPlayer(mc, ctx.tickDelta(), cfg.tagMaxDistance);
			if (target != null) {
				renderTagFor(ctx, target, cfg);
			}
			return;
		}

		double r2 = cfg.tagNearbyRadius * cfg.tagNearbyRadius;
		for (PlayerEntity p : mc.world.getPlayers()) {
			if (p == mc.player || p.isSpectator()) {
				continue;
			}
			if (p.squaredDistanceTo(mc.player) <= r2) {
				renderTagFor(ctx, p, cfg);
			}
		}
	}

	private static void renderTagFor(WorldRenderContext ctx, PlayerEntity target, Config cfg) {
		String tag = resolveTag(target.getGameProfile().getName(), cfg);
		if (tag == null || tag.isEmpty()) {
			return;
		}
		drawBillboard(ctx, target, tag, cfg);
	}

	private static PlayerEntity raycastPlayer(MinecraftClient mc, float tickDelta, double maxDist) {
		Entity camEntity = mc.getCameraEntity() != null ? mc.getCameraEntity() : mc.player;
		if (camEntity == null) {
			return null;
		}
		Vec3d start = camEntity.getCameraPosVec(tickDelta);
		Vec3d dir = camEntity.getRotationVec(tickDelta);
		Vec3d end = start.add(dir.multiply(maxDist));
		Box box = camEntity.getBoundingBox().stretch(dir.multiply(maxDist)).expand(1.0);

		EntityHitResult hit = ProjectileUtil.raycast(
				camEntity, start, end, box,
				e -> e instanceof PlayerEntity && e != mc.player && !e.isSpectator(),
				maxDist * maxDist);

		if (hit != null && hit.getType() == HitResult.Type.ENTITY
				&& hit.getEntity() instanceof PlayerEntity p) {
			return p;
		}
		return null;
	}

	/**
	 * Cached resident info for a player name, or null while a lookup is still in flight
	 * (which this call kicks off if one hasn't already been started). Shared by the tag
	 * renderer and the headcount feature so both draw from the same cache.
	 */
	public static Resident ensureResident(String name) {
		String key = name.toLowerCase(Locale.ROOT);
		Resident cached = RESIDENT_CACHE.get(key);
		if (cached != null) {
			return cached;
		}
		if (PENDING.add(key)) {
			PlanetEarthApi.resident(name).thenAccept(res -> {
				RESIDENT_CACHE.put(key, res != null ? res : UNRESOLVED);
				PENDING.remove(key);
			}).exceptionally(t -> {
				PENDING.remove(key);
				return null;
			});
		}
		return null;
	}

	/** Returns the display tag, "" to hide, or null while pending. */
	private static String resolveTag(String name, Config cfg) {
		Resident res = ensureResident(name);
		if (res == null) {
			return null;
		}
		return buildTag(res, cfg);
	}

	private static String buildTag(Resident res, Config cfg) {
		boolean hasNation = res.hasNation();
		boolean hasTown = res.hasTown();
		if (!hasNation && !hasTown) {
			return cfg.tagHideUnaffiliated ? "" : "[무소속]";
		}
		StringBuilder sb = new StringBuilder("[");
		if (hasNation) {
			sb.append(res.nation);
		}
		if (hasNation && hasTown) {
			sb.append(" | ");
		}
		if (hasTown) {
			sb.append(res.town);
		}
		sb.append("]");
		return sb.toString();
	}

	private static void drawBillboard(WorldRenderContext ctx, Entity target, String tag, Config cfg) {
		MinecraftClient mc = MinecraftClient.getInstance();
		TextRenderer tr = mc.textRenderer;
		Camera camera = ctx.camera();
		MatrixStack matrices = ctx.matrixStack();
		VertexConsumerProvider consumers = ctx.consumers();
		if (matrices == null || consumers == null) {
			return;
		}

		float tickDelta = ctx.tickDelta();
		double px = target.prevX + (target.getX() - target.prevX) * tickDelta;
		double py = target.prevY + (target.getY() - target.prevY) * tickDelta;
		double pz = target.prevZ + (target.getZ() - target.prevZ) * tickDelta;
		double height = target.getHeight() + 0.5 + cfg.tagYOffset;

		Vec3d cam = camera.getPos();
		matrices.push();
		matrices.translate(px - cam.x, py + height - cam.y, pz - cam.z);
		matrices.multiply(camera.getRotation());
		matrices.scale(-0.025f, -0.025f, 0.025f);

		Matrix4f matrix = matrices.peek().getPositionMatrix();
		Text text = Text.literal(tag);
		float x = -tr.getWidth(text) / 2f;
		int bg = (int) (mc.options.getTextBackgroundOpacity(0.25f) * 255f) << 24;

		tr.draw(text, x, 0, 0xFFFFFF, false, matrix, consumers,
				TextRenderer.TextLayerType.SEE_THROUGH, bg, 0xF000F0);
		matrices.pop();
	}

	/** Drop cached tags (e.g. on world change) so they get re-resolved. */
	public static void clearCache() {
		RESIDENT_CACHE.clear();
		PENDING.clear();
	}
}
