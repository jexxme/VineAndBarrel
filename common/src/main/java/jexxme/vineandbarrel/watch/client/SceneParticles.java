package jexxme.vineandbarrel.watch.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.Tesselator;
import jexxme.vineandbarrel.VineAndBarrel;
import jexxme.vineandbarrel.mixin.ParticleEngineAccessor;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.particles.ParticleOptions;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A scene's own particles: real ones, made by the providers every loaded mod registered, but kept, ticked and drawn
 * here instead of in the world's particle engine (which on this Minecraft version can't be built a second time: it
 * owns the particle atlas). Drawn the way the engine draws its own, sheet by sheet, into whatever is being drawn to.
 */
final class SceneParticles
{
	/** The engine's order: opaque sheets first, the translucent one after them. */
	private static final List<ParticleRenderType> RENDER_ORDER = List.of(
		ParticleRenderType.TERRAIN_SHEET,
		ParticleRenderType.PARTICLE_SHEET_OPAQUE,
		ParticleRenderType.PARTICLE_SHEET_LIT,
		ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT,
		ParticleRenderType.CUSTOM
	);
	/** As many as the engine keeps of one sheet. */
	private static final int MAX_PARTICLES_PER_LAYER = 16384;

	private final Map<ParticleRenderType, List<Particle>> particles = new LinkedHashMap<>();
	/** Made since the last tick: like the engine's, they join (and show) at the end of the tick, and tick from the next. */
	private final List<Particle> particlesToAdd = new ArrayList<>();
	private boolean failed;

	void clearParticles() {
		particles.clear();
		particlesToAdd.clear();
	}

	/** {@return the particle made, if its provider made one} (so its maker can colour it) */
	@Nullable Particle createParticle(ParticleOptions options, double x, double y, double z, double dx, double dy, double dz) {
		Particle particle;
		try {
			particle = ((ParticleEngineAccessor) Minecraft.getInstance().particleEngine).vineandbarrel$makeParticle(options, x, y, z, dx, dy, dz);
		} catch (RuntimeException e) {
			warn("make", e);
			return null;
		}
		if (particle != null) {
			particlesToAdd.add(particle);
		}
		return particle;
	}

	void tick() {
		for (List<Particle> sheet : particles.values()) {
			for (Iterator<Particle> it = sheet.iterator(); it.hasNext(); ) {
				Particle particle = it.next();
				try {
					particle.tick();
				} catch (RuntimeException e) {
					warn("tick", e);
					it.remove();
					continue;
				}
				if (!particle.isAlive()) {
					it.remove();
				}
			}
		}
		for (Particle particle : particlesToAdd) {
			List<Particle> sheet = particles.computeIfAbsent(particle.getRenderType(), type -> new ArrayList<>());
			if (sheet.size() >= MAX_PARTICLES_PER_LAYER) {
				sheet.remove(0);
			}
			sheet.add(particle);
		}
		particlesToAdd.clear();
	}

	/**
	 * Draws them as the particle engine does: billboards facing {@code camera}, relative to its position. The view's
	 * rotation is expected on the model-view matrix, as in the world.
	 */
	void render(Camera camera, float partialTick) {
		if (particles.values().stream().allMatch(List::isEmpty)) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		LightTexture lightTexture = minecraft.gameRenderer.lightTexture();
		lightTexture.turnOnLightLayer();
		RenderSystem.enableDepthTest();
		for (ParticleRenderType type : RENDER_ORDER) {
			render(type, camera, partialTick);
		}
		for (ParticleRenderType type : particles.keySet()) {
			// another mod's own sheet, after vanilla's
			if (!RENDER_ORDER.contains(type)) {
				render(type, camera, partialTick);
			}
		}
		RenderSystem.depthMask(true);
		RenderSystem.disableBlend();
		lightTexture.turnOffLightLayer();
	}

	private void render(ParticleRenderType type, Camera camera, float partialTick) {
		List<Particle> sheet = particles.get(type);
		if (sheet == null || sheet.isEmpty() || type == ParticleRenderType.NO_RENDER) {
			return;
		}
		RenderSystem.setShader(GameRenderer::getParticleShader);
		Tesselator tesselator = Tesselator.getInstance();
		BufferBuilder buffer = tesselator.getBuilder();
		type.begin(buffer, Minecraft.getInstance().getTextureManager());
		for (Particle particle : sheet) {
			try {
				particle.render(buffer, camera, partialTick);
			} catch (RuntimeException e) {
				warn("draw", e);
			}
		}
		type.end(tesselator);
	}

	private void warn(String what, RuntimeException e) {
		if (!failed) {
			failed = true;
			VineAndBarrel.LOGGER.warn("Watch it: can't {} a particle in a scene", what, e);
		}
	}
}
