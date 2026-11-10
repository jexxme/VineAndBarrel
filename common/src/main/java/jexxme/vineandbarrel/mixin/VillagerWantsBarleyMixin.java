package jexxme.vineandbarrel.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import jexxme.vineandbarrel.registry.VBItems;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Villagers pick up barley, as they pick up wheat, and farmers barley seeds, as they pick up wheat seeds (26.x: the
 * item tag {@code minecraft:villager_picks_up}; on this Minecraft what a villager picks up is a fixed set in its code
 * plus a fixed set per profession).
 */
@Mixin(Villager.class)
public abstract class VillagerWantsBarleyMixin
{
	@ModifyExpressionValue(method = "wantsToPickUp", at = @At(value = "INVOKE", target = "Ljava/util/Set;contains(Ljava/lang/Object;)Z", ordinal = 0))
	private boolean vineandbarrel$wantsBarley(boolean wanted, ItemStack stack) {
		return wanted || stack.is(VBItems.BARLEY.get())
			|| stack.is(VBItems.BARLEY_SEEDS.get()) && ((Villager) (Object) this).getVillagerData().getProfession() == VillagerProfession.FARMER;
	}
}
