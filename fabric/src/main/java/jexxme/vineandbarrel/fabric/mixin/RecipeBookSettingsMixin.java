package jexxme.vineandbarrel.fabric.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import jexxme.vineandbarrel.fabric.FabricRecipeBook;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.stats.RecipeBookSettings;
import net.minecraft.world.inventory.RecipeBookType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

/**
 * The kettle book's settings (open, filtering) next to vanilla's four: read, changed, copied and saved with the player
 * like theirs (NeoForge keeps every type's settings in vanilla's map instead). Vanilla's map and its settings packet
 * are left exactly as they are (they never hold the kettle's type), so a client with Vine & Barrel can join any server:
 * the kettle book's settings travel in our own payload instead, and settings that came from vanilla's packet (which
 * can't carry them) don't overwrite the known ones. See {@link FabricRecipeBook}.
 */
@Mixin(RecipeBookSettings.class)
abstract class RecipeBookSettingsMixin implements FabricRecipeBook.BrewingSettings
{
	@Shadow
	@Final
	private Map<RecipeBookType, ?> states;

	/** False while unknown (a fresh object, or one decoded from vanilla's packet). */
	@Unique
	private boolean vineandbarrel$brewingKnown;
	@Unique
	private boolean vineandbarrel$brewingOpen;
	@Unique
	private boolean vineandbarrel$brewingFiltering;

	@Override
	public boolean vineandbarrel$brewingKnown() {
		return vineandbarrel$brewingKnown;
	}

	@Override
	public boolean vineandbarrel$brewingOpen() {
		return vineandbarrel$brewingOpen;
	}

	@Override
	public boolean vineandbarrel$brewingFiltering() {
		return vineandbarrel$brewingFiltering;
	}

	@Override
	public void vineandbarrel$setBrewing(boolean open, boolean filtering) {
		vineandbarrel$brewingKnown = true;
		vineandbarrel$brewingOpen = open;
		vineandbarrel$brewingFiltering = filtering;
	}

	// ------------------------------------------------------------------ vanilla's map and packet: never the kettle's type

	@Unique
	private static RecipeBookType[] vineandbarrel$withoutBrewing(RecipeBookType[] types) {
		int count = 0;
		for (RecipeBookType type : types) {
			if (!FabricRecipeBook.isBrewing(type)) {
				count++;
			}
		}
		RecipeBookType[] others = new RecipeBookType[count];
		int next = 0;
		for (RecipeBookType type : types) {
			if (!FabricRecipeBook.isBrewing(type)) {
				others[next++] = type;
			}
		}
		return others;
	}

	@ModifyExpressionValue(method = "read(Lnet/minecraft/network/FriendlyByteBuf;)Lnet/minecraft/stats/RecipeBookSettings;",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/inventory/RecipeBookType;values()[Lnet/minecraft/world/inventory/RecipeBookType;"))
	private static RecipeBookType[] vineandbarrel$typesInPacket(RecipeBookType[] types) {
		return vineandbarrel$withoutBrewing(types);
	}

	@ModifyExpressionValue(method = {"write(Lnet/minecraft/network/FriendlyByteBuf;)V", "copy", "replaceFrom"},
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/inventory/RecipeBookType;values()[Lnet/minecraft/world/inventory/RecipeBookType;"))
	private RecipeBookType[] vineandbarrel$typesInMap(RecipeBookType[] types) {
		return vineandbarrel$withoutBrewing(types);
	}

	/** A new settings object starts with an entry for every constant of the enum: ours goes, it lives in the fields above. */
	@Inject(method = "<init>(Ljava/util/Map;)V", at = @At("TAIL"))
	private void vineandbarrel$noBrewingInMap(CallbackInfo ci) {
		states.keySet().removeIf(FabricRecipeBook::isBrewing);
	}

	// ------------------------------------------------------------------ the kettle book's own two flags

	@Inject(method = "isOpen", at = @At("HEAD"), cancellable = true)
	private void vineandbarrel$isBrewingOpen(RecipeBookType type, CallbackInfoReturnable<Boolean> callback) {
		if (FabricRecipeBook.isBrewing(type)) {
			callback.setReturnValue(vineandbarrel$brewingOpen);
		}
	}

	@Inject(method = "setOpen", at = @At("HEAD"), cancellable = true)
	private void vineandbarrel$setBrewingOpen(RecipeBookType type, boolean open, CallbackInfo ci) {
		if (FabricRecipeBook.isBrewing(type)) {
			vineandbarrel$brewingKnown = true;
			vineandbarrel$brewingOpen = open;
			ci.cancel();
		}
	}

	@Inject(method = "isFiltering", at = @At("HEAD"), cancellable = true)
	private void vineandbarrel$isBrewingFiltering(RecipeBookType type, CallbackInfoReturnable<Boolean> callback) {
		if (FabricRecipeBook.isBrewing(type)) {
			callback.setReturnValue(vineandbarrel$brewingFiltering);
		}
	}

	@Inject(method = "setFiltering", at = @At("HEAD"), cancellable = true)
	private void vineandbarrel$setBrewingFiltering(RecipeBookType type, boolean filtering, CallbackInfo ci) {
		if (FabricRecipeBook.isBrewing(type)) {
			vineandbarrel$brewingKnown = true;
			vineandbarrel$brewingFiltering = filtering;
			ci.cancel();
		}
	}

	@Inject(method = "copy", at = @At("RETURN"))
	private void vineandbarrel$copyBrewing(CallbackInfoReturnable<RecipeBookSettings> callback) {
		if (vineandbarrel$brewingKnown) {
			((FabricRecipeBook.BrewingSettings) (Object) callback.getReturnValue()).vineandbarrel$setBrewing(vineandbarrel$brewingOpen, vineandbarrel$brewingFiltering);
		}
	}

	@Inject(method = "replaceFrom", at = @At("TAIL"))
	private void vineandbarrel$replaceBrewing(RecipeBookSettings other, CallbackInfo ci) {
		FabricRecipeBook.BrewingSettings theirs = (FabricRecipeBook.BrewingSettings) (Object) other;
		if (theirs.vineandbarrel$brewingKnown()) {
			vineandbarrel$setBrewing(theirs.vineandbarrel$brewingOpen(), theirs.vineandbarrel$brewingFiltering());
		}
	}

	// ------------------------------------------------------------------ saved with the player (never sent)

	@Inject(method = "read(Lnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/stats/RecipeBookSettings;", at = @At("RETURN"))
	private static void vineandbarrel$loadBrewing(CompoundTag tag, CallbackInfoReturnable<RecipeBookSettings> callback) {
		((FabricRecipeBook.BrewingSettings) (Object) callback.getReturnValue())
			.vineandbarrel$setBrewing(tag.getBoolean(FabricRecipeBook.TAG_OPEN), tag.getBoolean(FabricRecipeBook.TAG_FILTERING));
	}

	@Inject(method = "write(Lnet/minecraft/nbt/CompoundTag;)V", at = @At("TAIL"))
	private void vineandbarrel$saveBrewing(CompoundTag tag, CallbackInfo ci) {
		tag.putBoolean(FabricRecipeBook.TAG_OPEN, vineandbarrel$brewingOpen);
		tag.putBoolean(FabricRecipeBook.TAG_FILTERING, vineandbarrel$brewingFiltering);
	}
}
