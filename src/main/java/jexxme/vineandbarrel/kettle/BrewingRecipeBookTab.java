package jexxme.vineandbarrel.kettle;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/** The kettle recipe book's tabs (K9), like FD's cooking pot tabs (meals, drinks, misc). */
public enum BrewingRecipeBookTab implements StringRepresentable
{
	JUICES("juices"),
	WORTS("worts"),
	MISC("misc");

	public static final Codec<BrewingRecipeBookTab> CODEC = StringRepresentable.fromEnum(BrewingRecipeBookTab::values);

	private final String name;

	BrewingRecipeBookTab(String name) {
		this.name = name;
	}

	@Override
	public String getSerializedName() {
		return name;
	}
}
