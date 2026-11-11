package jexxme.vineandbarrel.forge.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import jexxme.vineandbarrel.forge.ForgeRecipeBook;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ServerboundRecipeBookChangeSettingsPacket;
import net.minecraft.world.inventory.RecipeBookType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The book type in the packet that opens, closes and filters a recipe book: vanilla sends its place in the enum, which
 * for the kettle's type (and every type after it) is not the same on every side. Here the kettle's type is counted
 * last, so every other type has the number it has without Vine & Barrel. See {@link ForgeRecipeBook}.
 */
@Mixin(ServerboundRecipeBookChangeSettingsPacket.class)
abstract class ServerboundRecipeBookChangeSettingsPacketMixin
{
	@WrapOperation(method = "write",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/network/FriendlyByteBuf;writeEnum(Ljava/lang/Enum;)Lnet/minecraft/network/FriendlyByteBuf;"))
	private FriendlyByteBuf vineandbarrel$writeType(FriendlyByteBuf buffer, Enum<?> type, Operation<FriendlyByteBuf> original) {
		return buffer.writeVarInt(ForgeRecipeBook.wireIndex((RecipeBookType) type));
	}

	@WrapOperation(method = "<init>(Lnet/minecraft/network/FriendlyByteBuf;)V",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/network/FriendlyByteBuf;readEnum(Ljava/lang/Class;)Ljava/lang/Enum;"))
	private Enum<?> vineandbarrel$readType(FriendlyByteBuf buffer, Class<?> enumClass, Operation<Enum<?>> original) {
		return ForgeRecipeBook.typeAt(buffer.readVarInt());
	}
}
