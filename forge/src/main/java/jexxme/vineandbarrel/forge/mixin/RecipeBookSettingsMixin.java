package jexxme.vineandbarrel.forge.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import jexxme.vineandbarrel.forge.ForgeRecipeBook;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.stats.RecipeBookSettings;
import net.minecraft.world.inventory.RecipeBookType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Vanilla's settings packet without the kettle book's type, so a client with Vine & Barrel can join any server (see
 * {@link ForgeRecipeBook}). The kettle book's settings stay where Forge keeps every type's: in vanilla's map, saved
 * with the player under the keys the enum constant's {@code init} names. Only the packet leaves them out, and settings
 * read from it don't overwrite the ones a recipe book already has.
 */
@Mixin(RecipeBookSettings.class)
abstract class RecipeBookSettingsMixin implements ForgeRecipeBook.FromPacket
{
	@Shadow
	public abstract boolean isOpen(RecipeBookType type);

	@Shadow
	public abstract void setOpen(RecipeBookType type, boolean open);

	@Shadow
	public abstract boolean isFiltering(RecipeBookType type);

	@Shadow
	public abstract void setFiltering(RecipeBookType type, boolean filtering);

	@Unique
	private boolean vineandbarrel$brewingUnknown;

	@Override
	public boolean vineandbarrel$brewingUnknown() {
		return vineandbarrel$brewingUnknown;
	}

	@ModifyExpressionValue(method = "write(Lnet/minecraft/network/FriendlyByteBuf;)V",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/inventory/RecipeBookType;values()[Lnet/minecraft/world/inventory/RecipeBookType;"))
	private RecipeBookType[] vineandbarrel$typesInPacket(RecipeBookType[] types) {
		return ForgeRecipeBook.withoutBrewing(types);
	}

	/** Reading: the kettle's type takes nothing from the packet but still gets its entry in the map (closed, not filtering). */
	@WrapOperation(method = "read(Lnet/minecraft/network/FriendlyByteBuf;)Lnet/minecraft/stats/RecipeBookSettings;",
		at = @At(value = "INVOKE", remap = false, target = "Lnet/minecraft/network/FriendlyByteBuf;readBoolean()Z"))
	private static boolean vineandbarrel$notInPacket(FriendlyByteBuf buffer, Operation<Boolean> original, @Local RecipeBookType type) {
		return !ForgeRecipeBook.isBrewing(type) && original.call(buffer);
	}

	@Inject(method = "read(Lnet/minecraft/network/FriendlyByteBuf;)Lnet/minecraft/stats/RecipeBookSettings;", at = @At("RETURN"))
	private static void vineandbarrel$readFromPacket(FriendlyByteBuf buffer, CallbackInfoReturnable<RecipeBookSettings> callback) {
		((RecipeBookSettingsMixin) (Object) callback.getReturnValue()).vineandbarrel$brewingUnknown = true;
	}

	@Inject(method = "copy", at = @At("RETURN"))
	private void vineandbarrel$copyUnknown(CallbackInfoReturnable<RecipeBookSettings> callback) {
		((RecipeBookSettingsMixin) (Object) callback.getReturnValue()).vineandbarrel$brewingUnknown = vineandbarrel$brewingUnknown;
	}

	/** The client's book takes the settings of every recipe packet: the kettle book's stay as they are then. */
	@WrapMethod(method = "replaceFrom")
	private void vineandbarrel$keepBrewing(RecipeBookSettings other, Operation<Void> original) {
		if (!((ForgeRecipeBook.FromPacket) (Object) other).vineandbarrel$brewingUnknown()) {
			original.call(other);
			vineandbarrel$brewingUnknown = false;
			return;
		}
		RecipeBookType type = ForgeRecipeBook.brewingType();
		boolean open = isOpen(type);
		boolean filtering = isFiltering(type);
		original.call(other);
		setOpen(type, open);
		setFiltering(type, filtering);
	}
}
