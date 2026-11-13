package jexxme.vineandbarrel.gametest;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import jexxme.vineandbarrel.VineAndBarrel;
import jexxme.vineandbarrel.barrel.AgingBarrelBlockEntity;
import jexxme.vineandbarrel.barrel.AgingRecipe;
import jexxme.vineandbarrel.block.ClimbingCropBlock;
import jexxme.vineandbarrel.block.VineClimbing;
import jexxme.vineandbarrel.compat.farmersdelight.FDCompat;
import jexxme.vineandbarrel.drink.Cheers;
import jexxme.vineandbarrel.drink.DrinkItem;
import jexxme.vineandbarrel.drink.DrinkItems;
import jexxme.vineandbarrel.kettle.BaseItems;
import jexxme.vineandbarrel.kettle.BrewKettleBlockEntity;
import jexxme.vineandbarrel.kettle.BrewingRecipe;
import jexxme.vineandbarrel.tipsy.Blackout;
import jexxme.vineandbarrel.tipsy.BlackoutEnding;
import jexxme.vineandbarrel.tipsy.BlackoutSpots;
import jexxme.vineandbarrel.tipsy.GoingUnder;
import jexxme.vineandbarrel.tipsy.Hangover;
import jexxme.vineandbarrel.tipsy.Rowdy;
import jexxme.vineandbarrel.tipsy.Tipsy;
import jexxme.vineandbarrel.tipsy.TipsyConfig;
import jexxme.vineandbarrel.tipsy.TipsyEyes;
import jexxme.vineandbarrel.tipsy.TipsyInfo;
import jexxme.vineandbarrel.tipsy.TipsyLegs;
import jexxme.vineandbarrel.tipsy.TipsyLevels;
import jexxme.vineandbarrel.tipsy.TipsyMotion;
import jexxme.vineandbarrel.world.village.VillageStructures;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import jexxme.vineandbarrel.platform.GameTestRegistrar;
import jexxme.vineandbarrel.platform.registry.DeferredRegister;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Dev-only exporter for the wiki (site/). It is registered only when the system property {@code vineandbarrel.docsExport}
 * names an output file (Gradle task {@code runDocsExport}), and then instead of the normal GameTests, so it never shows up
 * in their results. It writes the real runtime values the wiki prints: every item's data components, block properties,
 * the loaded tags, the brewing and aging recipes as the game parsed them, the drinks' buffs, the servings rules, Cheers,
 * and the Tipsy levels and config defaults. No number on the wiki is typed in by hand.
 */
public final class VBDocsExport
{
	public static final String PROPERTY = "vineandbarrel.docsExport";
	private static final Identifier STRUCTURE = VineAndBarrel.id("gametest/empty_5x5");
	private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS = DeferredRegister.create(Registries.TEST_FUNCTION, VineAndBarrel.MODID);
	private static final ResourceKey<Consumer<GameTestHelper>> EXPORT = ResourceKey.create(Registries.TEST_FUNCTION, VineAndBarrel.id("docs_export"));

	static {
		FUNCTIONS.register("docs_export", () -> VBDocsExport::export);
	}

	private VBDocsExport() {
	}

	public static void register() {
		FUNCTIONS.register();
		jexxme.vineandbarrel.platform.Services.PLATFORM.addGameTests(VBDocsExport::registerTests);
	}

	private static void registerTests(GameTestRegistrar event) {
		Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(VineAndBarrel.id("docs_export"));
		event.registerTest(EXPORT.identifier(), new FunctionGameTestInstance(EXPORT, new TestData<>(environment, STRUCTURE, 200, 0, true)));
	}

	private static String key(Item item) {
		return BuiltInRegistries.ITEM.getKey(item).toString();
	}

	private static void export(GameTestHelper helper) {
		RegistryAccess access = helper.getLevel().registryAccess();
		RegistryOps<JsonElement> ops = access.createSerializationContext(JsonOps.INSTANCE);
		JsonObject root = new JsonObject();

		// Items: every data component of our items
		JsonObject items = new JsonObject();
		for (Item item : BuiltInRegistries.ITEM) {
			Identifier id = BuiltInRegistries.ITEM.getKey(item);
			if (!id.getNamespace().equals(VineAndBarrel.MODID)) {
				continue;
			}
			ItemStack stack = new ItemStack(item);
			JsonObject entry = new JsonObject();
			// Sorted by component id, so a rerun writes the same file (the component map has no stable order)
			java.util.TreeMap<String, JsonElement> sorted = new java.util.TreeMap<>();
			for (TypedDataComponent<?> typed : stack.getComponents()) {
				Identifier type = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(typed.type());
				if (type != null) {
					typed.encodeValue(ops).result().ifPresent(json -> sorted.put(type.toString(), json));
				}
			}
			JsonObject components = new JsonObject();
			sorted.forEach(components::add);
			entry.add("components", components);
			entry.addProperty("class", item.getClass().getName());
			JsonArray tags = new JsonArray();
			item.builtInRegistryHolder().tags().map(tag -> tag.location().toString()).sorted().forEach(tags::add);
			entry.add("tags", tags);
			items.add(id.toString(), entry);
		}
		root.add("items", items);

		// Our blocks
		JsonObject blocks = new JsonObject();
		for (Block block : BuiltInRegistries.BLOCK) {
			Identifier id = BuiltInRegistries.BLOCK.getKey(block);
			if (!id.getNamespace().equals(VineAndBarrel.MODID)) {
				continue;
			}
			JsonObject entry = new JsonObject();
			BlockState state = block.defaultBlockState();
			entry.addProperty("class", block.getClass().getName());
			entry.addProperty("hardness", block.defaultDestroyTime());
			entry.addProperty("blastResistance", block.getExplosionResistance());
			entry.addProperty("item", key(block.asItem()));
			JsonObject properties = new JsonObject();
			JsonObject defaults = new JsonObject();
			for (Property<?> property : block.getStateDefinition().getProperties()) {
				JsonArray values = new JsonArray();
				property.getPossibleValues().forEach(value -> values.add(valueName(property, value)));
				properties.add(property.getName(), values);
				defaults.addProperty(property.getName(), valueName(property, state.getValue(property)));
			}
			entry.add("properties", properties);
			entry.add("defaultState", defaults);
			JsonArray tags = new JsonArray();
			block.builtInRegistryHolder().tags().map(tag -> tag.location().toString()).sorted().forEach(tags::add);
			entry.add("tags", tags);
			blocks.add(id.toString(), entry);
		}
		root.add("blocks", blocks);

		// Our mob effects
		JsonObject effects = new JsonObject();
		Registry<MobEffect> effectRegistry = access.lookupOrThrow(Registries.MOB_EFFECT);
		effectRegistry.listElements().filter(holder -> holder.key().identifier().getNamespace().equals(VineAndBarrel.MODID)).forEach(holder -> {
			JsonObject entry = new JsonObject();
			entry.addProperty("category", holder.value().getCategory().name());
			entry.addProperty("color", holder.value().getColor());
			effects.add(holder.key().identifier().toString(), entry);
		});
		root.add("effects", effects);

		// Drinks: vessel, colour, buff (the rule in DrinkItems)
		JsonObject drinks = new JsonObject();
		for (DrinkItem drink : DrinkItems.allDrinks()) {
			JsonObject entry = new JsonObject();
			MobEffectInstance buff = drink.buff();
			Item vessel = AgingBarrelBlockEntity.vesselOf(new ItemStack(drink));
			entry.addProperty("vessel", vessel == null ? null : key(vessel));
			entry.addProperty("color", drink.color());
			entry.addProperty("aged", drink.isAged());
			entry.addProperty("effect", buff.getEffect().unwrapKey().map(k -> k.identifier().toString()).orElse("?"));
			entry.addProperty("duration", buff.getDuration());
			entry.addProperty("amplifier", buff.getAmplifier());
			drinks.add(key(drink), entry);
		}
		root.add("drinks", drinks);

		// Brewing and aging recipes as the game loaded them (FD-only ones are missing in an FD-free run)
		JsonArray brewing = new JsonArray();
		JsonArray aging = new JsonArray();
		JsonArray loaded = new JsonArray();
		helper.getLevel().getServer().getRecipeManager().getRecipes().stream()
			.sorted(Comparator.comparing(holder -> holder.id().identifier().toString()))
			.forEach((RecipeHolder<?> holder) -> {
				String id = holder.id().identifier().toString();
				if (id.startsWith(VineAndBarrel.MODID + ":")) {
					loaded.add(id);
				}
				if (holder.value() instanceof BrewingRecipe recipe) {
					JsonObject entry = new JsonObject();
					entry.addProperty("id", id);
					JsonArray ingredients = new JsonArray();
					for (Ingredient ingredient : recipe.ingredients()) {
						Ingredient.CODEC.encodeStart(ops, ingredient).result().ifPresent(ingredients::add);
					}
					entry.add("ingredients", ingredients);
					entry.addProperty("result", key(recipe.result().getItem()));
					entry.addProperty("bucket", key(recipe.bucket().getItem()));
					entry.addProperty("servings", recipe.servings());
					entry.addProperty("brewingTime", recipe.brewingTime());
					entry.addProperty("experience", recipe.experience());
					brewing.add(entry);
				} else if (holder.value() instanceof AgingRecipe recipe) {
					JsonObject entry = new JsonObject();
					entry.addProperty("id", id);
					entry.addProperty("base", key(recipe.base().value()));
					recipe.bucket().ifPresent(bucket -> entry.addProperty("bucket", key(bucket.value())));
					entry.addProperty("young", key(recipe.young().getItem()));
					entry.addProperty("aged", key(recipe.aged().getItem()));
					entry.addProperty("fermentingTime", recipe.fermentingTime());
					entry.addProperty("agingTime", recipe.agingTime());
					aging.add(entry);
				}
			});
		root.add("brewing", brewing);
		root.add("aging", aging);
		root.add("loadedRecipes", loaded);

		// Servings, barrel, Cheers
		JsonObject rules = new JsonObject();
		rules.addProperty("servingsPerBucket", BrewKettleBlockEntity.SERVINGS_PER_BUCKET);
		rules.addProperty("servingsPerBottle", 1);
		rules.addProperty("kettleIngredientSlots", BrewKettleBlockEntity.INPUT_SLOTS);
		rules.addProperty("kettleMaxServings", BrewKettleBlockEntity.MAX_SERVINGS);
		// The brews' colours, as the kettle's gauge and open lid show them
		JsonObject liquids = new JsonObject();
		for (Item base : BaseItems.all().stream().filter(base -> !key(base).endsWith("_bucket")).toList()) {
			liquids.addProperty(key(base), BaseItems.liquidColor(new ItemStack(base)));
		}
		rules.add("kettleLiquidColors", liquids);
		rules.addProperty("barrelCapacity", AgingBarrelBlockEntity.CAPACITY);
		rules.addProperty("drinkStackSize", DrinkItems.STACK_SIZE);
		rules.addProperty("youngBuffTicks", DrinkItems.YOUNG_TICKS);
		rules.addProperty("agedBuffTicks", DrinkItems.AGED_TICKS);
		rules.addProperty("youngBuffAmplifier", DrinkItems.YOUNG_AMPLIFIER);
		rules.addProperty("agedBuffAmplifier", DrinkItems.AGED_AMPLIFIER);
		rules.addProperty("dayTicks", 24000);
		rules.addProperty("cheersWindowTicks", Cheers.WINDOW_TICKS);
		rules.addProperty("cheersRadius", Cheers.RADIUS);
		rules.addProperty("cheersBonusPercent", Cheers.BONUS_PERCENT);
		JsonObject meals = new JsonObject();
		FDCompat.meals().forEach(meal -> meals.addProperty(key(meal), meal.nourishmentTicks()));
		rules.add("fdMealNourishment", meals);
		root.add("rules", rules);

		// Crops: bush ages and climbing (trellis or rope); villages: tavern weights and which crops their farms grow
		JsonObject crops = new JsonObject();
		crops.addProperty("bushMaxAge", ClimbingCropBlock.MAX_AGE);
		crops.addProperty("bushPickedAge", ClimbingCropBlock.PICKED_AGE);
		crops.addProperty("bushHalfRipeAge", ClimbingCropBlock.HALF_RIPE_AGE);
		crops.addProperty("climbMaxHeight", VineClimbing.MAX_HEIGHT);
		root.add("crops", crops);
		JsonObject villages = new JsonObject();
		JsonObject weights = new JsonObject();
		VillageStructures.TAVERN_WEIGHTS.entrySet().stream().sorted(Comparator.comparing(e -> e.getKey().id()))
			.forEach(e -> weights.addProperty(e.getKey().id(), e.getValue()));
		villages.add("tavernWeights", weights);
		JsonObject farms = new JsonObject();
		VillageStructures.FARM_CROPS.entrySet().stream().sorted(Comparator.comparing(e -> e.getKey().id())).forEach(e -> {
			JsonArray list = new JsonArray();
			for (VillageStructures.FarmCrop crop : e.getValue()) {
				JsonObject entry = new JsonObject();
				entry.addProperty("block", BuiltInRegistries.BLOCK.getKey(crop.crop().get()).toString());
				entry.addProperty("chance", crop.chance());
				list.add(entry);
			}
			farms.add(e.getKey().id(), list);
		});
		villages.add("farmCrops", farms);
		root.add("villages", villages);

		// Tipsy: what every level does (TipsyLevels, the one table the effects read), its thresholds and the tooltip's
		// words, as the game shows them
		JsonObject tipsy = new JsonObject();
		tipsy.addProperty("maxLevel", Tipsy.ABSOLUTE_MAX_LEVEL);
		tipsy.addProperty("hiccupLevel", Tipsy.HICCUP_LEVEL);
		tipsy.addProperty("slurLevel", Tipsy.SLUR_LEVEL);
		tipsy.addProperty("noBuffLevel", Tipsy.NO_BUFF_LEVEL);
		tipsy.addProperty("stumbleLevel", Tipsy.STUMBLE_LEVEL);
		tipsy.addProperty("heartbeatLevel", jexxme.vineandbarrel.tipsy.TipsySounds.HEARTBEAT_LEVEL);
		tipsy.addProperty("cutOffLevel", Rowdy.CUT_OFF_LEVEL);
		tipsy.addProperty("hiccupGapTicks", Tipsy.HICCUP_GAP_MIN + "-" + Tipsy.HICCUP_GAP_MAX);
		tipsy.addProperty("tripCooldownTicks", TipsyLevels.at(Tipsy.ABSOLUTE_MAX_LEVEL).legs().tripCooldown());
		tipsy.addProperty("tripTicks", TipsyMotion.TRIP_TICKS);
		tipsy.addProperty("tripDownTicks", TipsyMotion.TRIP_DOWN);
		tipsy.addProperty("tripMonsterFree", TipsyLegs.MONSTER_FREE);
		tipsy.addProperty("sprintTripOneIn", TipsyLevels.SPRINT_TRIP_ONE_IN);
		// The first level of each part of being drunk (0 = none): the drunk walk, heavy legs, the hic in chat, the drunk
		// eyes' double vision and blurred signs, the woolly sound, nodding off
		tipsy.addProperty("drunkWalkLevel", TipsyLevels.first(level -> level.legs().drunkWalk()));
		tipsy.addProperty("slowerLevel", TipsyLevels.first(level -> level.legs().slower() > 0.0F));
		tipsy.addProperty("hicChatLevel", TipsyLevels.first(level -> level.chat().stage() >= 1));
		tipsy.addProperty("softEdgesLevel", TipsyLevels.first(level -> level.eyes().softEdges() > 0.0F));
		tipsy.addProperty("doubleVisionLevel", TipsyLevels.first(level -> level.eyes().doubleVision() > 0.0F));
		tipsy.addProperty("signBlurLevel", TipsyLevels.first(level -> level.eyes().signBlur() > 0.0F));
		tipsy.addProperty("woollyLevel", TipsyLevels.first(level -> level.ears().muffle() > 0.0F));
		tipsy.addProperty("nodLevel", TipsyLevels.first(level -> level.nod().any()));
		// Rowdy: how long you are spoiling for a fight, and the arm drawn back before the swing
		tipsy.addProperty("rowdyItchTicks", Rowdy.ITCH);
		tipsy.addProperty("rowdyWindupTicks", Rowdy.WINDUP);
		// Going under: the drink one too many catches up with you this long after, the last ticks you feel it coming
		JsonObject goingUnder = new JsonObject();
		goingUnder.addProperty("soonestTicks", GoingUnder.SOONEST);
		goingUnder.addProperty("latestTicks", GoingUnder.LATEST);
		goingUnder.addProperty("ticks", GoingUnder.TICKS);
		tipsy.add("goingUnder", goingUnder);
		// The morning after: how long, how hungry, how much a sobering drink leaves, how long the fresh glare indoors lasts
		JsonObject hangover = new JsonObject();
		hangover.addProperty("afterBlackoutTicks", Hangover.AFTER_BLACKOUT);
		JsonObject afterSleep = new JsonObject();
		for (TipsyLevels.Level at : TipsyLevels.LEVELS) {
			if (Hangover.afterSleep(at.level()) > 0) {
				afterSleep.addProperty(Integer.toString(at.level()), Hangover.afterSleep(at.level()));
			}
		}
		hangover.add("afterSleepTicks", afterSleep);
		hangover.addProperty("sleepLevel", TipsyLevels.first(level -> Hangover.afterSleep(level.level()) > 0));
		hangover.addProperty("hungryFood", Hangover.HUNGRY_FOOD);
		hangover.addProperty("drinkLeaves", Hangover.DRINK_LEAVES);
		hangover.addProperty("freshGlareTicks", TipsyEyes.FRESH_TICKS);
		tipsy.add("hangover", hangover);
		// Every level: its row of each section of the table (courage, hiccups, legs, eyes, ears, nod, chat, rules), as
		// the record's fields, and the effect tooltip's lines with the default settings
		com.google.gson.Gson gson = new com.google.gson.Gson();
		JsonArray levels = new JsonArray();
		for (TipsyLevels.Level at : TipsyLevels.LEVELS) {
			JsonObject entry = new JsonObject();
			entry.addProperty("level", at.level());
			for (TipsyLevels.Section section : TipsyLevels.sections()) {
				entry.add(section.name(), gson.toJsonTree(section.of().apply(at)));
			}
			JsonArray good = new JsonArray();
			TipsyInfo.good(at.level()).forEach(line -> good.add(line.getString()));
			entry.add("tooltipGood", good);
			JsonArray bad = new JsonArray();
			TipsyInfo.drawbacks(at.level()).forEach(word -> bad.add(word.getString()));
			entry.add("tooltipBad", bad);
			levels.add(entry);
		}
		tipsy.add("levels", levels);
		JsonObject blackout = new JsonObject();
		blackout.addProperty("ticks", Blackout.TICKS);
		blackout.addProperty("wakeAt", Blackout.WAKE_AT);
		blackout.addProperty("unseenBonus", Blackout.UNSEEN_BONUS);
		blackout.addProperty("pumpkinOneIn", Blackout.PUMPKIN_ONE_IN);
		blackout.addProperty("hungerLost", Blackout.HUNGER_LOST);
		// How far each ending looks for its spot, and the safety margin every spot keeps
		JsonObject ranges = new JsonObject();
		ranges.addProperty("home", BlackoutSpots.HOME_RANGE);
		ranges.addProperty("openMin", BlackoutSpots.OPEN_MIN);
		ranges.addProperty("openMax", BlackoutSpots.OPEN_MAX);
		ranges.addProperty("animal", BlackoutSpots.ANIMAL_RANGE);
		ranges.addProperty("hay", BlackoutSpots.HAY_RANGE);
		ranges.addProperty("tavern", BlackoutSpots.TAVERN_RANGE);
		ranges.addProperty("villager", BlackoutSpots.VILLAGER_RANGE);
		ranges.addProperty("villagerNearBed", BlackoutSpots.VILLAGER_NEAR_BED);
		ranges.addProperty("roof", BlackoutSpots.ROOF_RANGE);
		ranges.addProperty("roofMinHeight", BlackoutSpots.ROOF_MIN_HEIGHT);
		ranges.addProperty("roofMaxHeight", BlackoutSpots.ROOF_MAX_HEIGHT);
		ranges.addProperty("boat", BlackoutSpots.BOAT_RANGE);
		ranges.addProperty("monsterFree", BlackoutSpots.MONSTER_FREE);
		blackout.add("ranges", ranges);
		// Every ending: its weight in the draw, the pose, the souvenirs and how many wake-up lines it has (the lines
		// themselves are in the lang file: blackout.vineandbarrel.<id>.<n>)
		JsonArray endings = new JsonArray();
		for (BlackoutEnding ending : BlackoutEnding.values()) {
			JsonObject entry = new JsonObject();
			entry.addProperty("id", ending.id());
			entry.addProperty("weight", ending.weight());
			entry.addProperty("pose", ending.pose().name().toLowerCase(Locale.ROOT));
			entry.addProperty("openSky", ending.needsOpenSky());
			entry.addProperty("collectable", BlackoutEnding.COLLECTABLE.contains(ending));
			entry.addProperty("lines", ending.lineCount());
			JsonArray souvenirs = new JsonArray();
			ending.souvenirs().forEach(id -> souvenirs.add(id.toString()));
			entry.add("souvenirs", souvenirs);
			endings.add(entry);
		}
		blackout.add("endings", endings);
		tipsy.add("blackout", blackout);
		tipsy.addProperty("rowdyLevel", Rowdy.ROWDY_LEVEL);
		tipsy.addProperty("rowdyPunchDamage", Rowdy.PUNCH_DAMAGE);
		JsonObject config = new JsonObject();
		config.addProperty("enabled", TipsyConfig.ENABLED.getDefault());
		config.addProperty("maxLevel", TipsyConfig.MAX_LEVEL.getDefault());
		config.addProperty("secondsPerLevel", TipsyConfig.SECONDS_PER_LEVEL.getDefault());
		config.addProperty("slurredChat", TipsyConfig.SLURRED_CHAT.getDefault());
		config.addProperty("stumbling", TipsyConfig.STUMBLING.getDefault());
		config.addProperty("blackout", TipsyConfig.BLACKOUT.getDefault());
		config.addProperty("rowdy", TipsyConfig.ROWDY.getDefault());
		config.addProperty("rowdyPunches", TipsyConfig.ROWDY_PUNCHES.getDefault());
		config.addProperty("cutOff", TipsyConfig.CUT_OFF.getDefault());
		config.addProperty("noddingOff", TipsyConfig.NODDING_OFF.getDefault());
		config.addProperty("hangover", TipsyConfig.HANGOVER.getDefault());
		config.addProperty("drunkScreen", TipsyConfig.DRUNK_SCREEN.getDefault());
		config.addProperty("doubleVision", TipsyConfig.DOUBLE_VISION.getDefault());
		config.addProperty("woollySound", TipsyConfig.WOOLLY_SOUND.getDefault());
		config.addProperty("otherPlayersSway", TipsyConfig.OTHER_PLAYERS_SWAY.getDefault());
		config.addProperty("heartbeatVolume", TipsyConfig.HEARTBEAT_VOLUME.getDefault());
		tipsy.add("config", config);
		root.add("tipsy", tipsy);

		// Every loaded tag of the registries the wiki cares about (heat sources, conductors, drinks, sobering drinks...)
		JsonObject tags = new JsonObject();
		tags.add("item", tagsOf(access.lookupOrThrow(Registries.ITEM)));
		tags.add("block", tagsOf(access.lookupOrThrow(Registries.BLOCK)));
		tags.add("worldgen/biome", tagsOf(access.lookupOrThrow(Registries.BIOME)));
		root.add("tags", tags);

		Path out = Path.of(System.getProperty(PROPERTY));
		try {
			Files.createDirectories(out.toAbsolutePath().getParent());
			Files.writeString(out, new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(root), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new IllegalStateException("Could not write " + out, e);
		}
		VineAndBarrel.LOGGER.info("Docs export written to {}", out.toAbsolutePath());
		helper.succeed();
	}

	private static <T> JsonObject tagsOf(Registry<T> registry) {
		JsonObject out = new JsonObject();
		registry.getTags().sorted(Comparator.comparing(set -> set.key().location().toString())).forEach((HolderSet.Named<T> set) -> {
			JsonArray values = new JsonArray();
			set.stream().map(holder -> holder.unwrapKey().map(k -> k.identifier().toString()).orElse("?")).sorted().forEach(values::add);
			out.add(set.key().location().toString(), values);
		});
		return out;
	}

	@SuppressWarnings("unchecked")
	private static <V extends Comparable<V>> String valueName(Property<V> property, Object value) {
		return property.getName((V) value);
	}
}
