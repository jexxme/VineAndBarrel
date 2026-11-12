package jexxme.vineandbarrel.neoforge.client;

import com.mojang.math.OctahedralGroup;
import jexxme.vineandbarrel.block.ClimbingCropBlock;
import jexxme.vineandbarrel.block.SupportLeaves;
import jexxme.vineandbarrel.block.VineOnSupportBlock;
import jexxme.vineandbarrel.block.VineSupports;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockModelRotation;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.DelegateBlockStateModel;
import net.neoforged.neoforge.client.model.standalone.SimpleUnbakedStandaloneModel;
import net.neoforged.neoforge.client.model.standalone.StandaloneModelKey;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A vine on a fence, wall, chain or bars ({@link VineOnSupportBlock}): the block it holds, drawn with that block's own
 * model (read from the vine's block entity), and the vine's leaves as pieces that follow that block's shape
 * ({@link SupportLeaves}). So an oak fence stays an oak fence, with its arms as they are, and the leaves run along them.
 */
final class SupportedVineModel extends DelegateBlockStateModel
{
	private static final OctahedralGroup[] TURNS = {OctahedralGroup.IDENTITY, OctahedralGroup.BLOCK_ROT_Y_90, OctahedralGroup.BLOCK_ROT_Y_180,
			OctahedralGroup.BLOCK_ROT_Y_270};
	private static final Map<String, StandaloneModelKey<BlockStateModel>> PIECES = new HashMap<>();

	private SupportedVineModel(BlockStateModel model) {
		super(model);
	}

	/** Every piece of every vine on a support, per age and root, in each of its quarter turns. */
	static void registerPieces(ModelEvent.RegisterStandalone event) {
		for (Block block : BuiltInRegistries.BLOCK) {
			if (!(block instanceof VineOnSupportBlock)) {
				continue;
			}
			for (int age = 0; age <= ClimbingCropBlock.MAX_AGE; age++) {
				for (boolean base : new boolean[] {false, true}) {
					for (SupportLeaves.Piece piece : SupportLeaves.Piece.values()) {
						Identifier model = SupportLeaves.model(block, piece, base, age);
						for (int turn = 0; turn < piece.turns(); turn++) {
							String name = model + "#" + turn;
							StandaloneModelKey<BlockStateModel> key = new StandaloneModelKey<>(() -> name);
							PIECES.put(name, key);
							event.register(key, SimpleUnbakedStandaloneModel.blockStateModel(model, BlockModelRotation.get(TURNS[turn])));
						}
					}
				}
			}
		}
	}

	/** Wraps the baked model of every vine-on-support state (NeoForge: {@code ModelEvent.ModifyBakingResult}). */
	static void wrap(ModelEvent.ModifyBakingResult event) {
		for (Map.Entry<BlockState, BlockStateModel> entry : event.getBakingResult().blockStateModels().entrySet()) {
			if (entry.getKey().getBlock() instanceof VineOnSupportBlock) {
				entry.setValue(new SupportedVineModel(entry.getValue()));
			}
		}
	}

	private static @Nullable BlockStateModel supportModel(@Nullable BlockState support) {
		return support == null ? null : Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(support);
	}

	@Override
	public void collectParts(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random, List<BlockStateModelPart> parts) {
		BlockState support = VineSupports.supportAt(level, pos);
		if (support == null) {
			return;
		}
		boolean base = state.getValue(VineOnSupportBlock.BASE);
		int age = state.getValue(ClimbingCropBlock.AGE);
		for (SupportLeaves.Placed placed : SupportLeaves.of(support)) {
			StandaloneModelKey<BlockStateModel> key = PIECES.get(SupportLeaves.model(state.getBlock(), placed.piece(), base, age) + "#" + placed.turn());
			BlockStateModel leaves = key == null ? null : Minecraft.getInstance().getModelManager().getStandaloneModel(key);
			if (leaves != null) {
				leaves.collectParts(level, pos, state, random, parts);
			}
		}
		BlockStateModel model = supportModel(support);
		if (model != null) {
			model.collectParts(level, pos, support, random, parts);
		}
	}

	@Override
	public int materialFlags(BlockAndTintGetter level, BlockPos pos, BlockState state) {
		BlockState support = VineSupports.supportAt(level, pos);
		BlockStateModel model = supportModel(support);
		int leaves = delegate.materialFlags(level, pos, state);
		return model == null ? leaves : leaves | model.materialFlags(level, pos, support);
	}

	/** Drawn from the block entity each time: no geometry to share between positions. */
	@Override
	public @Nullable Object createGeometryKey(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random) {
		return null;
	}
}
