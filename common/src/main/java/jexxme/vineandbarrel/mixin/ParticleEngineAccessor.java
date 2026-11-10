package jexxme.vineandbarrel.mixin;

import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Makes a particle through the providers every loaded mod registered, without adding it to the world's particles: a
 * "Watch it" scene keeps, ticks and draws its own real particles.
 */
@Mixin(ParticleEngine.class)
public interface ParticleEngineAccessor
{
	@Invoker("makeParticle")
	<T extends ParticleOptions> @Nullable Particle vineandbarrel$makeParticle(T options, double x, double y, double z, double dx, double dy, double dz);
}
