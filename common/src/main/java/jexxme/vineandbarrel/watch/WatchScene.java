package jexxme.vineandbarrel.watch;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import jexxme.vineandbarrel.platform.Services;
import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A "Watch it" scene, as its data file describes it ({@code assets/<namespace>/watch/<name>.json}, so a resource pack
 * can change or add scenes): a small stage of real blocks, then steps, each with one caption line and a few actions on a
 * tick timeline. The client plays it ({@code ScenePlayer}); captions and the title are lang keys, so scenes translate.
 * A file may list {@code "requires": ["modid"]}: then it is only read where those mods are loaded.
 *
 * @param title   lang key of the scene's title
 * @param icon    the item that stands for the scene ("Also watch" at the end of another scene)
 * @param width   the stage's size along x (blocks)
 * @param depth   the stage's size along z (blocks)
 * @param floor   the block the stage's floor (y = 0) is made of, if any
 * @param blocks  blocks standing on the stage from the start
 * @param view    where it is looked at from at first
 * @param opens   which items' tooltips, which blocks in the world and which JEI categories open it
 * @param also    the scenes offered at its end ("Also watch")
 * @param steps   the steps, played one after the other
 * @param needs   what the scene uses, for the "What you need" rail (items or parts of real recipes)
 * @param recipe  the aging recipe the scene is written for, if it is about one: asked for another base, vessel or barrel,
 *                the scene plays that recipe instead (every part of this one it names becomes that one's)
 */
public record WatchScene(String title, Holder<Item> icon, int width, int depth, Optional<BlockState> floor, List<Placed> blocks, View view,
						 Opens opens, List<ResourceLocation> also, List<Step> steps, List<Either<Holder<Item>, RecipePart>> needs,
						 Optional<ResourceLocation> recipe)
{
	/** A block state as a string, the way commands write it: {@code vineandbarrel:aging_barrel[facing=south,fill=4]}. */
	public static final Codec<BlockState> BLOCK_STATE = Codec.STRING.comapFlatMap(WatchScene::parseState, WatchScene::writeState);

	/** An item: its id, or a part of a real recipe ({@code {"recipe": ..., "part": ...}}). */
	public static final Codec<Either<Holder<Item>, RecipePart>> ITEM_REF = Codec.either(BuiltInRegistries.ITEM.holderByNameCodec(), RecipePart.CODEC);

	public static final Codec<WatchScene> CODEC = RecordCodecBuilder.create(inst -> inst.group(
		Codec.STRING.fieldOf("title").forGetter(WatchScene::title),
		BuiltInRegistries.ITEM.holderByNameCodec().fieldOf("icon").forGetter(WatchScene::icon),
		ExtraCodecs.intRange(1, 7).fieldOf("width").forGetter(WatchScene::width),
		ExtraCodecs.intRange(1, 7).fieldOf("depth").forGetter(WatchScene::depth),
		BLOCK_STATE.optionalFieldOf("floor").forGetter(WatchScene::floor),
		Placed.CODEC.listOf().optionalFieldOf("blocks", List.of()).forGetter(WatchScene::blocks),
		View.CODEC.optionalFieldOf("view", View.DEFAULT).forGetter(WatchScene::view),
		Opens.CODEC.optionalFieldOf("opens", Opens.NONE).forGetter(WatchScene::opens),
		ResourceLocation.CODEC.listOf().optionalFieldOf("also", List.of()).forGetter(WatchScene::also),
		Step.CODEC.listOf().fieldOf("steps").forGetter(WatchScene::steps),
		ITEM_REF.listOf().optionalFieldOf("needs", List.of()).forGetter(WatchScene::needs),
		ResourceLocation.CODEC.optionalFieldOf("recipe").forGetter(WatchScene::recipe)
	).apply(inst, WatchScene::new));

	private static DataResult<BlockState> parseState(String text) {
		try {
			return DataResult.success(BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), text, false).blockState());
		} catch (CommandSyntaxException e) {
			return DataResult.error(() -> "Bad block state '" + text + "': " + e.getMessage());
		}
	}

	private static String writeState(BlockState state) {
		return BlockStateParser.serialize(state);
	}

	/**
	 * {@return whether a scene file's {@code requires} mods (ids) are all loaded} A scene of another mod's blocks names
	 * them there, so it is only read where that mod is.
	 */
	public static boolean requirementsMet(JsonElement json) {
		if (json.isJsonObject() && json.getAsJsonObject().get("requires") instanceof JsonArray mods) {
			for (JsonElement mod : mods) {
				if (!Services.PLATFORM.isModLoaded(mod.getAsString())) {
					return false;
				}
			}
		}
		return true;
	}

	/** {@return the total length of the scene in ticks} */
	public int totalTicks() {
		return steps.stream().mapToInt(Step::ticks).sum();
	}

	/**
	 * Where the scene is looked at from at first: Minecraft's yaw (0 looks south) and the pitch (looking down); and how
	 * high above the floor things happen ({@code height}, blocks), so the stage is framed to fit them.
	 */
	public record View(float yaw, float pitch, float height)
	{
		public static final View DEFAULT = new View(135.0F, 30.0F, 1.9F);

		public static final Codec<View> CODEC = RecordCodecBuilder.create(inst -> inst.group(
			Codec.FLOAT.optionalFieldOf("yaw", DEFAULT.yaw()).forGetter(View::yaw),
			Codec.floatRange(5.0F, 80.0F).optionalFieldOf("pitch", DEFAULT.pitch()).forGetter(View::pitch),
			Codec.floatRange(1.0F, 6.0F).optionalFieldOf("height", DEFAULT.height()).forGetter(View::height)
		).apply(inst, View::new));
	}

	/**
	 * What opens a scene. {@code items}: item ids, {@code #tags}, or {@code @aging_bases} (every base and bucket an aging
	 * recipe takes) and {@code @drinks} (every drink). {@code blocks}: a block in the world, in a state, opens the scene
	 * at a step, with a line saying what that block is doing now. {@code jei}: JEI categories that get a button for it.
	 */
	public record Opens(List<String> items, List<Entry> blocks, List<ResourceLocation> jei)
	{
		public static final Opens NONE = new Opens(List.of(), List.of(), List.of());

		public static final Codec<Opens> CODEC = RecordCodecBuilder.create(inst -> inst.group(
			Codec.STRING.listOf().optionalFieldOf("items", List.of()).forGetter(Opens::items),
			Entry.CODEC.listOf().optionalFieldOf("blocks", List.of()).forGetter(Opens::blocks),
			ResourceLocation.CODEC.listOf().optionalFieldOf("jei", List.of()).forGetter(Opens::jei)
		).apply(inst, Opens::new));
	}

	/**
	 * A block in the world that opens a scene: {@code block} in a state that has these {@code when} property values
	 * (all of them; none = any state), and if {@code heated} is given, with a kettle's heat under it or not; at step
	 * {@code step} (1 = the first), with the lang key {@code now} of a line saying what the block is doing. The first
	 * entry that fits wins.
	 */
	public record Entry(ResourceLocation block, Map<String, String> when, Optional<Boolean> heated, int step, Optional<String> now)
	{
		public static final Codec<Entry> CODEC = RecordCodecBuilder.create(inst -> inst.group(
			ResourceLocation.CODEC.fieldOf("block").forGetter(Entry::block),
			Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("when", Map.of()).forGetter(Entry::when),
			Codec.BOOL.optionalFieldOf("heated").forGetter(Entry::heated),
			ExtraCodecs.POSITIVE_INT.optionalFieldOf("step", 1).forGetter(Entry::step),
			Codec.STRING.optionalFieldOf("now").forGetter(Entry::now)
		).apply(inst, Entry::new));

		/** {@return whether a block in the world in this state fits} */
		public boolean fits(BlockState state) {
			if (!BuiltInRegistries.BLOCK.getKey(state.getBlock()).equals(block)) {
				return false;
			}
			for (Map.Entry<String, String> property : when.entrySet()) {
				Property<?> p = state.getBlock().getStateDefinition().getProperty(property.getKey());
				if (p == null || !valueName(state, p).equals(property.getValue())) {
					return false;
				}
			}
			return true;
		}

		private static <T extends Comparable<T>> String valueName(BlockState state, Property<T> property) {
			return property.getName(state.getValue(property));
		}
	}

	/** A block at a stage position (the floor is y = 0, so blocks on it stand at y = 1). */
	public record Placed(BlockPos pos, BlockState block)
	{
		public static final Codec<Placed> CODEC = RecordCodecBuilder.create(inst -> inst.group(
			BlockPos.CODEC.fieldOf("pos").forGetter(Placed::pos),
			BLOCK_STATE.fieldOf("block").forGetter(Placed::block)
		).apply(inst, Placed::new));
	}

	/**
	 * One step: what the caption says and what happens, for {@code ticks} ticks.
	 *
	 * @param caption  lang key of the one caption line
	 * @param ticks    how long the step plays
	 * @param actions  what happens, each at its tick into the step
	 * @param with     what the caption's {@code %s} stand for, in order: an item or a recipe's part (its name), or an aging
	 *                 recipe's {@code fermenting_time} or {@code aging_time} (in days)
	 * @param variants other lines for the same step, for what the scene is played for: the first whose item fits wins
	 */
	public record Step(String caption, int ticks, List<Action> actions, Optional<Anchor> anchor,
					   List<Either<Holder<Item>, RecipePart>> with, List<CaptionVariant> variants)
	{
		public static final Codec<Step> CODEC = RecordCodecBuilder.create(inst -> inst.group(
			Codec.STRING.fieldOf("caption").forGetter(Step::caption),
			ExtraCodecs.intRange(1, 20 * 60).fieldOf("ticks").forGetter(Step::ticks),
			Action.CODEC.listOf().optionalFieldOf("actions", List.of()).forGetter(Step::actions),
			Anchor.CODEC.optionalFieldOf("anchor").forGetter(Step::anchor),
			ITEM_REF.listOf().optionalFieldOf("with", List.of()).forGetter(Step::with),
			CaptionVariant.CODEC.listOf().optionalFieldOf("variants", List.of()).forGetter(Step::variants)
		).apply(inst, Step::new));

		/** {@return every lang key this step may show} */
		public List<String> captions() {
			List<String> keys = new java.util.ArrayList<>();
			keys.add(caption);
			variants.forEach(variant -> keys.add(variant.caption()));
			return keys;
		}
	}

	/**
	 * Another line for a step: when the item {@code of} names (an item, or a part of the recipe the scene plays, such as
	 * its {@code base} or {@code vessel}) is one of {@code items} (ids or {@code #tags}), the step says {@code caption}.
	 * So a barrel scene played for a wort says "Pour in the wort." and one for a beer says to tap a mug.
	 */
	public record CaptionVariant(Either<Holder<Item>, RecipePart> of, List<String> items, String caption)
	{
		public static final Codec<CaptionVariant> CODEC = RecordCodecBuilder.create(inst -> inst.group(
			ITEM_REF.fieldOf("of").forGetter(CaptionVariant::of),
			Codec.STRING.listOf().fieldOf("items").forGetter(CaptionVariant::items),
			Codec.STRING.fieldOf("caption").forGetter(CaptionVariant::caption)
		).apply(inst, CaptionVariant::new));

		/** {@return whether this item is one of the variant's} */
		public boolean fits(net.minecraft.world.item.ItemStack stack) {
			for (String selector : items) {
				if (selector.startsWith("#")) {
					ResourceLocation tag = ResourceLocation.tryParse(selector.substring(1));
					if (tag != null && stack.is(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM, tag))) {
						return true;
					}
				} else if (BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(selector)) {
					return true;
				}
			}
			return false;
		}
	}

	/**
	 * What a step's caption points at: a point on the stage (its block gets the outline), or a figure (what it holds).
	 * Without one, a step points where its pointer does.
	 */
	public record Anchor(Optional<Vec3> pos, Optional<String> figure)
	{
		public static final Codec<Anchor> CODEC = Codec.either(Vec3.CODEC, Codec.STRING).xmap(
			either -> either.map(pos -> new Anchor(Optional.of(pos), Optional.empty()), id -> new Anchor(Optional.empty(), Optional.of(id))),
			anchor -> anchor.pos().<Either<Vec3, String>>map(Either::left).orElseGet(() -> Either.right(anchor.figure().orElse("brewer"))));
	}

	/** Something that happens in a step, starting {@link #at()} ticks into it. */
	public sealed interface Action
	{
		// the action's own fields stand beside its "type" (a map codec's codec is read in place)
		Codec<Action> CODEC = ActionType.CODEC.dispatch("type", Action::type, type -> type.codec().codec());

		int at();

		ActionType type();

		/** {@return the tick this action is over} (the same as {@link #at()} for actions that happen at once) */
		default int end() {
			return at();
		}
	}

	/** A block set onto the stage; {@code drop} lets it come down from above, the way Ponder's blocks do. */
	public record Place(int at, BlockPos pos, BlockState block, boolean drop) implements Action
	{
		public static final int DROP_TICKS = 8;

		static final MapCodec<Place> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
			atField(),
			BlockPos.CODEC.fieldOf("pos").forGetter(Place::pos),
			BLOCK_STATE.fieldOf("block").forGetter(Place::block),
			Codec.BOOL.optionalFieldOf("drop", true).forGetter(Place::drop)
		).apply(inst, Place::new));

		@Override
		public ActionType type() {
			return ActionType.PLACE;
		}

		@Override
		public int end() {
			return drop ? at + DROP_TICKS : at;
		}
	}

	/** A block changes its state at once (a campfire lit, a bung swapped), or is taken away ({@code minecraft:air}). */
	public record SetBlock(int at, BlockPos pos, BlockState block) implements Action
	{
		static final MapCodec<SetBlock> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
			atField(),
			BlockPos.CODEC.fieldOf("pos").forGetter(SetBlock::pos),
			BLOCK_STATE.fieldOf("block").forGetter(SetBlock::block)
		).apply(inst, SetBlock::new));

		@Override
		public ActionType type() {
			return ActionType.SET_BLOCK;
		}
	}

	/** Some of a block's properties change, the rest stay ({@code {"tapping": "true"}}: the barrel's tap turns). */
	public record SetProperties(int at, BlockPos pos, Map<String, String> properties) implements Action
	{
		static final MapCodec<SetProperties> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
			atField(),
			BlockPos.CODEC.fieldOf("pos").forGetter(SetProperties::pos),
			Codec.unboundedMap(Codec.STRING, Codec.STRING).fieldOf("properties").forGetter(SetProperties::properties)
		).apply(inst, SetProperties::new));

		@Override
		public ActionType type() {
			return ActionType.SET_PROPERTIES;
		}

		/** {@return the state with these properties set} (names or values the block doesn't have are left alone) */
		public BlockState applyTo(BlockState state) {
			for (Map.Entry<String, String> entry : properties.entrySet()) {
				Property<?> property = state.getBlock().getStateDefinition().getProperty(entry.getKey());
				if (property != null) {
					state = withValue(state, property, entry.getValue());
				}
			}
			return state;
		}

		private static <T extends Comparable<T>> BlockState withValue(BlockState state, Property<T> property, String value) {
			return property.getValue(value).map(v -> state.setValue(property, v)).orElse(state);
		}
	}

	/**
	 * An Aging Barrel's contents, from a real aging recipe (so a data pack's changes show up): {@code servings} of it,
	 * at {@code stage} ({@code fermenting}, {@code young} or {@code aged}) and {@code progress} (0 to 1) through it. The
	 * barrel's fill and bung follow, as in the world.
	 */
	public record Barrel(int at, BlockPos pos, ResourceLocation recipe, int servings, String stage, float progress) implements Action
	{
		static final MapCodec<Barrel> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
			atField(),
			BlockPos.CODEC.fieldOf("pos").forGetter(Barrel::pos),
			ResourceLocation.CODEC.fieldOf("recipe").forGetter(Barrel::recipe),
			ExtraCodecs.intRange(0, 64).fieldOf("servings").forGetter(Barrel::servings),
			ExtraCodecs.validate(Codec.STRING, WatchScene::validStage).fieldOf("stage").forGetter(Barrel::stage),
			Codec.floatRange(0.0F, 1.0F).optionalFieldOf("progress", 0.0F).forGetter(Barrel::progress)
		).apply(inst, Barrel::new));

		@Override
		public ActionType type() {
			return ActionType.BARREL;
		}
	}

	/**
	 * A Brew Kettle's brew, from a real brewing recipe (so a data pack's changes show up): {@code servings} of the
	 * recipe's result, in its bucket form too. The kettle's fill follows, as in the world.
	 */
	public record Kettle(int at, BlockPos pos, ResourceLocation recipe, int servings) implements Action
	{
		static final MapCodec<Kettle> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
			atField(),
			BlockPos.CODEC.fieldOf("pos").forGetter(Kettle::pos),
			ResourceLocation.CODEC.fieldOf("recipe").forGetter(Kettle::recipe),
			ExtraCodecs.intRange(0, 64).fieldOf("servings").forGetter(Kettle::servings)
		).apply(inst, Kettle::new));

		@Override
		public ActionType type() {
			return ActionType.KETTLE;
		}
	}

	private static DataResult<String> validStage(String stage) {
		return switch (stage) {
			case "fermenting", "young", "aged" -> DataResult.success(stage);
			default -> DataResult.error(() -> "Unknown barrel stage '" + stage + "' (fermenting, young or aged)");
		};
	}

	/**
	 * One of a real recipe's items. An aging recipe: its {@code base}, the base's {@code bucket}, the {@code young} or the
	 * {@code aged} drink, or the {@code vessel} its drinks are tapped into (a mug or a wine glass). A brewing recipe: its
	 * {@code result} (a bottle of it), its {@code bucket}, or an {@code ingredient} (the first item of its first
	 * ingredient). For a caption's {@code with} and a time-lapse's {@code days_of} only: an aging recipe's
	 * {@code fermenting_time} or {@code aging_time}.
	 */
	public record RecipePart(ResourceLocation recipe, String part)
	{
		public static final Codec<RecipePart> CODEC = RecordCodecBuilder.create(inst -> inst.group(
			ResourceLocation.CODEC.fieldOf("recipe").forGetter(RecipePart::recipe),
			Codec.STRING.fieldOf("part").forGetter(RecipePart::part)
		).apply(inst, RecipePart::new));
	}

	/**
	 * Real particles, from a point with some spread: on average {@code rate} a tick, for {@code ticks} ticks (a rate
	 * below 1 is a chance per tick). A coloured particle can take its colour from a real item ({@code color_of}: the
	 * drink's liquid colour, so a data pack's drink drips in its own colour). {@code color} is the colour written with
	 * the particle ({@code {"type": "minecraft:entity_effect", "color": -3318613}}): on this Minecraft version vanilla's
	 * particles carry no colour themselves, so the scene keeps it and whoever makes the particle colours it.
	 */
	public record Particles(int at, int ticks, ParticleOptions particle, Optional<Integer> color, Vec3 pos, Vec3 spread, Vec3 velocity, float rate,
							Optional<Either<Holder<Item>, RecipePart>> colorOf) implements Action
	{
		/** A particle as a scene writes it, with the {@code color} that may stand beside its type. */
		private static final Codec<Pair<ParticleOptions, Optional<Integer>>> PARTICLE = Codec.PASSTHROUGH.flatXmap(
			data -> ParticleTypes.CODEC.parse(data).map(options -> Pair.of(options, data.get("color").asNumber().result().map(Number::intValue))),
			particle -> ParticleTypes.CODEC.encodeStart(JsonOps.INSTANCE, particle.getFirst()).<Dynamic<?>>map(json -> {
				Dynamic<JsonElement> data = new Dynamic<>(JsonOps.INSTANCE, json);
				return particle.getSecond().isPresent() ? data.set("color", data.createInt(particle.getSecond().get())) : data;
			}));

		static final MapCodec<Particles> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
			atField(),
			ExtraCodecs.POSITIVE_INT.fieldOf("ticks").forGetter(Particles::ticks),
			PARTICLE.fieldOf("particle").forGetter(particles -> Pair.of(particles.particle(), particles.color())),
			Vec3.CODEC.fieldOf("pos").forGetter(Particles::pos),
			Vec3.CODEC.optionalFieldOf("spread", Vec3.ZERO).forGetter(Particles::spread),
			Vec3.CODEC.optionalFieldOf("velocity", Vec3.ZERO).forGetter(Particles::velocity),
			Codec.floatRange(0.0F, 20.0F).optionalFieldOf("rate", 1.0F).forGetter(Particles::rate),
			ITEM_REF.optionalFieldOf("color_of").forGetter(Particles::colorOf)
		).apply(inst, (at, ticks, particle, pos, spread, velocity, rate, colorOf) ->
			new Particles(at, ticks, particle.getFirst(), particle.getSecond(), pos, spread, velocity, rate, colorOf)));

		@Override
		public ActionType type() {
			return ActionType.PARTICLES;
		}

		@Override
		public int end() {
			return at + ticks;
		}
	}

	/** A real sound event, played at a point of the stage (vanilla's subtitles show it). */
	public record Sound(int at, ResourceLocation sound, Vec3 pos, float volume, float pitch) implements Action
	{
		static final MapCodec<Sound> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
			atField(),
			ResourceLocation.CODEC.fieldOf("sound").forGetter(Sound::sound),
			Vec3.CODEC.optionalFieldOf("pos", Vec3.ZERO).forGetter(Sound::pos),
			Codec.FLOAT.optionalFieldOf("volume", 1.0F).forGetter(Sound::volume),
			Codec.FLOAT.optionalFieldOf("pitch", 1.0F).forGetter(Sound::pitch)
		).apply(inst, Sound::new));

		@Override
		public ActionType type() {
			return ActionType.SOUND;
		}
	}

	/** The small white arrow over what a step is about. */
	public record Pointer(int at, int ticks, Vec3 pos) implements Action
	{
		static final MapCodec<Pointer> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
			atField(),
			ExtraCodecs.POSITIVE_INT.fieldOf("ticks").forGetter(Pointer::ticks),
			Vec3.CODEC.fieldOf("pos").forGetter(Pointer::pos)
		).apply(inst, Pointer::new));

		@Override
		public ActionType type() {
			return ActionType.POINTER;
		}

		@Override
		public int end() {
			return at + ticks;
		}
	}

	/**
	 * The signature: game days pass in the window's sky while the stage waits, {@code days} of them in {@code ticks}; or
	 * as many as a real recipe's time takes ({@code days_of}: an aging recipe's {@code fermenting_time} or
	 * {@code aging_time}), so the sky counts the days the drink really needs.
	 */
	public record Timelapse(int at, int ticks, float days, Optional<RecipePart> daysOf) implements Action
	{
		static final MapCodec<Timelapse> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
			atField(),
			ExtraCodecs.POSITIVE_INT.fieldOf("ticks").forGetter(Timelapse::ticks),
			Codec.floatRange(0.25F, 30.0F).optionalFieldOf("days", 1.0F).forGetter(Timelapse::days),
			RecipePart.CODEC.optionalFieldOf("days_of").forGetter(Timelapse::daysOf)
		).apply(inst, Timelapse::new));

		@Override
		public ActionType type() {
			return ActionType.TIMELAPSE;
		}

		@Override
		public int end() {
			return at + ticks;
		}
	}

	/**
	 * Someone on the stage: appears at {@code pos} facing {@code yaw}, or walks there over {@code ticks} if already
	 * standing somewhere. {@code id} tells figures apart (Cheers! has two); {@code skin} is {@code viewer} (the player's
	 * own) or {@code default} (one of the game's default skins, picked by the id).
	 */
	public record Figure(int at, String id, Vec3 pos, float yaw, int ticks, String skin) implements FigureAction
	{
		static final MapCodec<Figure> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
			atField(),
			figureId(),
			Vec3.CODEC.fieldOf("pos").forGetter(Figure::pos),
			Codec.FLOAT.optionalFieldOf("yaw", 0.0F).forGetter(Figure::yaw),
			ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("ticks", 0).forGetter(Figure::ticks),
			ExtraCodecs.validate(Codec.STRING, WatchScene::validSkin).optionalFieldOf("skin", "viewer").forGetter(Figure::skin)
		).apply(inst, Figure::new));

		@Override
		public ActionType type() {
			return ActionType.FIGURE;
		}

		@Override
		public int end() {
			return at + ticks;
		}
	}

	/** What a figure holds in its main hand ({@code minecraft:air} for nothing): an item id or a part of a real recipe. */
	public record Hold(int at, String id, Either<Holder<Item>, RecipePart> item) implements FigureAction
	{
		static final MapCodec<Hold> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
			atField(),
			figureId(),
			ITEM_REF.fieldOf("item").forGetter(Hold::item)
		).apply(inst, Hold::new));

		@Override
		public ActionType type() {
			return ActionType.HOLD;
		}
	}

	/** A figure uses what it holds: the arm swings, as when a player right-clicks a block. */
	public record Swing(int at, String id) implements FigureAction
	{
		static final MapCodec<Swing> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
			atField(),
			figureId()
		).apply(inst, Swing::new));

		@Override
		public ActionType type() {
			return ActionType.SWING;
		}
	}

	private static DataResult<String> validSkin(String skin) {
		return skin.equals("viewer") || skin.equals("default") ? DataResult.success(skin)
			: DataResult.error(() -> "Unknown skin '" + skin + "' (viewer or default)");
	}

	/** A figure drinks what it holds for {@code ticks}: the glass or mug raised to the mouth. */
	public record Drink(int at, String id, int ticks) implements FigureAction
	{
		static final MapCodec<Drink> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
			atField(),
			figureId(),
			ExtraCodecs.POSITIVE_INT.optionalFieldOf("ticks", 32).forGetter(Drink::ticks)
		).apply(inst, Drink::new));

		@Override
		public ActionType type() {
			return ActionType.DRINK;
		}

		@Override
		public int end() {
			return at + ticks;
		}
	}

	/** A figure turns its head (and body) toward a point of the stage. */
	public record Look(int at, String id, Vec3 pos) implements FigureAction
	{
		static final MapCodec<Look> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
			atField(),
			figureId(),
			Vec3.CODEC.fieldOf("pos").forGetter(Look::pos)
		).apply(inst, Look::new));

		@Override
		public ActionType type() {
			return ActionType.LOOK;
		}
	}

	/** An action of one of the figures on the stage (by its {@code id}). */
	public sealed interface FigureAction extends Action
	{
		String id();
	}

	private static <A extends FigureAction> RecordCodecBuilder<A, String> figureId() {
		return Codec.STRING.optionalFieldOf("id", "brewer").forGetter(FigureAction::id);
	}

	private static <A extends Action> RecordCodecBuilder<A, Integer> atField() {
		return ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("at", 0).forGetter(Action::at);
	}

	public enum ActionType implements StringRepresentable
	{
		PLACE("place", Place.CODEC),
		SET_BLOCK("set_block", SetBlock.CODEC),
		SET_PROPERTIES("set_properties", SetProperties.CODEC),
		BARREL("barrel", Barrel.CODEC),
		KETTLE("kettle", Kettle.CODEC),
		PARTICLES("particles", Particles.CODEC),
		SOUND("sound", Sound.CODEC),
		POINTER("pointer", Pointer.CODEC),
		TIMELAPSE("timelapse", Timelapse.CODEC),
		FIGURE("figure", Figure.CODEC),
		HOLD("hold", Hold.CODEC),
		SWING("swing", Swing.CODEC),
		LOOK("look", Look.CODEC),
		DRINK("drink", Drink.CODEC);

		static final Codec<ActionType> CODEC = StringRepresentable.fromEnum(ActionType::values);

		private final String name;
		private final MapCodec<? extends Action> codec;

		ActionType(String name, MapCodec<? extends Action> codec) {
			this.name = name;
			this.codec = codec;
		}

		MapCodec<? extends Action> codec() {
			return codec;
		}

		@Override
		public String getSerializedName() {
			return name;
		}
	}
}
