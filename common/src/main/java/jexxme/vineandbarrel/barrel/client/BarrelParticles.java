package jexxme.vineandbarrel.barrel.client;

import jexxme.vineandbarrel.barrel.BarrelRegistry;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/** The barrel's and the drinks' particles: the airlock's bubbles (B5), the coloured tap drip (B6), cheers sparkles (D2). */
public final class BarrelParticles
{
	private BarrelParticles() {
	}

	/** B5: a little bubble rising out of the airlock and popping after a moment, like the brewing stand's. */
	public static class FermentBubble extends SingleQuadParticle
	{
		FermentBubble(ClientLevel level, double x, double y, double z, double ySpeed, SpriteSet sprites, RandomSource random) {
			super(level, x, y, z, sprites.first());
			this.lifetime = 10 + random.nextInt(8);
			this.gravity = 0.0F;
			this.hasPhysics = false;
			this.quadSize = 0.05F + random.nextFloat() * 0.03F;
			this.xd = (random.nextFloat() - 0.5F) * 0.004F;
			this.yd = Math.max(0.01, ySpeed) + random.nextFloat() * 0.01F;
			this.zd = (random.nextFloat() - 0.5F) * 0.004F;
		}

		@Override
		protected Layer getLayer() {
			return Layer.TRANSLUCENT;
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
			this.yd *= 0.9;
			this.move(this.xd, this.yd, this.zd);
		}

		public static class Provider implements ParticleProvider<SimpleParticleType>
		{
			private final SpriteSet sprites;

			public Provider(SpriteSet sprites) {
				this.sprites = sprites;
			}

			@Override
			public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed, RandomSource random) {
				return new FermentBubble(level, x, y, z, ySpeed, this.sprites, random);
			}
		}
	}

	/**
	 * B6: a drop that hangs at the tap for a moment, falls and lands with a soft drip, like honey from a beehive. Sent
	 * with a speed, it is a drop of a pour or a splash instead: it flies at once and lands without a sound (the pour or
	 * the splash has its own).
	 */
	public static class TapDrip extends SingleQuadParticle
	{
		private static final int HANG_TICKS = 20;
		private final int hangTicks;
		private final boolean silent;
		private boolean landed;

		TapDrip(ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed, ColorParticleOption color, SpriteSet sprites) {
			super(level, x, y, z, sprites.first());
			this.setColor(color.getRed(), color.getGreen(), color.getBlue());
			this.silent = xSpeed != 0.0 || ySpeed != 0.0 || zSpeed != 0.0;
			this.hangTicks = silent ? 0 : HANG_TICKS;
			this.lifetime = hangTicks + (silent ? 30 : 60);
			this.gravity = 0.0F;
			this.quadSize = silent ? 0.045F : 0.06F;
			this.setSize(0.02F, 0.02F);
			this.xd = xSpeed;
			this.yd = ySpeed;
			this.zd = zSpeed;
		}

		@Override
		protected Layer getLayer() {
			return Layer.OPAQUE;
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
			if (this.age > hangTicks) {
				this.yd -= 0.03;
			}
			this.move(this.xd, this.yd, this.zd);
			if (this.onGround && !landed) {
				landed = true;
				this.xd = 0.0;
				this.zd = 0.0;
				if (!silent) {
					this.level.playLocalSound(BlockPos.containing(this.x, this.y, this.z), BarrelRegistry.BARREL_DRIP.get(), SoundSource.BLOCKS,
						0.3F, 0.9F + this.random.nextFloat() * 0.2F, false);
				}
				this.lifetime = this.age + 4;
				this.quadSize *= 1.3F;
			}
		}

		public static class Provider implements ParticleProvider<ColorParticleOption>
		{
			private final SpriteSet sprites;

			public Provider(SpriteSet sprites) {
				this.sprites = sprites;
			}

			@Override
			public Particle createParticle(ColorParticleOption options, ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed, RandomSource random) {
				return new TapDrip(level, x, y, z, xSpeed, ySpeed, zSpeed, options, this.sprites);
			}
		}
	}

	/** D2: a golden sparkle drifting up from clinking glasses. */
	public static class CheersSparkle extends SingleQuadParticle
	{
		CheersSparkle(ClientLevel level, double x, double y, double z, double xSpeed, double zSpeed, SpriteSet sprites, RandomSource random) {
			super(level, x, y, z, sprites.first());
			this.lifetime = 16 + random.nextInt(10);
			this.gravity = 0.0F;
			this.hasPhysics = false;
			this.quadSize = 0.12F + random.nextFloat() * 0.04F;
			this.xd = xSpeed + (random.nextFloat() - 0.5F) * 0.02F;
			this.yd = 0.03F + random.nextFloat() * 0.02F;
			this.zd = zSpeed + (random.nextFloat() - 0.5F) * 0.02F;
		}

		@Override
		protected Layer getLayer() {
			return Layer.TRANSLUCENT;
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
			this.yd *= 0.92;
			this.move(this.xd, this.yd, this.zd);
			int left = this.lifetime - this.age;
			if (left < 8) {
				this.alpha = left / 8.0F;
			}
		}

		public static class Provider implements ParticleProvider<SimpleParticleType>
		{
			private final SpriteSet sprites;

			public Provider(SpriteSet sprites) {
				this.sprites = sprites;
			}

			@Override
			public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed, RandomSource random) {
				return new CheersSparkle(level, x, y, z, xSpeed, zSpeed, this.sprites, random);
			}
		}
	}
}
