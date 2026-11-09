package jexxme.vineandbarrel.watch;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The Brewer's path: the journey from vine to glass as a row of stations, each a "Watch it" scene, ticked by an
 * advancement. Read from {@code assets/<namespace>/watch/path.json} of every namespace and merged: Vine &amp; Barrel's
 * file sets the stations, other mods' files add branches, side scenes that hang under a station.
 *
 * @param stations the main line, in order
 * @param branches side scenes, each under one station
 */
public record WatchPath(List<Station> stations, List<Branch> branches)
{
	/** The file in each namespace's {@code watch} folder (not a scene). */
	public static final String FILE = "path.json";
	public static final WatchPath EMPTY = new WatchPath(List.of(), List.of());

	public static final Codec<WatchPath> CODEC = RecordCodecBuilder.create(inst -> inst.group(
		Station.CODEC.listOf().optionalFieldOf("stations", List.of()).forGetter(WatchPath::stations),
		Branch.CODEC.listOf().optionalFieldOf("branches", List.of()).forGetter(WatchPath::branches)
	).apply(inst, WatchPath::new));

	/**
	 * One station on the main line.
	 *
	 * @param verb        lang key of its short name ("Grow")
	 * @param scene       the scene it plays
	 * @param advancement the advancement that ticks it off; without one it is never ticked nor "next for you"
	 */
	public record Station(String verb, Identifier scene, Optional<Identifier> advancement)
	{
		public static final Codec<Station> CODEC = RecordCodecBuilder.create(inst -> inst.group(
			Codec.STRING.fieldOf("verb").forGetter(Station::verb),
			Identifier.CODEC.fieldOf("scene").forGetter(Station::scene),
			Identifier.CODEC.optionalFieldOf("advancement").forGetter(Station::advancement)
		).apply(inst, Station::new));
	}

	/**
	 * A side scene under a station, named by its scene's title.
	 *
	 * @param after    the scene of the station it hangs under
	 * @param scene    the scene it plays
	 * @param note     lang key of a short line under its name
	 * @param requires a mod that must be loaded for it to show
	 */
	public record Branch(Identifier after, Identifier scene, Optional<String> note, Optional<String> requires)
	{
		public static final Codec<Branch> CODEC = RecordCodecBuilder.create(inst -> inst.group(
			Identifier.CODEC.fieldOf("after").forGetter(Branch::after),
			Identifier.CODEC.fieldOf("scene").forGetter(Branch::scene),
			Codec.STRING.optionalFieldOf("note").forGetter(Branch::note),
			Codec.STRING.optionalFieldOf("requires").forGetter(Branch::requires)
		).apply(inst, Branch::new));
	}

	/**
	 * {@return this path with another namespace's file added}: its branches join; its stations only if this path has
	 * none yet (the main line is one mod's, so addons can't reorder it)
	 */
	public WatchPath with(WatchPath other) {
		List<Branch> joined = new ArrayList<>(branches);
		joined.addAll(other.branches);
		return new WatchPath(stations.isEmpty() ? other.stations : stations, List.copyOf(joined));
	}
}
