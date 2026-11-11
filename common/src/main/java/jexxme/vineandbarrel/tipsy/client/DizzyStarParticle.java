package jexxme.vineandbarrel.tipsy.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * A gold star circling above the head of a blacked-out player, cartoon style. Spawned at the middle of the circle; it
 * flies its round itself, twinkling between its two frames, and fades in and out. One spawns every
 * {@link #SPAWN_EVERY} ticks, so they follow each other around the circle evenly.
 */
public class DizzyStarParticle extends SingleQuadParticle
{
	public static final int LIFETIME = 30;
	public static final int SPAWN_EVERY = 6;
	private static final float RADIUS = 0.32F;
	/** One round per lifetime. */
	private static final float TURN = Mth.TWO_PI / LIFETIME;
	private static final int FADE = 5;
	private static final int TWINKLE = 3;

	private final SpriteSet sprites;
	private final double cx, cy, cz;
	private final int twinkleOffset;

	protected DizzyStarParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites, RandomSource random) {
		super(level, x + RADIUS, y, z, sprites.get(0, 1));
		this.sprites = sprites;
		this.cx = x;
		this.cy = y;
		this.cz = z;
		this.lifetime = LIFETIME;
		this.gravity = 0.0F;
		this.hasPhysics = false;
		this.quadSize = 0.085F;
		this.alpha = 0.0F;
		this.twinkleOffset = random.nextInt(TWINKLE * 2);
		place();
		this.xo = this.x;
		this.yo = this.y;
		this.zo = this.z;
	}

	private void place() {
		float angle = this.age * TURN;
		this.x = cx + Mth.cos(angle) * RADIUS;
		this.z = cz + Mth.sin(angle) * RADIUS;
		// A gentle wave as it goes round
		this.y = cy + Mth.sin(angle * 2.0F) * 0.04F;
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
		if (this.lifetime > this.age + FADE && !BlackoutClient.starsCircle(cx, cy, cz)) {
			// Whoever they circled is getting up (or came to): they fade out now instead of finishing the round
			this.lifetime = this.age + FADE;
		}
		place();
		this.setSprite(this.sprites.get(((this.age + twinkleOffset) / TWINKLE) % 2, 1));
		int left = this.lifetime - this.age;
		this.alpha = Mth.clamp(Math.min(this.age, left) / (float) FADE, 0.0F, 1.0F);
	}

	/** Stars shine: always fully lit, even at night. */
	@Override
	protected int getLightCoords(float a) {
		return 0xF000F0;
	}

	public static class Provider implements ParticleProvider<SimpleParticleType>
	{
		private final SpriteSet sprites;

		public Provider(SpriteSet sprites) {
			this.sprites = sprites;
		}

		@Override
		public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed, RandomSource random) {
			return new DizzyStarParticle(level, x, y, z, this.sprites, random);
		}
	}
}
