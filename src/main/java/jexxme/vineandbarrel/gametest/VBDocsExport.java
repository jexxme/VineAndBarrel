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
import jexxme.vineandbarrel.block.RopeClimbing;
import jexxme.vineandbarrel.compat.farmersdelight.FDCompat;
import jexxme.vineandbarrel.drink.Cheers;
import jexxme.vineandbarrel.drink.DrinkItem;
import jexxme.vineandbarrel.drink.DrinkItems;
import jexxme.vineandbarrel.kettle.BaseItems;
import jexxme.vineandbarrel.kettle.BrewKettleBlockEntity;
import jexxme.vineandbarrel.kettle.BrewingRecipe;
import jexxme.vineandbarrel.tipsy.Blackout;
import jexxme.vineandbarrel.tipsy.Tipsy;
import jexxme.vineandbarrel.tipsy.TipsyConfig;
import jexxme.vineandbarrel.tipsy.TipsyEffect;
import jexxme.vineandbarrel.tipsy.TipsyVision;
import jexxme.vineandbarrel.tipsy.TipsyWobble;
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
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
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

	public static void register(IEventBus modEventBus) {
		FUNCTIONS.register(modEventBus);
		modEventBus.addListener(VBDocsExport::registerTests);
	}

	private static void registerTests(RegisterGameTestsEvent event) {
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
		for (Item base : List.of(BaseItems.GRAPE_JUICE.get(), BaseItems.SWEET_BERRY_JUICE.get(), BaseItems.BARLEY_WORT_BOTTLE.get(), BaseItems.WHEAT_WORT_BOTTLE.get())) {
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

		// Crops: bush ages and rope climbing; villages: tavern weight and which crops their farms grow
		JsonObject crops = new JsonObject();
		crops.addProperty("bushMaxAge", ClimbingCropBlock.MAX_AGE);
		crops.addProperty("bushPickedAge", ClimbingCropBlock.PICKED_AGE);
		crops.addProperty("ropeMaxHeight", RopeClimbing.MAX_HEIGHT);
		root.add("crops", crops);
		JsonObject villages = new JsonObject();
		villages.addProperty("tavernWeight", VillageStructures.TAVERN_WEIGHT);
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

		// Tipsy: level thresholds and config defaults
		JsonObject tipsy = new JsonObject();
		tipsy.addProperty("maxLevel", Tipsy.ABSOLUTE_MAX_LEVEL);
		tipsy.addProperty("hiccupLevel", Tipsy.HICCUP_LEVEL);
		tipsy.addProperty("slurLevel", Tipsy.SLUR_LEVEL);
		tipsy.addProperty("stumbleLevel", Tipsy.STUMBLE_LEVEL);
		tipsy.addProperty("heavyLevel", Tipsy.HEAVY_LEVEL);
		tipsy.addProperty("nauseaLevel", Tipsy.NAUSEA_LEVEL);
		tipsy.addProperty("nauseaPulseTicks", Tipsy.NAUSEA_PULSE_TICKS);
		tipsy.addProperty("weaknessLevel", Tipsy.WEAKNESS_LEVEL);
		tipsy.addProperty("wobbleStrength", TipsyWobble.STRENGTH);
		tipsy.addProperty("noBuffLevel", Tipsy.NO_BUFF_LEVEL);
		// Tunnel vision and blinks, read from the vision code itself: edge darkness per level, the first level that
		// blinks, and the first with long blinks (a blink longer than any at the first blinking level)
		JsonArray vignette = new JsonArray();
		int[] longestBlink = new int[Tipsy.ABSOLUTE_MAX_LEVEL + 1];
		for (int lvl = 0; lvl <= Tipsy.ABSOLUTE_MAX_LEVEL; lvl++) {
			vignette.add(TipsyVision.vignette(lvl));
			int run = 0;
			for (int time = 0; time < 24000 * 5; time++) {
				run = TipsyVision.eyelids(lvl, time, 0) > 0.0F ? run + 1 : 0;
				longestBlink[lvl] = Math.max(longestBlink[lvl], run);
			}
		}
		tipsy.add("vignette", vignette);
		int blinkLevel = 0;
		int longBlinkLevel = 0;
		for (int lvl = 1; lvl <= Tipsy.ABSOLUTE_MAX_LEVEL; lvl++) {
			if (blinkLevel == 0 && longestBlink[lvl] > 0) {
				blinkLevel = lvl;
			} else if (blinkLevel > 0 && longBlinkLevel == 0 && longestBlink[lvl] > longestBlink[blinkLevel]) {
				longBlinkLevel = lvl;
			}
		}
		tipsy.addProperty("blinkLevel", blinkLevel);
		tipsy.addProperty("longBlinkLevel", longBlinkLevel);
		JsonObject blackout = new JsonObject();
		blackout.addProperty("ticks", Blackout.TICKS);
		blackout.addProperty("shakyTicks", Blackout.SHAKY_TICKS);
		tipsy.add("blackout", blackout);
		JsonArray courage = new JsonArray();
		for (int i = 0; i < TipsyEffect.COURAGE_KNOCKBACK_RESISTANCE.length; i++) {
			JsonObject atLevel = new JsonObject();
			atLevel.addProperty("level", i + 1);
			atLevel.addProperty("knockbackResistance", TipsyEffect.COURAGE_KNOCKBACK_RESISTANCE[i]);
			atLevel.addProperty("fallDamageReduction", TipsyEffect.COURAGE_FALL_DAMAGE_REDUCTION[i]);
			courage.add(atLevel);
		}
		tipsy.add("courage", courage);
		JsonObject config = new JsonObject();
		config.addProperty("enabled", TipsyConfig.ENABLED.getDefault());
		config.addProperty("maxLevel", TipsyConfig.MAX_LEVEL.getDefault());
		config.addProperty("secondsPerLevel", TipsyConfig.SECONDS_PER_LEVEL.getDefault());
		config.addProperty("slurredChat", TipsyConfig.SLURRED_CHAT.getDefault());
		config.addProperty("stumbling", TipsyConfig.STUMBLING.getDefault());
		config.addProperty("blackout", TipsyConfig.BLACKOUT.getDefault());
		config.addProperty("visionEffects", TipsyConfig.VISION_EFFECTS.getDefault());
		config.addProperty("wobbleIntensity", TipsyConfig.WOBBLE_INTENSITY.getDefault());
		config.addProperty("otherPlayersSway", TipsyConfig.OTHER_PLAYERS_SWAY.getDefault());
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
