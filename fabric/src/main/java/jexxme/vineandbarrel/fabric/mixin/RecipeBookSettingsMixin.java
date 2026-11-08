package jexxme.vineandbarrel.fabric.mixin;

import com.mojang.serialization.MapCodec;
import jexxme.vineandbarrel.fabric.FabricRecipeBook;
import net.minecraft.stats.RecipeBookSettings;
import net.minecraft.world.inventory.RecipeBookType;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.UnaryOperator;

/**
 * The kettle book's settings (open, filtering) next to vanilla's four fixed ones: read, changed, copied and saved with
 * the player like theirs (NeoForge keeps every type's settings in a map instead). Vanilla's settings packet is left
 * exactly as it is, so a client with Vine & Barrel can join any server: the kettle book's settings travel in our own
 * payload instead, and settings that came from vanilla's packet (which can't carry them) don't overwrite the known
 * ones. See {@link FabricRecipeBook}.
 */
@Mixin(RecipeBookSettings.class)
abstract class RecipeBookSettingsMixin implements FabricRecipeBook.BrewingSettings
{
	@Shadow
	@Final
	@Mutable
	public static MapCodec<RecipeBookSettings> MAP_CODEC;

	/** The kettle book's settings, or null while unknown (a fresh object, or one decoded from vanilla's packet). */
	@Unique
	private RecipeBookSettings.@Nullable TypeSettings vineandbarrel$brewing;

	@Override
	public RecipeBookSettings.@Nullable TypeSettings vineandbarrel$knownBrewing() {
		return vineandbarrel$brewing;
	}

	@Override
	public void vineandbarrel$setBrewing(RecipeBookSettings.@Nullable TypeSettings settings) {
		vineandbarrel$brewing = settings;
	}

	@Inject(method = "<clinit>", at = @At("TAIL"))
	private static void vineandbarrel$savedWithThePlayer(CallbackInfo ci) {
		MAP_CODEC = FabricRecipeBook.withBrewing(MAP_CODEC);
	}

	@Inject(method = "getSettings", at = @At("HEAD"), cancellable = true)
	private void vineandbarrel$getBrewing(RecipeBookType type, CallbackInfoReturnable<RecipeBookSettings.TypeSettings> callback) {
		if (type.name().equals(FabricRecipeBook.BREWING)) {
			callback.setReturnValue(vineandbarrel$brewing());
		}
	}

	@Inject(method = "updateSettings", at = @At("HEAD"), cancellable = true)
	private void vineandbarrel$updateBrewing(RecipeBookType type, UnaryOperator<RecipeBookSettings.TypeSettings> update, CallbackInfo ci) {
		if (type.name().equals(FabricRecipeBook.BREWING)) {
			vineandbarrel$brewing = update.apply(vineandbarrel$brewing());
			ci.cancel();
		}
	}

	@Inject(method = "copy", at = @At("RETURN"))
	private void vineandbarrel$copyBrewing(CallbackInfoReturnable<RecipeBookSettings> callback) {
		((FabricRecipeBook.BrewingSettings) (Object) callback.getReturnValue()).vineandbarrel$setBrewing(vineandbarrel$brewing);
	}

	@Inject(method = "replaceFrom", at = @At("TAIL"))
	private void vineandbarrel$replaceBrewing(RecipeBookSettings other, CallbackInfo ci) {
		RecipeBookSettings.TypeSettings theirs = ((FabricRecipeBook.BrewingSettings) (Object) other).vineandbarrel$knownBrewing();
		if (theirs != null) {
			vineandbarrel$brewing = theirs;
		}
	}
}
