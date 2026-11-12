package jexxme.vineandbarrel.smoke;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import jexxme.vineandbarrel.barrel.AgingBarrelBlockEntity;
import jexxme.vineandbarrel.barrel.BarrelRegistry;
import jexxme.vineandbarrel.compat.farmersdelight.FDCompat;
import jexxme.vineandbarrel.drink.DrinkItems;
import jexxme.vineandbarrel.drink.DrinkPlacing;
import jexxme.vineandbarrel.kettle.BaseItems;
import jexxme.vineandbarrel.kettle.BrewKettleBlockEntity;
import jexxme.vineandbarrel.kettle.KettleContents;
import jexxme.vineandbarrel.kettle.KettleRegistry;
import jexxme.vineandbarrel.platform.Services;
import jexxme.vineandbarrel.registry.VBBlocks;
import jexxme.vineandbarrel.tipsy.Blackout;
import jexxme.vineandbarrel.tipsy.BlackoutEnding;
import jexxme.vineandbarrel.world.village.VillageStructures;
import jexxme.vineandbarrel.world.village.VillageType;
import jexxme.vineandbarrel.tipsy.Tipsy;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import jexxme.vineandbarrel.block.VineOnSupportBlock;

import java.util.Optional;

/**
 * Dev-only server commands the smoke test drives (from its client, over the real connection): {@code /vbsmoke setup}
 * builds a small scene of everything V&B places (a heated kettle with a brew, a barrel with a batch, a table of drinks,
 * the crops), the other subcommands aim the player, open the kettle and make the player tipsy or black out. Same in
 * singleplayer and on a server the smoke client joined.
 */
public final class SmokeCommands implements ModInitializer
{
	/** Where the scene is built: high above the spawn, so the terrain never matters. */
	static final int HEIGHT = 140;
	static final BlockPos KETTLE = new BlockPos(0, 1, 3), BARREL = new BlockPos(3, 0, 3), TABLE = new BlockPos(-3, 0, 3),
		GRAPES = new BlockPos(-3, 0, 6), BARLEY = new BlockPos(0, 0, 6), HOPS = new BlockPos(3, 0, 6), EMPTY_TABLE = new BlockPos(-5, 0, 3),
		MEAD_BARREL = new BlockPos(5, 0, 3), BED_FOOT = new BlockPos(4, 0, 0);
	/** Trellis 2.0's patch behind the scene: the first root in the fence row, the first in the wall, the trellis row's first bush. */
	static final BlockPos VINE_FENCE = new BlockPos(-5, 0, 13), VINE_WALL = new BlockPos(1, 0, 13), VINE_TRELLIS = new BlockPos(-4, 0, 16);
	/** The blackout ending the next draw gives (see ForcedBlackoutEndingMixin), or null for the normal draw. */
	private static volatile BlackoutEnding forcedEnding;

	/** The ending asked for, once: the draw takes it. */
	public static BlackoutEnding takeForcedEnding() {
		BlackoutEnding ending = forcedEnding;
		forcedEnding = null;
		return ending;
	}

	@Override
	public void onInitialize() {
		SmokeServer.register();
		CommandRegistrationCallback.EVENT.register((dispatcher, registries, environment) -> dispatcher.register(Commands.literal("vbsmoke")
			.requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
			.then(Commands.literal("setup").executes(SmokeCommands::setup))
			.then(Commands.literal("vines").executes(SmokeCommands::vines))
			.then(Commands.literal("look").then(Commands.argument("what", StringArgumentType.word()).executes(SmokeCommands::look)))
			.then(Commands.literal("view").executes(context -> view(context.getSource().getPlayerOrException())))
			.then(Commands.literal("open_kettle").executes(SmokeCommands::openKettle))
			.then(Commands.literal("tipsy").then(Commands.argument("level", IntegerArgumentType.integer(0, 5)).executes(context -> {
				ServerPlayer player = context.getSource().getPlayerOrException();
				Tipsy.setLevel(player, IntegerArgumentType.getInteger(context, "level"));
				return reply(context, "tipsy " + Tipsy.getLevel(player));
			})))
			.then(Commands.literal("blackout").executes(context -> {
				ServerPlayer player = context.getSource().getPlayerOrException();
				Blackout.start(player);
				return reply(context, "blackout");
			}).then(Commands.literal("home").executes(SmokeCommands::blackoutHome))
				.then(Commands.literal("getup").executes(SmokeCommands::blackoutGetUp)))
			.then(Commands.literal("village").executes(SmokeCommands::village))
			.then(Commands.literal("sober").executes(context -> {
				ServerPlayer player = context.getSource().getPlayerOrException();
				Tipsy.clear(player);
				return reply(context, "sober");
			}))));
	}

	/** The scene's origin: above the world spawn. */
	static BlockPos origin(ServerLevel level) {
		BlockPos spawn = level.getLevelData().getSpawnPos();
		return new BlockPos(spawn.getX(), HEIGHT, spawn.getZ());
	}

	private static int setup(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		ServerLevel level = player.serverLevel();
		BlockPos o = origin(level);
		boolean fd = FDCompat.isLoaded();
		for (int x = -6; x <= 6; x++) {
			for (int z = -2; z <= 9; z++) {
				level.setBlock(o.offset(x, -1, z), Blocks.SMOOTH_STONE.defaultBlockState(), Block.UPDATE_ALL);
				for (int y = 0; y < 5; y++) {
					level.setBlock(o.offset(x, y, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
				}
			}
		}
		// The kettle on a lit stove (Farmer's Delight) or campfire, with a brew of sweet berry juice
		BlockState heat = fd ? fdBlock("stove").map(stove -> stove.setValue(BlockStateProperties.LIT, true)).orElseThrow()
			: Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true);
		level.setBlock(o.offset(KETTLE.below()), heat, Block.UPDATE_ALL);
		level.setBlock(o.offset(KETTLE), KettleRegistry.kettle().defaultBlockState(), Block.UPDATE_ALL);
		if (level.getBlockEntity(o.offset(KETTLE)) instanceof BrewKettleBlockEntity kettle) {
			ItemStack item = new ItemStack(KettleRegistry.kettleItem());
			item.set(KettleRegistry.KETTLE_CONTENTS.get(), new KettleContents(new ItemStack(BaseItems.SWEET_BERRY_JUICE.get(), 6),
				new ItemStack(BaseItems.SWEET_BERRY_JUICE_BUCKET.get())));
			kettle.applyComponentsFromItemStack(item);
			kettle.setChanged();
			level.sendBlockUpdated(o.offset(KETTLE), level.getBlockState(o.offset(KETTLE)), level.getBlockState(o.offset(KETTLE)), Block.UPDATE_CLIENTS);
		}
		// The barrel with grape juice poured in (fermenting)
		level.setBlock(o.offset(BARREL), BarrelRegistry.AGING_BARREL.get().defaultBlockState(), Block.UPDATE_ALL);
		if (level.getBlockEntity(o.offset(BARREL)) instanceof AgingBarrelBlockEntity barrel) {
			for (int i = 0; i < 4; i++) {
				barrel.pour(new ItemStack(BaseItems.GRAPE_JUICE.get()), null);
			}
		}
		// A second barrel with honey must, for the new drinks' aging (mead)
		level.setBlock(o.offset(MEAD_BARREL), BarrelRegistry.AGING_BARREL.get().defaultBlockState(), Block.UPDATE_ALL);
		if (level.getBlockEntity(o.offset(MEAD_BARREL)) instanceof AgingBarrelBlockEntity mead) {
			for (int i = 0; i < 4; i++) {
				mead.pour(new ItemStack(BaseItems.HONEY_MUST_BOTTLE.get()), null);
			}
		}
		// A table with four drinks, put down the way a player does (sneak-use), and an empty one for the client to use
		level.setBlock(o.offset(TABLE), Blocks.OAK_PLANKS.defaultBlockState(), Block.UPDATE_ALL);
		level.setBlock(o.offset(EMPTY_TABLE), Blocks.OAK_PLANKS.defaultBlockState(), Block.UPDATE_ALL);
		GameType mode = player.gameMode.getGameModeForPlayer();
		player.setGameMode(GameType.SURVIVAL);
		player.setShiftKeyDown(true);
		ItemStack[] drinks = {new ItemStack(DrinkItems.AGED_RED_WINE.get()), new ItemStack(DrinkItems.BARLEY_ALE.get()),
			new ItemStack(DrinkItems.MUG.get()), new ItemStack(DrinkItems.WINE_GLASS.get())};
		double[][] spots = {{0.25, 0.25}, {0.75, 0.25}, {0.25, 0.75}, {0.75, 0.75}};
		for (int i = 0; i < 4; i++) {
			player.setItemInHand(InteractionHand.MAIN_HAND, drinks[i]);
			BlockPos clicked = o.offset(TABLE);
			Vec3 hit = Vec3.atLowerCornerOf(o.offset(TABLE)).add(spots[i][0], 1.0, spots[i][1]);
			DrinkPlacing.place(new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(hit, Direction.UP, clicked, false)));
		}
		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		player.setShiftKeyDown(false);
		player.setGameMode(mode);
		// The crops, ripe: grapes (on a rope when FD is here), barley on farmland, hops
		level.setBlock(o.offset(GRAPES.below()), Blocks.GRASS_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
		level.setBlock(o.offset(GRAPES), VBBlocks.GRAPE_BUSH.get().defaultBlockState().setValue(BlockStateProperties.AGE_3, 3), Block.UPDATE_ALL);
		if (fd) {
			fdBlock("rope").ifPresent(rope -> level.setBlock(o.offset(GRAPES.above()), rope, Block.UPDATE_ALL));
		}
		level.setBlock(o.offset(BARLEY.below()), Blocks.FARMLAND.defaultBlockState(), Block.UPDATE_ALL);
		level.setBlock(o.offset(BARLEY), VBBlocks.BARLEY.get().defaultBlockState().setValue(CropBlock.AGE, CropBlock.MAX_AGE), Block.UPDATE_ALL);
		level.setBlock(o.offset(HOPS.below()), Blocks.GRASS_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
		level.setBlock(o.offset(HOPS), VBBlocks.HOP_BUSH.get().defaultBlockState().setValue(BlockStateProperties.AGE_3, 3), Block.UPDATE_ALL);
		view(player);
		return reply(context, "setup at " + o + (fd ? " with Farmer's Delight" : ""));
	}

	/**
	 * Trellis 2.0 behind the scene: grapes and hops grown into an oak fence row and a mossy wall two high (the vines keep the
	 * blocks they grew into, drawn under their leaves), and a framed trellis row; the player looks at it.
	 */
	private static int vines(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		ServerLevel level = player.serverLevel();
		BlockPos o = origin(level);
		for (int x = -6; x <= 6; x++) {
			for (int z = 11; z <= 17; z++) {
				level.setBlock(o.offset(x, -1, z), Blocks.GRASS_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
				for (int y = 0; y < 5; y++) {
					level.setBlock(o.offset(x, y, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
				}
			}
		}
		java.util.List<BlockPos> built = new java.util.ArrayList<>();
		for (int x = 0; x < 5; x++) {
			built.add(o.offset(VINE_FENCE).east(x));
			level.setBlock(built.getLast(), Blocks.OAK_FENCE.defaultBlockState(), Block.UPDATE_ALL);
		}
		for (int x = 0; x < 5; x++) {
			for (int y = 0; y < 2; y++) {
				built.add(o.offset(VINE_WALL).east(x).above(y));
				level.setBlock(built.getLast(), Blocks.MOSSY_COBBLESTONE_WALL.defaultBlockState(), Block.UPDATE_ALL);
			}
		}
		for (BlockPos pos : built) {
			level.setBlock(pos, Block.updateFromNeighbourShapes(level.getBlockState(pos), level, pos), Block.UPDATE_ALL);
		}
		int[] ages = {3, 3, 2, 1};
		for (int x = 0; x < ages.length; x++) {
			grow(level, o.offset(VINE_FENCE).east(x), x == 1 ? VBBlocks.HOP_VINE_ON_SUPPORT.get() : VBBlocks.GRAPE_VINE_ON_SUPPORT.get(), ages[x], true);
			grow(level, o.offset(VINE_WALL).east(x), VBBlocks.WHITE_GRAPE_VINE_ON_SUPPORT.get(), ages[x], true);
			if (ages[x] >= 2) {
				grow(level, o.offset(VINE_WALL).east(x).above(), VBBlocks.WHITE_GRAPE_VINE_ON_SUPPORT.get(), ages[x], false);
			}
		}
		// a trellis row two high on ripe bushes, the last trellis bare: it draws its frame
		for (int x = 0; x < 4; x++) {
			BlockPos bush = o.offset(VINE_TRELLIS).east(x);
			boolean grown = x < 3;
			level.setBlock(bush, (x == 1 ? VBBlocks.HOP_BUSH.get() : VBBlocks.GRAPE_BUSH.get()).defaultBlockState()
				.setValue(BlockStateProperties.AGE_3, grown ? 3 : 0), Block.UPDATE_ALL);
			for (int y = 1; y <= 2; y++) {
				BlockState trellis = VBBlocks.TRELLIS.get().defaultBlockState();
				BlockState vine = (x == 1 ? VBBlocks.HOP_VINE_ON_TRELLIS.get() : VBBlocks.GRAPE_VINE_ON_TRELLIS.get()).defaultBlockState()
					.setValue(BlockStateProperties.AGE_3, 3);
				level.setBlock(bush.above(y), grown ? vine : trellis, Block.UPDATE_ALL);
			}
		}
		fly(player);
		player.teleportTo(level, o.getX() + 0.5, o.getY() + 2.5, o.getZ() + 8.5, java.util.Set.of(), 0.0F, 20.0F);
		return reply(context, "vines at " + o.offset(VINE_FENCE));
	}

	private static void grow(ServerLevel level, BlockPos pos, VineOnSupportBlock vine, int age, boolean root) {
		VineOnSupportBlock.place(level, pos, vine.defaultBlockState().setValue(BlockStateProperties.AGE_3, age), level.getBlockState(pos), root);
	}

	/** The whole scene in view, from behind the kettle's row. */
	private static int view(ServerPlayer player) {
		fly(player);
		BlockPos o = origin(player.serverLevel());
		player.teleportTo(player.serverLevel(), o.getX() + 0.5, o.getY() + 1.5, o.getZ() - 1.5, java.util.Set.of(), 0.0F, 30.0F);
		return 1;
	}

	/** Stands in front of one block, looking at it (Jade's tooltip shows what is looked at). */
	private static int look(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		fly(player);
		BlockPos o = origin(player.serverLevel());
		String what = StringArgumentType.getString(context, "what");
		if (what.equals("sky")) {
			// Nothing in reach: a held use key then only uses the item in hand
			player.teleportTo(player.serverLevel(), o.getX() + 0.5, o.getY() + 1.5, o.getZ() - 1.5, java.util.Set.of(), 0.0F, -80.0F);
			return reply(context, "looking at the sky");
		}
		BlockPos target = switch (what) {
			case "kettle" -> o.offset(KETTLE);
			case "barrel" -> o.offset(BARREL);
			case "mead_barrel" -> o.offset(MEAD_BARREL);
			case "drinks" -> o.offset(TABLE).above();
			case "grapes" -> o.offset(GRAPES);
			case "empty_table" -> o.offset(EMPTY_TABLE);
			default -> o;
		};
		// The drinks are small: aim at the first glass (spot 0), not at the middle of the block
		Vec3 center = what.equals("drinks") ? Vec3.atLowerCornerOf(target).add(0.25, 0.2, 0.25) : Vec3.atCenterOf(target);
		Vec3 eye = center.add(0.0, 1.2, -1.8);
		Vec3 d = center.subtract(eye);
		float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
		float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.hypot(d.x, d.z)));
		player.teleportTo(player.serverLevel(), eye.x, eye.y - player.getEyeHeight(), eye.z, java.util.Set.of(), yaw, pitch);
		return reply(context, "looking at " + target);
	}

	/** Opens the kettle's screen the way the block does (the platform's extended menu, with the block's position). */
	private static int openKettle(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		BlockPos pos = origin(player.serverLevel()).offset(KETTLE);
		if (player.level().getBlockEntity(pos) instanceof BrewKettleBlockEntity kettle) {
			Services.PLATFORM.openBlockMenu(player, kettle, pos);
			return reply(context, "opened the kettle");
		}
		context.getSource().sendFailure(Component.literal("no kettle at " + pos));
		return 0;
	}

	/**
	 * A blackout that ends in the player's own bed: a bed in the scene becomes the player's spawn point, and the draw
	 * gives the home ending. The timeline is the mod's own: fall on the face, wake up in bed, get up.
	 */
	private static int blackoutHome(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		ServerLevel level = player.serverLevel();
		BlockPos foot = origin(level).offset(BED_FOOT);
		BlockPos head = foot.east();
		BlockState bed = Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.EAST);
		level.setBlock(foot, bed.setValue(BedBlock.PART, BedPart.FOOT), Block.UPDATE_ALL);
		level.setBlock(head, bed.setValue(BedBlock.PART, BedPart.HEAD), Block.UPDATE_ALL);
		player.setRespawnPosition(level.dimension(), head, 0.0F, false, false);
		forcedEnding = BlackoutEnding.HOME;
		Blackout.start(player);
		return reply(context, "blackout, ending in the bed at " + head);
	}

	/**
	 * A blackout on the ground right here, about to get up: the client poses the limbs from here to the end.
	 */
	private static int blackoutGetUp(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		Blackout.start(player);
		jexxme.vineandbarrel.tipsy.TipsyRegistry.BLACKOUT.set(player, Blackout.WAKE_AT);
		Blackout.wakeUp(player, new jexxme.vineandbarrel.tipsy.BlackoutSpots.Wake(BlackoutEnding.RIGHT_HERE, player.position(), player.getYRot(), null, null),
			player.getRandom());
		jexxme.vineandbarrel.tipsy.TipsyRegistry.BLACKOUT.set(player, Blackout.GROUND_GET_UP + 2);
		return reply(context, "blackout, getting up off the ground");
	}

	/**
	 * A real plains village, grown and placed the way {@code /place structure} does it (the game's own jigsaw layout with
	 * our pool additions), at the first spot east of the spawn whose village has one of our taverns; the player hovers
	 * south of the tavern, looking at it.
	 */
	private static int village(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		ServerLevel level = player.serverLevel();
		Holder.Reference<Structure> village = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(BuiltinStructures.VILLAGE_PLAINS);
		ChunkGenerator generator = level.getChunkSource().getGenerator();
		RandomState randomState = level.getChunkSource().randomState();
		ChunkPos spawn = new ChunkPos(level.getLevelData().getSpawnPos());
		for (int i = 0; i < 64; i++) {
			ChunkPos at = new ChunkPos(spawn.x + 16 + 8 * i, spawn.z);
			StructureStart start = village.value().generate(level.registryAccess(), generator, generator.getBiomeSource(),
				randomState, level.getStructureManager(), level.getSeed(), at, 0, level,
				biome -> true);
			if (!start.isValid()) {
				continue;
			}
			Optional<BoundingBox> tavern = start.getPieces().stream()
				.filter(piece -> piece instanceof PoolElementStructurePiece pool && VillageStructures.isTavern(pool.getElement(), VillageType.PLAINS))
				.map(StructurePiece::getBoundingBox).findFirst();
			if (tavern.isEmpty()) {
				continue;
			}
			BoundingBox box = start.getBoundingBox();
			ChunkPos min = new ChunkPos(SectionPos.blockToSectionCoord(box.minX()), SectionPos.blockToSectionCoord(box.minZ()));
			ChunkPos max = new ChunkPos(SectionPos.blockToSectionCoord(box.maxX()), SectionPos.blockToSectionCoord(box.maxZ()));
			ChunkPos.rangeClosed(min, max).forEach(chunk -> level.getChunk(chunk.x, chunk.z));
			ChunkPos.rangeClosed(min, max).forEach(chunk -> start.placeInChunk(level, level.structureManager(), generator, level.getRandom(),
				new BoundingBox(chunk.getMinBlockX(), level.getMinBuildHeight(), chunk.getMinBlockZ(), chunk.getMaxBlockX(), level.getMaxBuildHeight(), chunk.getMaxBlockZ()), chunk));
			BlockPos centre = tavern.get().getCenter();
			Vec3 target = Vec3.atCenterOf(centre);
			Vec3 eye = target.add(0.0, 12.0, -20.0);
			Vec3 d = target.subtract(eye);
			float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
			float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.hypot(d.x, d.z)));
			fly(player);
			player.teleportTo(level, eye.x, eye.y - player.getEyeHeight(), eye.z, java.util.Set.of(), yaw, pitch);
			fly(player);
			return reply(context, "a plains village with a tavern at " + centre + " (spot " + (i + 1) + ")");
		}
		context.getSource().sendFailure(Component.literal("no plains village with a tavern in 64 spots"));
		return 0;
	}

	/** Hovers where it is put (creative flight), so the camera stays where a step aims it. */
	private static void fly(ServerPlayer player) {
		if (player.getAbilities().mayfly) {
			player.getAbilities().flying = true;
			player.onUpdateAbilities();
		}
	}

	private static Optional<BlockState> fdBlock(String name) {
		return BuiltInRegistries.BLOCK.getOptional(ResourceLocation.fromNamespaceAndPath(FDCompat.MODID, name)).map(Block::defaultBlockState);
	}

	private static int reply(CommandContext<CommandSourceStack> context, String message) {
		context.getSource().sendSuccess(() -> Component.literal("[vbsmoke] " + message), false);
		return 1;
	}
}
