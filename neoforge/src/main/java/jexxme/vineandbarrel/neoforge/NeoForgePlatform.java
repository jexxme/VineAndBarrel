package jexxme.vineandbarrel.neoforge;

import jexxme.vineandbarrel.gametest.NeoForgeGameTests;
import jexxme.vineandbarrel.neoforge.transfer.NeoForgeItemAccess;
import jexxme.vineandbarrel.platform.EntityAttachment;
import jexxme.vineandbarrel.platform.GameTestRegistrar;
import jexxme.vineandbarrel.platform.Platform;
import jexxme.vineandbarrel.platform.SlotAccess;
import jexxme.vineandbarrel.platform.registry.DeferredRegister;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInstance;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.RecipeBookType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.DataMapHooks;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** {@link jexxme.vineandbarrel.platform.Platform} on NeoForge (named in META-INF/services). */
public final class NeoForgePlatform implements Platform
{
	/** The mod's event bus, set by {@link VineAndBarrelNeoForge} before anything registers. */
	private static @Nullable IEventBus modBus;
	private static final net.neoforged.neoforge.registries.DeferredRegister<AttachmentType<?>> ATTACHMENTS =
		net.neoforged.neoforge.registries.DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, jexxme.vineandbarrel.VineAndBarrel.MODID);

	static void init(IEventBus bus) {
		modBus = bus;
		ATTACHMENTS.register(bus);
	}

	private static IEventBus modBus() {
		return Objects.requireNonNull(modBus, "Vine & Barrel's NeoForge entry point has not run yet");
	}

	@Override
	public Loader loader() {
		return Loader.NEOFORGE;
	}

	@Override
	public boolean isModLoaded(String modId) {
		return ModList.get().isLoaded(modId);
	}

	@Override
	public Optional<String> modVersion(String modId) {
		return ModList.get().getModContainerById(modId).map(mod -> mod.getModInfo().getVersion().toString());
	}

	@Override
	public boolean isDevelopment() {
		return !FMLEnvironment.isProduction();
	}

	// ------------------------------------------------------------------ registration

	@Override
	public <T> void register(DeferredRegister<T> register) {
		modBus().addListener(RegisterEvent.class, event -> event.register(register.getRegistryKey(), helper ->
			register.getEntries().forEach((id, factory) -> helper.register(id, factory.apply(id)))));
	}

	@Override
	public <T> Holder<T> holder(ResourceKey<T> key) {
		return net.neoforged.neoforge.registries.DeferredHolder.create(key);
	}

	@Override
	public <T> EntityAttachment<T> attachment(Identifier id, EntityAttachment.Spec<T> spec) {
		Supplier<AttachmentType<T>> type = ATTACHMENTS.register(id.getPath(), () -> {
			AttachmentType.Builder<T> builder = AttachmentType.builder(spec.defaultValue());
			if (spec.saveCodec() != null) {
				builder.serialize(spec.saveCodec());
			}
			if (spec.syncCodec() != null) {
				builder.sync(spec.syncCodec());
			}
			if (spec.copyOnDeath()) {
				builder.copyOnDeath();
			}
			return builder.build();
		});
		return new EntityAttachment<>()
		{
			@Override
			public boolean has(Entity entity) {
				return entity.hasData(type);
			}

			@Override
			public T get(Entity entity) {
				return entity.getData(type);
			}

			@Override
			public @Nullable T getExisting(Entity entity) {
				return entity.getExistingDataOrNull(type);
			}

			@Override
			public void set(Entity entity, T value) {
				entity.setData(type, value);
			}

			@Override
			public void remove(Entity entity) {
				entity.removeData(type);
			}
		};
	}

	@Override
	public void addGameTests(Consumer<GameTestRegistrar> registrar) {
		modBus().addListener(RegisterGameTestsEvent.class, event -> registrar.accept(new GameTestRegistrar()
		{
			@Override
			public Holder<TestEnvironmentDefinition<?>> registerEnvironment(Identifier name, TestEnvironmentDefinition<?> definition) {
				return event.registerEnvironment(name, definition);
			}

			@Override
			public void registerTest(Identifier name, GameTestInstance test) {
				event.registerTest(name, test);
			}
		}));
	}

	@Override
	public @Nullable Consumer<GameTestHelper> loaderGameTest(String name) {
		return NeoForgeGameTests.get(name);
	}

	@Override
	public void registerAppleSkin() {
		jexxme.vineandbarrel.neoforge.compat.AppleSkinNeoForge.register();
	}

	@Override
	public CreativeModeTab.Builder creativeTabBuilder() {
		return CreativeModeTab.builder();
	}

	// ------------------------------------------------------------------ menus

	@Override
	public <M extends AbstractContainerMenu> MenuType<M> blockMenuType(BlockMenuFactory<M> factory) {
		return IMenuTypeExtension.create((windowId, inventory, data) -> factory.create(windowId, inventory, data.readBlockPos()));
	}

	@Override
	public void openBlockMenu(ServerPlayer player, MenuProvider provider, BlockPos pos) {
		player.openMenu(provider, pos);
	}

	@Override
	public void openMenuForGameTest(ServerPlayer player, MenuProvider provider, BlockPos pos) {
		player.openMenu(provider);
	}

	@Override
	public RecipeBookType brewingRecipeBookType() {
		return RecipeBookType.valueOf("VINEANDBARREL_BREWING");
	}

	// ------------------------------------------------------------------ game rules

	@Override
	public boolean canCropGrow(ServerLevel level, BlockPos pos, BlockState state, boolean vanilla) {
		return CommonHooks.canCropGrow(level, pos, state, vanilla);
	}

	@Override
	public void cropGrown(ServerLevel level, BlockPos pos, BlockState state) {
		CommonHooks.fireCropGrowPost(level, pos, state);
	}

	@Override
	public boolean canEntityGrief(ServerLevel level, Entity entity) {
		return EventHooks.canEntityGrief(level, entity);
	}

	@Override
	public @Nullable Block unwaxed(Block block) {
		return DataMapHooks.getBlockUnwaxed(block);
	}

	@Override
	public @Nullable ItemStackTemplate craftingRemainder(ItemStack stack) {
		return stack.getCraftingRemainder();
	}

	// ------------------------------------------------------------------ item automation

	@Override
	public @Nullable SlotAccess itemAccess(ServerLevel level, BlockPos pos, @Nullable Direction side) {
		return NeoForgeItemAccess.of(level, pos, side);
	}

	@Override
	public SlotAccess.Tx openTransaction() {
		Transaction transaction = Transaction.openRoot();
		return new NeoForgeItemAccess.Tx(transaction);
	}
}
