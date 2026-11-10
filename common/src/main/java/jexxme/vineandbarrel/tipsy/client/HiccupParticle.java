package jexxme.vineandbarrel.tipsy.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;

/** A little hiccup bubble: pops out in front of the face, drifts up with a slight wobble and fades. */
public class HiccupParticle extends TextureSheetParticle
{
	private static final int FADE_TICKS = 8;

	protected HiccupParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites, RandomSource random) {
		super(level, x, y, z);
		// The first sprite (1.21.1 has no SpriteSet#first)
		this.setSprite(sprites.get(0, 1));
		this.lifetime = 14 + random.nextInt(6);
		this.gravity = 0.0F;
		this.hasPhysics = false;
		// A small bubble: the hiccup is in the jolt and the sound, the bubble only marks it
		this.quadSize = 0.07F + random.nextFloat() * 0.02F;
		this.xd = (random.nextFloat() - 0.5F) * 0.01F;
		this.yd = 0.025F + random.nextFloat() * 0.01F;
		this.zd = (random.nextFloat() - 0.5F) * 0.01F;
	}

	@Override
	public ParticleRenderType getRenderType() {
		return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
	}

	@Override
	public void tick() {
		this.xo = this.x;
		this.yo = this.y;
		this.zo = this.z;
		if (this.age++ >= this.lifetime) {
			this.remove();
			return;
		}
		this.xd += Math.sin(this.age * 0.6) * 0.0015;
		this.yd *= 0.96;
		this.move(this.xd, this.yd, this.zd);
		int left = this.lifetime - this.age;
		if (left < FADE_TICKS) {
			this.alpha = left / (float) FADE_TICKS;
		}
	}

	public static class Provider implements ParticleProvider<SimpleParticleType>
	{
		private final SpriteSet sprites;

		public Provider(SpriteSet sprites) {
			this.sprites = sprites;
		}

		@Override
		public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
			return new HiccupParticle(level, x, y, z, this.sprites, level.random);
		}
	}
}
