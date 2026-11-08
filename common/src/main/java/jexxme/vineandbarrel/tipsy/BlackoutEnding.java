package jexxme.vineandbarrel.tipsy;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Where a blackout ends (playtest 1: "I want to go again, what's next?"). Each ending has a weight for the draw, the
 * pose the player wakes up in, a pool of souvenirs and its own wake-up lines; {@link BlackoutSpots} finds out whether
 * it is possible here and where exactly. {@link #RIGHT_HERE} is the fallback when nothing else is.
 */
public enum BlackoutEnding
{
	/** In your own bed, if it is in this dimension and still stands. */
	HOME(30, Pose.BED, 4, "minecraft:cornflower", "minecraft:bone", "minecraft:string", "minecraft:sweet_berries"),
	/** On the surface 20-60 blocks away. */
	IN_THE_OPEN(20, Pose.GROUND, 4, "minecraft:stick", "minecraft:dandelion", "minecraft:poppy", "minecraft:brown_mushroom"),
	/** Next to an animal, which stares at you. */
	FARMYARD(20, Pose.GROUND, 4, "minecraft:feather", "minecraft:egg", "minecraft:white_wool", "minecraft:carrot", "minecraft:lead"),
	/** On top of a bale. */
	IN_THE_HAY(15, Pose.GROUND, 4, "minecraft:wheat", "minecraft:wheat_seeds", "vineandbarrel:barley_seeds"),
	/** In front of the barrels of one of our taverns. */
	TAVERN(10, Pose.GROUND, 4, "vineandbarrel:mug", "vineandbarrel:wine_glass"),
	/** In a villager's bed, with the villager next to it shaking its head. */
	SOMEONE_ELSES_BED(10, Pose.BED, 4, "minecraft:bread", "minecraft:potato", "minecraft:flower_pot"),
	/** On a roof (or anything else high above its surroundings) nearby. */
	ON_THE_ROOF(8, Pose.GROUND, 4, "minecraft:feather", "minecraft:stick", "minecraft:vine"),
	/** Sitting in a brand new oak boat on calm water; the boat is yours. */
	IN_A_BOAT(8, Pose.SEAT, 4, "minecraft:cod", "minecraft:kelp", "minecraft:lily_pad"),
	/** Nothing else possible (the Nether, the End, sealed in): you wake up where you fell. */
	RIGHT_HERE(0, Pose.GROUND, 4);

	/** How the player lies while waking up; synced to every client in {@link TipsyRegistry#BLACKOUT_POSE}. */
	public enum Pose
	{
		/** On the ground: the camera tipped for the player, the body lying on its side for the others. */
		GROUND,
		/** In a bed, in vanilla's sleeping pose. */
		BED,
		/** Sitting in a boat. */
		SEAT;

		public static Pose byId(int id) {
			return id >= 0 && id < values().length ? values()[id] : GROUND;
		}
	}

	/** The endings that count for the Lost Weekend advancement (all but {@link #RIGHT_HERE}). */
	public static final List<BlackoutEnding> COLLECTABLE = Arrays.stream(values()).filter(e -> e != RIGHT_HERE).toList();

	private final int weight;
	private final Pose pose;
	private final int lines;
	private final List<Identifier> souvenirs;

	BlackoutEnding(int weight, Pose pose, int lines, String... souvenirs) {
		this.weight = weight;
		this.pose = pose;
		this.lines = lines;
		this.souvenirs = Arrays.stream(souvenirs).map(Identifier::parse).toList();
	}

	/** {@return the id used in saves, lang keys and the advancement criteria, e.g. {@code in_the_hay}} */
	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	public static Optional<BlackoutEnding> byId(@Nullable String id) {
		return Arrays.stream(values()).filter(e -> e.id().equals(id)).findFirst();
	}

	public int weight() {
		return weight;
	}

	public Pose pose() {
		return pose;
	}

	/**
	 * {@return whether this ending happens outdoors under the open sky (so never in the Nether or the End, and never
	 * inside anyone's build): in the open, the farmyard, the hay, the roof and the boat}
	 */
	public boolean needsOpenSky() {
		return this == IN_THE_OPEN || this == FARMYARD || this == IN_THE_HAY || this == ON_THE_ROOF || this == IN_A_BOAT;
	}

	/** {@return how many wake-up lines this ending has ({@code blackout.vineandbarrel.<id>.<n>})} */
	public int lineCount() {
		return lines;
	}

	public String lineKey(int line) {
		return "blackout.vineandbarrel." + id() + "." + line;
	}

	/** {@return the souvenir ids of this ending; {@link #RIGHT_HERE} draws from all of them} */
	public List<Identifier> souvenirs() {
		if (this == RIGHT_HERE) {
			return COLLECTABLE.stream().flatMap(e -> e.souvenirs.stream()).distinct().toList();
		}
		return souvenirs;
	}

	/** {@return one souvenir from this ending's pool (empty only if none of its items exist)} */
	public ItemStack souvenir(RandomSource random) {
		List<Identifier> pool = souvenirs();
		for (int tries = 0; tries < pool.size(); tries++) {
			Optional<Holder.Reference<Item>> item = BuiltInRegistries.ITEM.get(pool.get(random.nextInt(pool.size())));
			if (item.isPresent()) {
				return new ItemStack(item.get());
			}
		}
		return ItemStack.EMPTY;
	}
}
