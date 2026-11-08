package jexxme.vineandbarrel.mixin;

import net.minecraft.world.entity.animal.bee.Bee;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** C2: lets {@link jexxme.vineandbarrel.entity.BeeGrowth} count a grown crop against the bee's pollination, like vanilla. */
@Mixin(Bee.class)
public interface BeeAccessor
{
	@Invoker("incrementNumCropsGrownSincePollination")
	void vineandbarrel$incrementCropsGrown();

	/** Read by the GameTests to check that a grown crop counts against the bee's pollination, like vanilla. */
	@Invoker("getCropsGrownSincePollination")
	int vineandbarrel$cropsGrown();
}
