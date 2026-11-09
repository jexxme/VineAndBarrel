package jexxme.vineandbarrel.mixin;

import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleResources;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The particle providers every loaded mod registered, so a "Watch it" scene can run real particles in its own engine. */
@Mixin(ParticleEngine.class)
public interface ParticleEngineAccessor
{
	@Accessor("resourceManager")
	ParticleResources vineandbarrel$resources();
}
