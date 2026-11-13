package jexxme.vineandbarrel.mixin;

import com.mojang.datafixers.util.Pair;
import jexxme.vineandbarrel.world.village.TavernKeeper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.AcquirePoi;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * A villager looking for a job site can take a kettle he can walk up to anywhere beside it. Vanilla only counts a job
 * site he can get within two blocks of (counted along the axes) of the one block it is; a Big Kettle is that block's
 * corner of a kettle two blocks wide, and with that corner walled in or by a fire he could never take it, though he
 * could stand beside the rest of it. When vanilla finds no way, a safe way to a spot at a kettle
 * ({@link TavernKeeper#wayToKettle}) counts, for that kettle.
 */
@Mixin(AcquirePoi.class)
public abstract class AcquirePoiMixin
{
	@Inject(method = "findPathToPois", at = @At("RETURN"), cancellable = true)
	private static void vineandbarrel$besideAKettle(Mob body, Set<Pair<Holder<PoiType>, BlockPos>> pois, CallbackInfoReturnable<Path> cir) {
		Path found = cir.getReturnValue();
		if (!(body instanceof Villager) || found != null && found.canReach()) {
			return;
		}
		for (Pair<Holder<PoiType>, BlockPos> poi : pois) {
			if (poi.getFirst().is(TavernKeeper.JOB_SITE)) {
				Path way = TavernKeeper.wayToKettle(body, poi.getSecond());
				if (way != null) {
					List<Node> nodes = new ArrayList<>();
					for (int i = 0; i < way.getNodeCount(); i++) {
						nodes.add(way.getNode(i));
					}
					// the kettle as the target: the job site taken is the path's target
					cir.setReturnValue(new Path(nodes, poi.getSecond(), true));
					return;
				}
			}
		}
	}
}
