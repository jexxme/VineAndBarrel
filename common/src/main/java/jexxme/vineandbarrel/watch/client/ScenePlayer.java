package jexxme.vineandbarrel.watch.client;

import jexxme.vineandbarrel.VineAndBarrel;
import jexxme.vineandbarrel.barrel.AgingBarrelBlock;
import jexxme.vineandbarrel.barrel.AgingBarrelBlockEntity;
import jexxme.vineandbarrel.barrel.AgingRecipe;
import jexxme.vineandbarrel.barrel.AgingRecipes;
import jexxme.vineandbarrel.barrel.BarrelContents;
import jexxme.vineandbarrel.barrel.BarrelRegistry;
import jexxme.vineandbarrel.barrel.BarrelStage;
import jexxme.vineandbarrel.compat.jei.JeiRecipeCache;
import jexxme.vineandbarrel.drink.DrinkColors;
import jexxme.vineandbarrel.kettle.BrewKettleBlock;
import jexxme.vineandbarrel.kettle.BrewKettleBlockEntity;
import jexxme.vineandbarrel.kettle.BrewingRecipe;
import jexxme.vineandbarrel.kettle.KettleContents;
import jexxme.vineandbarrel.kettle.KettleRegistry;
import jexxme.vineandbarrel.mixin.ParticleEngineAccessor;
import jexxme.vineandbarrel.watch.WatchParts;
import jexxme.vineandbarrel.watch.WatchScene;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Plays a {@link WatchScene}: the stage as it is at the current step and tick, the scene's own particles, and the
 * timeline. Ticks with the client (20 a second) while the screen is open; jumping to a step rebuilds the stage from the
 * start, so every step looks the same however you got there.
 */
public final class ScenePlayer
{
	/**
	 * Where the scene's particles live in the player's level: far out, where no chunk is ever loaded, so they are lit
	 * by sky light alone and collide with nothing.
	 */
	static final Vec3 PARTICLE_ORIGIN = new Vec3(20_000_000.0, 0.0, 20_000_000.0);

	/** The recipes the client has (synced from the server), for the parts of recipes a scene names. */
	static final WatchParts.Recipes CLIENT_RECIPES = new WatchParts.Recipes()
	{
		@Override
		public @Nullable AgingRecipe aging(net.minecraft.resources.Identifier id) {
			return agingRecipe(id, false);
		}

		@Override
		public @Nullable BrewingRecipe brewing(net.minecraft.resources.Identifier id) {
			return brewingRecipe(id, false);
		}
	};

	private final WatchScene scene;
	private final WatchParts parts;
	private final SceneWorld world;
	private final ParticleEngine particles;
	private final ClientLevel level;
	private final Map<String, SceneFigure> figures = new LinkedHashMap<>();
	private final RandomSource random = RandomSource.create();
	private int step;
	private int tick;
	private boolean paused;
	private boolean finished;
	private int age;

	public ScenePlayer(WatchScene scene, ClientLevel level) {
		this(scene, level, Optional.empty());
	}

	/** A scene played for an aging recipe other than the one it is written for (a wort bucket asked for, say). */
	public ScenePlayer(WatchScene scene, ClientLevel level, Optional<net.minecraft.resources.Identifier> recipe) {
		this.scene = scene;
		// a data pack took out the recipe the scene is written for: play the first aging recipe there is, not an empty stage
		if (recipe.isEmpty() && scene.recipe().isPresent() && agingRecipe(scene.recipe().get(), false) == null) {
			recipe = jexxme.vineandbarrel.barrel.AgingRecipes.all(level).stream().findFirst().map(holder -> holder.id().identifier());
		}
		this.parts = WatchParts.of(scene, CLIENT_RECIPES, recipe);
		this.level = level;
		this.world = new SceneWorld(level.registryAccess().lookupOrThrow(Registries.BIOME).get(Biomes.PLAINS).orElse(null));
		this.particles = new ParticleEngine(level, ((ParticleEngineAccessor) Minecraft.getInstance().particleEngine).vineandbarrel$resources());
		jumpTo(0);
	}

	public WatchScene scene() {
		return scene;
	}

	/** {@return what the scene's items and lines are, for the recipe it plays} */
	public WatchParts parts() {
		return parts;
	}

	/** {@return the recipe it plays, if it was asked for another than the one it is written for} */
	public Optional<net.minecraft.resources.Identifier> askedRecipe() {
		return parts.asked();
	}

	/** {@return a step's caption line, for the recipe the scene plays} */
	public Component caption(int index) {
		return parts.caption(scene.steps().get(index));
	}

	public Component caption() {
		return caption(step);
	}

	SceneWorld world() {
		return world;
	}

	ParticleEngine particles() {
		return particles;
	}

	public int step() {
		return step;
	}

	/** {@return ticks into the current step} */
	public int tick() {
		return tick;
	}

	public boolean isPaused() {
		return paused;
	}

	public void setPaused(boolean paused) {
		this.paused = paused;
	}

	/** {@return ticks the scene has been running (not counting pauses), for idle motion like a spinning item} */
	public float age(float partialTick) {
		return age + (paused ? 0.0F : partialTick);
	}

	/** {@return whether the last step has played to its end} */
	public boolean isFinished() {
		return finished;
	}

	public WatchScene.Step currentStep() {
		return scene.steps().get(step);
	}

	/**
	 * {@return how long the current step plays}: its own length, but never shorter than its caption takes to read in the
	 * player's language (a second, then about 15 characters a second), so a long translation isn't cut off
	 */
	public int stepTicks() {
		int characters = caption().getString().length();
		return Math.max(currentStep().ticks(), captionAt() + 20 + characters * 4 / 3);
	}

	/**
	 * {@return ticks into the current step before its caption shows}: a caption about a block that the step itself puts
	 * on the stage waits until it stands there (the barrel set down before "Pour in the juice." points at it); else 0
	 */
	public int captionAt() {
		WatchScene.Step current = currentStep();
		if (current.anchor().flatMap(WatchScene.Anchor::figure).isPresent()) {
			return 0;
		}
		Optional<Vec3> point = current.anchor().flatMap(WatchScene.Anchor::pos).or(() -> current.actions().stream()
			.filter(WatchScene.Pointer.class::isInstance).map(action -> ((WatchScene.Pointer) action).pos()).findFirst());
		if (point.isEmpty()) {
			return 0;
		}
		BlockPos at = BlockPos.containing(point.get());
		for (int i = 0; i < 2; i++, at = at.below()) {
			for (WatchScene.Action action : current.actions()) {
				if (action instanceof WatchScene.Place place && place.pos().equals(at) && place.at() > 0) {
					return place.end();
				}
			}
			if (at.getY() == 0 && scene.floor().isPresent()) {
				break;
			}
		}
		return 0;
	}

	/** {@return how far through the current step it is, 0 to 1} */
	public float stepProgress(float partialTick) {
		return Mth.clamp((tick + (paused || finished ? 0.0F : partialTick)) / stepTicks(), 0.0F, 1.0F);
	}

	/** Starts a step from its beginning: the stage as the steps before it left it. */
	public void jumpTo(int index) {
		step = Mth.clamp(index, 0, scene.steps().size() - 1);
		tick = 0;
		finished = false;
		world.clear();
		scene.floor().ifPresent(floor -> {
			for (int x = 0; x < scene.width(); x++) {
				for (int z = 0; z < scene.depth(); z++) {
					world.set(new BlockPos(x, 0, z), floor);
				}
			}
		});
		for (WatchScene.Placed placed : scene.blocks()) {
			world.set(placed.pos(), placed.block());
		}
		figures.clear();
		for (int s = 0; s < step; s++) {
			for (WatchScene.Action action : scene.steps().get(s).actions()) {
				applyToStage(action, false);
			}
		}
		particles.clearParticles();
		fire(0);
	}

	public void restart() {
		jumpTo(0);
	}

	/** One client tick: the timeline moves on (unless paused or over) and the particles live. */
	public void clientTick() {
		if (!paused && !finished) {
			emitParticles();
			tick++;
			if (tick >= stepTicks()) {
				if (step + 1 < scene.steps().size()) {
					step++;
					tick = 0;
					fire(0);
				} else {
					tick = stepTicks();
					finished = true;
				}
			} else {
				fire(tick);
			}
		}
		if (!paused) {
			particles.tick();
			for (SceneFigure figure : figures.values()) {
				figure.tick();
			}
			age++;
		}
	}

	/** The actions of the current step that happen at this tick. */
	private void fire(int at) {
		for (WatchScene.Action action : currentStep().actions()) {
			if (action.at() == at) {
				applyToStage(action, true);
				switch (action) {
					case WatchScene.Sound sound -> play(sound);
					case WatchScene.Swing swing -> figure(swing.id()).ifPresent(SceneFigure::swing);
					case WatchScene.Drink drink -> figure(drink.id()).ifPresent(f -> f.drink(drink.ticks()));
					default -> {
					}
				}
			}
		}
	}

	/**
	 * What an action leaves on the stage (the timeline-only actions leave nothing). {@code live}: while playing (a figure
	 * walks and turns), or false when jumping to a step (it is just there).
	 */
	private void applyToStage(WatchScene.Action action, boolean live) {
		switch (action) {
			case WatchScene.Place place -> world.set(place.pos(), place.block());
			case WatchScene.SetBlock set -> world.set(set.pos(), set.block());
			case WatchScene.SetProperties set -> world.set(set.pos(), set.applyTo(world.getBlockState(set.pos())));
			case WatchScene.Barrel barrel -> fillBarrel(barrel);
			case WatchScene.Kettle kettle -> fillKettle(kettle);
			case WatchScene.Figure figure -> {
				SceneFigure existing = figures.get(figure.id());
				if (existing == null) {
					figures.put(figure.id(), new SceneFigure(level, figure.id(), figure.skin().equals("viewer"), figure.pos(), figure.yaw()));
				} else if (live && figure.ticks() > 0) {
					existing.walkTo(figure.pos(), figure.ticks());
				} else {
					existing.teleport(figure.pos(), figure.yaw());
				}
			}
			case WatchScene.Hold hold -> figure(hold.id()).ifPresent(f -> f.hold(parts.item(hold.item())));
			case WatchScene.Look look -> figure(look.id()).ifPresent(f -> f.lookAt(look.pos(), !live));
			default -> {
			}
		}
	}

	private Optional<SceneFigure> figure(String id) {
		return Optional.ofNullable(figures.get(id));
	}

	/** The figures on the stage now. */
	Collection<SceneFigure> figures() {
		return figures.values();
	}

	/** A barrel's contents from the real aging recipe, at the asked stage; the bung and fill follow, as in the world. */
	private void fillBarrel(WatchScene.Barrel barrel) {
		AgingRecipe recipe = agingRecipe(parts.recipe(barrel.recipe()));
		if (recipe == null) {
			return;
		}
		long fermenting = recipe.fermentingTime();
		long aging = recipe.agingTime();
		long elapsed = switch (barrel.stage()) {
			case "fermenting" -> (long) (barrel.progress() * (fermenting - 1));
			case "young" -> fermenting + (long) (barrel.progress() * (aging - 1));
			default -> fermenting + aging + (long) (barrel.progress() * aging);
		};
		BarrelContents contents = recipe.newBatch().withServings(barrel.servings()).withElapsed(elapsed);
		BlockState state = world.getBlockState(barrel.pos());
		if (state.hasProperty(AgingBarrelBlock.FILL)) {
			BarrelStage stage = contents.stageAt(elapsed);
			state = state.setValue(AgingBarrelBlock.FILL, Math.min(barrel.servings(), AgingBarrelBlockEntity.CAPACITY))
				.setValue(AgingBarrelBlock.BUNG, stage.bung());
			world.set(barrel.pos(), state);
		}
		BlockEntity entity = world.getBlockEntity(barrel.pos());
		if (entity instanceof AgingBarrelBlockEntity) {
			entity.applyComponents(DataComponentMap.builder().set(BarrelRegistry.BARREL_CONTENTS.get(), contents).build(), DataComponentPatch.EMPTY);
		}
	}

	/** A kettle's brew from the real brewing recipe; its fill follows, as in the world. */
	private void fillKettle(WatchScene.Kettle kettle) {
		BrewingRecipe recipe = brewingRecipe(parts.recipe(kettle.recipe()));
		if (recipe == null) {
			return;
		}
		BlockState state = world.getBlockState(kettle.pos());
		if (state.hasProperty(BrewKettleBlock.FILL)) {
			world.set(kettle.pos(), state.setValue(BrewKettleBlock.FILL, BrewKettleBlockEntity.fillLevel(kettle.servings())));
		}
		if (world.getBlockEntity(kettle.pos()) instanceof BrewKettleBlockEntity entity) {
			ItemStack brew = kettle.servings() > 0 ? recipe.result().copyWithCount(kettle.servings()) : ItemStack.EMPTY;
			entity.applyComponents(DataComponentMap.builder().set(KettleRegistry.KETTLE_CONTENTS.get(),
				new KettleContents(brew, brew.isEmpty() ? ItemStack.EMPTY : recipe.bucket())).build(), DataComponentPatch.EMPTY);
		}
	}

	static @Nullable BrewingRecipe brewingRecipe(net.minecraft.resources.Identifier id) {
		return brewingRecipe(id, true);
	}

	static @Nullable BrewingRecipe brewingRecipe(net.minecraft.resources.Identifier id, boolean warn) {
		for (RecipeHolder<BrewingRecipe> holder : JeiRecipeCache.brewing()) {
			if (holder.id().identifier().equals(id)) {
				return holder.value();
			}
		}
		if (warn) {
			VineAndBarrel.LOGGER.warn("Watch it: no brewing recipe {} on this server", id);
		}
		return null;
	}

	static @Nullable AgingRecipe agingRecipe(net.minecraft.resources.Identifier id) {
		return agingRecipe(id, true);
	}

	static @Nullable AgingRecipe agingRecipe(net.minecraft.resources.Identifier id, boolean warn) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null) {
			return null;
		}
		for (RecipeHolder<AgingRecipe> holder : AgingRecipes.all(level)) {
			if (holder.id().identifier().equals(id)) {
				return holder.value();
			}
		}
		if (warn) {
			VineAndBarrel.LOGGER.warn("Watch it: no aging recipe {} on this server", id);
		}
		return null;
	}

	private void emitParticles() {
		for (WatchScene.Action action : currentStep().actions()) {
			if (action instanceof WatchScene.Particles emitter && tick >= emitter.at() && tick < emitter.end()) {
				int count = (int) emitter.rate();
				if (random.nextFloat() < emitter.rate() - count) {
					count++;
				}
				for (int i = 0; i < count; i++) {
					Vec3 spread = emitter.spread();
					Vec3 at = PARTICLE_ORIGIN.add(emitter.pos()).add(
						(random.nextDouble() - 0.5) * 2.0 * spread.x,
						(random.nextDouble() - 0.5) * 2.0 * spread.y,
						(random.nextDouble() - 0.5) * 2.0 * spread.z);
					Vec3 v = emitter.velocity();
					particles.createParticle(coloured(emitter), at.x, at.y, at.z, v.x, v.y, v.z);
				}
			}
		}
	}

	/** A coloured particle in the colour of the item it names (a drink's liquid colour), else as written. */
	private ParticleOptions coloured(WatchScene.Particles emitter) {
		if (emitter.particle() instanceof ColorParticleOption option && emitter.colorOf().isPresent()) {
			ItemStack stack = parts.item(emitter.colorOf().get());
			if (!stack.isEmpty()) {
				@SuppressWarnings("unchecked")
				ParticleType<ColorParticleOption> type = (ParticleType<ColorParticleOption>) option.getType();
				return ColorParticleOption.create(type, 0xFF000000 | DrinkColors.of(stack));
			}
		}
		return emitter.particle();
	}

	private void play(WatchScene.Sound sound) {
		Minecraft.getInstance().getSoundManager().play(new SimpleSoundInstance(sound.sound(), SoundSource.BLOCKS, sound.volume(), sound.pitch(),
			SoundInstance.createUnseededRandom(), false, 0, SoundInstance.Attenuation.NONE, 0.0, 0.0, 0.0, true));
	}

	// ------------------------------------------------------------------ what the renderer and the screen read

	/** How far a block being placed this step still hovers above its spot (blocks), 0 once it has landed. */
	float dropOffset(BlockPos pos, float partialTick) {
		for (WatchScene.Action action : currentStep().actions()) {
			if (action instanceof WatchScene.Place place && place.drop() && place.pos().equals(pos)) {
				float t = tick + (paused || finished ? 0.0F : partialTick) - place.at();
				if (t >= 0.0F && t < WatchScene.Place.DROP_TICKS) {
					float k = 1.0F - t / WatchScene.Place.DROP_TICKS;
					return 2.0F * k * k;
				}
			}
		}
		return 0.0F;
	}

	/**
	 * Where the current step's caption points: its anchor (a stage point, or a figure's hands), else its pointer's point.
	 */
	public Optional<Vec3> anchor(float partialTick) {
		WatchScene.Step step = currentStep();
		if (step.anchor().isPresent()) {
			WatchScene.Anchor anchor = step.anchor().get();
			if (anchor.pos().isPresent()) {
				return anchor.pos();
			}
			SceneFigure figure = figures.get(anchor.figure().orElse("brewer"));
			if (figure != null) {
				return Optional.of(figure.entity().getPosition(partialTick).subtract(PARTICLE_ORIGIN).add(0.0, 1.05, 0.0));
			}
		}
		for (WatchScene.Action action : step.actions()) {
			if (action instanceof WatchScene.Pointer pointer) {
				return Optional.of(pointer.pos());
			}
		}
		return Optional.empty();
	}

	/** {@return what the figures hold now (empty hands left out)} */
	public java.util.List<ItemStack> heldItems() {
		return figures.values().stream().map(figure -> figure.entity().getMainHandItem()).filter(stack -> !stack.isEmpty()).toList();
	}

	/** A time-lapse under way: how many days it shows and how far it is (0 to 1). */
	public record Lapse(float days, float progress) {
		/** {@return the time of day in the window, 0 = sunrise, as a fraction of a day} */
		public float dayFraction() {
			return (days * progress) % 1.0F;
		}
	}

	public Optional<Lapse> timelapse(float partialTick) {
		float now = tick + (paused || finished ? 0.0F : partialTick);
		for (WatchScene.Action action : currentStep().actions()) {
			if (action instanceof WatchScene.Timelapse lapse && now >= lapse.at() && now <= lapse.end()) {
				return Optional.of(new Lapse(parts.days(lapse), Mth.clamp((now - lapse.at()) / lapse.ticks(), 0.0F, 1.0F)));
			}
		}
		return Optional.empty();
	}
}
