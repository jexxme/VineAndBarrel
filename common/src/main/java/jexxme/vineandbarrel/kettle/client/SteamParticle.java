package jexxme.vineandbarrel.kettle.client;

import jexxme.vineandbarrel.kettle.BrewKettleBlock;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Steam curling out of the kettle (K3, K4): a light wisp that starts small, swells and thins out as it rises (eight
 * frames, steam_0 to steam_7), fading in and out so it never pops, and fading away under a ceiling. Size and alpha
 * follow the partial tick, so the wisp grows smoothly in recordings too.
 */
public class SteamParticle extends TextureSheetParticle
{
	/** Half the quad's width at the start and at the end of its life, before the per-wisp size jitter. */
	private static final float START_SIZE = 0.10F;
	private static final float END_SIZE = 0.32F;

	private final SpriteSet sprites;
	private final float maxAlpha;
	private final float sizeJitter;
	private final float swayPhase;
	private final float swaySpeed;
	private final boolean mirrored;
	/** 1 while the wisp is free; falls to 0 once it runs into a ceiling, so it never pools there cut flat by the block. */
	private float ceilingFade = 1.0F;
	private float ceilingFadeO = 1.0F;
	/** Reused by the ceiling check every tick. */
	private final BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();

	protected SteamParticle(ClientLevel level, double x, double y, double z, double motionX, double motionY, double motionZ, float maxAlpha, SpriteSet sprites) {
		super(level, x, y, z);
		this.sprites = sprites;
		this.maxAlpha = maxAlpha;
		this.setSize(0.25F, 0.25F);
		this.lifetime = 40 + this.random.nextInt(24);
		this.friction = 0.94F;
		this.xd = motionX;
		this.yd = motionY + 0.012D + this.random.nextFloat() * 0.006D;
		this.zd = motionZ;
		this.sizeJitter = 0.85F + this.random.nextFloat() * 0.3F;
		this.swayPhase = this.random.nextFloat() * Mth.TWO_PI;
		this.swaySpeed = 0.12F + this.random.nextFloat() * 0.08F;
		this.mirrored = this.random.nextBoolean();
		this.alpha = 0.0F;
		this.setSpriteFromAge(sprites);
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
		this.setSpriteFromAge(this.sprites);
		this.ceilingFadeO = this.ceilingFade;
		if (this.ceilingFade < 1.0F || this.underCeiling()) {
			this.ceilingFade -= 0.15F;
			if (this.ceilingFade <= 0.0F) {
				this.remove();
				return;
			}
		}
		// warm air: a gentle lift that eases off, and a slow sideways sway so the wisps curl instead of rising straight
		float sway = Mth.sin(this.age * this.swaySpeed + this.swayPhase);
		float cosSway = Mth.cos(this.age * this.swaySpeed * 0.8F + this.swayPhase);
		this.xd += sway * 0.0012D;
		this.zd += cosSway * 0.0012D;
		this.yd += 0.0009D;
		this.move(this.xd, this.yd, this.zd);
		this.xd *= this.friction;
		this.yd *= this.friction;
		this.zd *= this.friction;
	}

	/**
	 * Whether the top of the wisp touches a block's collision box. Kettles never count: their shape always has the
	 * closed lid and knob, so a wisp rising from an open pot would otherwise fade out the moment it starts. Air and full
	 * blocks are answered without building the shape's box list; only partial blocks (slabs, stairs) walk their boxes.
	 */
	private boolean underCeiling() {
		double top = this.y + this.getQuadSize(0.0F) + 0.05D;
		BlockPos pos = this.probe.set(this.x, top, this.z);
		BlockState state = this.level.getBlockState(pos);
		if (state.isAir() || state.getBlock() instanceof BrewKettleBlock || state.getBlock() instanceof jexxme.vineandbarrel.kettle.BrewKettlePartBlock) {
			return false;
		}
		if (state.isCollisionShapeFullBlock(this.level, pos)) {
			return true;
		}
		VoxelShape shape = state.getCollisionShape(this.level, pos);
		if (shape.isEmpty()) {
			return false;
		}
		double lx = this.x - pos.getX();
		double ly = top - pos.getY();
		double lz = this.z - pos.getZ();
		for (AABB box : shape.toAabbs()) {
			if (box.contains(lx, ly, lz)) {
				return true;
			}
		}
		return false;
	}

	/** How far through its life the wisp is, 0 to 1, between ticks. */
	private float life(float partialTick) {
		return Mth.clamp((this.age + partialTick) / (float) this.lifetime, 0.0F, 1.0F);
	}

	@Override
	public float getQuadSize(float partialTick) {
		// swells quickly at first, then slowly (ease out)
		float t = this.life(partialTick);
		float grow = 1.0F - (1.0F - t) * (1.0F - t);
		return Mth.lerp(grow, START_SIZE, END_SIZE) * this.sizeJitter;
	}

	@Override
	public void render(VertexConsumer buffer, Camera camera, float partialTick) {
		// fades in over the first fifth, out over the last three fifths (the big late frames stay faint)
		float t = this.life(partialTick);
		float in = Mth.clamp(t / 0.2F, 0.0F, 1.0F);
		float out = Mth.clamp((1.0F - t) / 0.6F, 0.0F, 1.0F);
		float ceiling = Mth.lerp(partialTick, this.ceilingFadeO, this.ceilingFade);
		this.alpha = this.maxAlpha * smooth(in) * smooth(out) * Math.max(0.0F, ceiling);
		super.render(buffer, camera, partialTick);
	}

	private static float smooth(float x) {
		return x * x * (3.0F - 2.0F * x);
	}

	// half of the wisps mirrored, so neighbours don't look alike
	@Override
	protected float getU0() {
		return this.mirrored ? this.sprite.getU1() : this.sprite.getU0();
	}

	@Override
	protected float getU1() {
		return this.mirrored ? this.sprite.getU0() : this.sprite.getU1();
	}

	public static class Provider implements ParticleProvider<SimpleParticleType>
	{
		private final SpriteSet sprites;

		public Provider(SpriteSet sprites) {
			this.sprites = sprites;
		}

		@Override
		public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
			return new SteamParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, 0.62F + level.random.nextFloat() * 0.16F, this.sprites);
		}
	}
}
