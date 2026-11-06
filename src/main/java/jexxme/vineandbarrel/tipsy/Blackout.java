package jexxme.vineandbarrel.tipsy;

import jexxme.vineandbarrel.advancement.VBAdvancements;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * One drink too many at the top Tipsy level: the player blacks out (playtest 1, "a bit funny too").
 * <p>
 * The eyes fall shut, a few seconds of black in which nothing can hurt or move the player, then they wake up
 * somewhere else: usually at their bed, sometimes at a random safe spot nearby. They wake hungry and shaky (Nausea and
 * Slowness for a while, Tipsy back to I), with an odd line in the action bar and a mystery souvenir in their pockets.
 * Nothing is lost, there is no time skip (other players on the server aren't affected), and it can be switched off.
 * <p>
 * The state is the synced {@link TipsyRegistry#BLACKOUT} attachment: ticks left. The client draws the black screen from it.
 */
public final class Blackout
{
	/** Whole blackout: fade to black, black, wake up and fade back in. */
	public static final int TICKS = 120;
	/** The player is moved and wakes up at this many ticks left, while the screen is fully black. */
	public static final int WAKE_AT = 40;
	/** Client fade lengths at the start and the end. */
	public static final int FADE_OUT = 25;
	public static final int FADE_IN = 30;

	/** Usually the bed: one wake-up in this many is at a random spot nearby instead. */
	static final int RANDOM_SPOT_ONE_IN = 3;
	static final int MIN_DISTANCE = 20;
	static final int MAX_DISTANCE = 60;
	static final int HUNGER_LOST = 8;
	public static final int SHAKY_TICKS = 30 * 20;
	static final int WAKE_LINES = 10;

	private static final List<String> SOUVENIRS = List.of("minecraft:poppy", "minecraft:dandelion", "minecraft:egg", "minecraft:cod",
		"minecraft:stick", "minecraft:bone", "minecraft:feather", "minecraft:string", "minecraft:wheat", "minecraft:oak_sapling",
		"minecraft:flower_pot", "minecraft:lead", "minecraft:carrot", "minecraft:sweet_berries", "minecraft:white_wool", "minecraft:brown_mushroom");

	private Blackout() {
	}

	/** {@return whether this player is blacked out right now} */
	public static boolean isOut(net.minecraft.world.entity.player.Player player) {
		Integer left = player.getExistingDataOrNull(TipsyRegistry.BLACKOUT);
		return left != null && left > 0;
	}

	/** {@return whether one more drink now makes this player black out} */
	public static boolean wouldBlackOut(ServerPlayer player) {
		return TipsyConfig.enabled() && TipsyConfig.blackout() && !isOut(player) && !player.isSpectator()
			&& Tipsy.getLevel(player) >= TipsyConfig.maxLevel();
	}

	public static void start(ServerPlayer player) {
		player.setData(TipsyRegistry.BLACKOUT, TICKS);
		// Can't walk, jump or look about much while out (the screen is black anyway)
		player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, TICKS, 9, false, false, false));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_BIG_FALL, SoundSource.PLAYERS, 1.0F, 0.8F);
	}

	/** Once a tick on the server, for a blacked-out player. */
	public static void tick(ServerPlayer player) {
		Integer left = player.getExistingDataOrNull(TipsyRegistry.BLACKOUT);
		if (left == null) {
			return;
		}
		int next = left - 1;
		if (next == WAKE_AT) {
			wakeUp(player);
		}
		if (next <= 0) {
			player.removeData(TipsyRegistry.BLACKOUT);
		} else {
			player.setData(TipsyRegistry.BLACKOUT, next);
		}
	}

	static void wakeUp(ServerPlayer player) {
		ServerLevel level = player.level();
		RandomSource random = player.getRandom();
		Vec3 spot = random.nextInt(RANDOM_SPOT_ONE_IN) == 0 ? null : bedSpot(player);
		if (spot == null) {
			BlockPos found = randomSpot(level, player.blockPosition(), random);
			spot = found == null ? null : Vec3.atBottomCenterOf(found);
		}
		if (spot != null) {
			player.teleportTo(level, spot.x, spot.y, spot.z, Set.of(), random.nextFloat() * 360.0F, 10.0F, true);
		}
		player.fallDistance = 0.0;
		Tipsy.removeDrinkBuffs(player);
		Tipsy.setLevel(player, 1);
		var food = player.getFoodData();
		food.setFoodLevel(Math.max(0, food.getFoodLevel() - HUNGER_LOST));
		food.setSaturation(0.0F);
		// Felt, not listed: no icons, like Tipsy's own side effects
		player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, SHAKY_TICKS, 0, false, false, false));
		player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, SHAKY_TICKS + WAKE_AT, 0, false, false, false));
		ItemStack souvenir = souvenir(random);
		if (!souvenir.isEmpty()) {
			// Into the pockets, or dropped at the feet when they're full
			if (!player.getInventory().add(souvenir)) {
				player.spawnAtLocation(level, souvenir);
			}
		}
		player.sendOverlayMessage(Component.translatable("blackout.vineandbarrel.wake." + random.nextInt(WAKE_LINES)));
		VBAdvancements.award(VBAdvancements.BLACKED_OUT, player);
	}

	/** {@return a safe spot right next to the player's bed, when they have one in this dimension and it still stands} */
	static @Nullable Vec3 bedSpot(ServerPlayer player) {
		ServerPlayer.RespawnConfig config = player.getRespawnConfig();
		ServerLevel level = player.level();
		if (config == null || !config.respawnData().dimension().equals(level.dimension())) {
			return null;
		}
		BlockPos bed = config.respawnData().pos();
		if (!level.hasChunkAt(bed) || !level.getBlockState(bed).is(net.minecraft.tags.BlockTags.BEDS)) {
			return null;
		}
		for (BlockPos around : BlockPos.betweenClosed(bed.offset(-2, -1, -2), bed.offset(2, 1, 2))) {
			if (!around.equals(bed) && isSafe(level, around)) {
				return Vec3.atBottomCenterOf(around.immutable());
			}
		}
		return null;
	}

	/** {@return a safe spot on the surface 20-60 blocks away: solid ground, no liquid, room to stand} or null. */
	public static @Nullable BlockPos randomSpot(ServerLevel level, BlockPos from, RandomSource random) {
		// Only under an open sky: in the Nether the surface heightmap is the bedrock roof, in the End the spike tops
		if (level.dimensionType().hasCeiling() || !level.dimensionType().hasSkyLight()) {
			return null;
		}
		for (int attempt = 0; attempt < 24; attempt++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			int distance = MIN_DISTANCE + random.nextInt(MAX_DISTANCE - MIN_DISTANCE + 1);
			int x = from.getX() + (int) Math.round(Math.cos(angle) * distance);
			int z = from.getZ() + (int) Math.round(Math.sin(angle) * distance);
			if (!level.hasChunkAt(new BlockPos(x, from.getY(), z))) {
				continue;
			}
			BlockPos feet = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
			if (isSafe(level, feet)) {
				return feet;
			}
		}
		return null;
	}

	static boolean isSafe(ServerLevel level, BlockPos feet) {
		BlockState ground = level.getBlockState(feet.below());
		return feet.getY() > level.getMinY() && ground.isFaceSturdy(level, feet.below(), net.minecraft.core.Direction.UP)
			&& ground.getFluidState().isEmpty() && !ground.is(net.minecraft.tags.BlockTags.FIRE)
			&& level.getBlockState(feet).getCollisionShape(level, feet).isEmpty() && level.getFluidState(feet).isEmpty()
			&& level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()
			&& !ground.is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK) && !ground.is(net.minecraft.world.level.block.Blocks.CACTUS);
	}

	static ItemStack souvenir(RandomSource random) {
		String id = SOUVENIRS.get(random.nextInt(SOUVENIRS.size()));
		Optional<Holder.Reference<Item>> item = BuiltInRegistries.ITEM.get(net.minecraft.resources.Identifier.parse(id));
		return item.map(holder -> new ItemStack(holder)).orElse(ItemStack.EMPTY);
	}
}
