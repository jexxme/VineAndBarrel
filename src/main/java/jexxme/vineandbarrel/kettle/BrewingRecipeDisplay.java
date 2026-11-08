package jexxme.vineandbarrel.kettle;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;

import java.util.List;

/** How a brewing recipe shows in the kettle's recipe book (FD's {@code CookingPotRecipeDisplay}, MIT). */
public record BrewingRecipeDisplay(List<SlotDisplay> ingredients, SlotDisplay container, SlotDisplay result, SlotDisplay craftingStation, int duration, float experience) implements RecipeDisplay
{
	public static final MapCodec<BrewingRecipeDisplay> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
		SlotDisplay.CODEC.listOf().fieldOf("ingredients").forGetter(BrewingRecipeDisplay::ingredients),
		SlotDisplay.CODEC.fieldOf("container").forGetter(BrewingRecipeDisplay::container),
		SlotDisplay.CODEC.fieldOf("result").forGetter(BrewingRecipeDisplay::result),
		SlotDisplay.CODEC.fieldOf("crafting_station").forGetter(BrewingRecipeDisplay::craftingStation),
		Codec.INT.fieldOf("duration").forGetter(BrewingRecipeDisplay::duration),
		Codec.FLOAT.fieldOf("experience").forGetter(BrewingRecipeDisplay::experience)
	).apply(instance, BrewingRecipeDisplay::new));

	public static final StreamCodec<RegistryFriendlyByteBuf, BrewingRecipeDisplay> STREAM_CODEC = StreamCodec.composite(
		SlotDisplay.STREAM_CODEC.apply(ByteBufCodecs.list()), BrewingRecipeDisplay::ingredients,
		SlotDisplay.STREAM_CODEC, BrewingRecipeDisplay::container,
		SlotDisplay.STREAM_CODEC, BrewingRecipeDisplay::result,
		SlotDisplay.STREAM_CODEC, BrewingRecipeDisplay::craftingStation,
		ByteBufCodecs.INT, BrewingRecipeDisplay::duration,
		ByteBufCodecs.FLOAT, BrewingRecipeDisplay::experience,
		BrewingRecipeDisplay::new);

	public static final Type<BrewingRecipeDisplay> TYPE = new Type<>(MAP_CODEC, STREAM_CODEC);

	@Override
	public Type<BrewingRecipeDisplay> type() {
		return TYPE;
	}

	@Override
	public boolean isEnabled(FeatureFlagSet enabledFeatures) {
		return ingredients.stream().allMatch(display -> display.isEnabled(enabledFeatures)) && RecipeDisplay.super.isEnabled(enabledFeatures);
	}
}
