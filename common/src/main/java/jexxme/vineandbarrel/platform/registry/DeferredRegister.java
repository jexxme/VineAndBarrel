package jexxme.vineandbarrel.platform.registry;

import jexxme.vineandbarrel.platform.Services;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * The loader-neutral twin of NeoForge's {@code DeferredRegister}: the same API (the parts this mod uses), so registry
 * classes read exactly as they did on NeoForge. Entries are collected here; {@link #register()} hands them to the loader,
 * which adds them to the game's registry at the right time (NeoForge: its {@code RegisterEvent}; Fabric: during mod
 * initialisation, in the game's registry order). The holders look their value up in the game's registry by key.
 */
public class DeferredRegister<T>
{
	private final ResourceKey<? extends Registry<T>> registryKey;
	private final String namespace;
	private final Map<Identifier, Function<Identifier, ? extends T>> entries = new LinkedHashMap<>();
	private boolean registered;

	protected DeferredRegister(ResourceKey<? extends Registry<T>> registryKey, String namespace) {
		this.registryKey = Objects.requireNonNull(registryKey);
		this.namespace = Objects.requireNonNull(namespace);
	}

	public static <T> DeferredRegister<T> create(ResourceKey<? extends Registry<T>> registryKey, String namespace) {
		return new DeferredRegister<>(registryKey, namespace);
	}

	public static Blocks createBlocks(String namespace) {
		return new Blocks(namespace);
	}

	public static Items createItems(String namespace) {
		return new Items(namespace);
	}

	public static DataComponents createDataComponents(ResourceKey<Registry<DataComponentType<?>>> registryKey, String namespace) {
		return new DataComponents(registryKey, namespace);
	}

	public <I extends T> DeferredHolder<T, I> register(String name, Supplier<? extends I> factory) {
		return register(name, id -> factory.get());
	}

	public <I extends T> DeferredHolder<T, I> register(String name, Function<Identifier, ? extends I> factory) {
		if (registered) {
			throw new IllegalStateException("Cannot add " + name + " to " + registryKey.identifier() + " after it was handed to the loader");
		}
		Identifier id = Identifier.fromNamespaceAndPath(namespace, name);
		if (entries.putIfAbsent(id, Objects.requireNonNull(factory)) != null) {
			throw new IllegalArgumentException("Duplicate registration " + id);
		}
		return createHolder(ResourceKey.create(registryKey, id));
	}

	protected <I extends T> DeferredHolder<T, I> createHolder(ResourceKey<T> key) {
		return new DeferredHolder<>(key);
	}

	/** Hands every entry to the loader (call once, from the mod's setup). */
	public void register() {
		if (registered) {
			throw new IllegalStateException("Registered twice: " + registryKey.identifier());
		}
		registered = true;
		Services.PLATFORM.register(this);
	}

	public ResourceKey<? extends Registry<T>> getRegistryKey() {
		return registryKey;
	}

	public String getNamespace() {
		return namespace;
	}

	/** The entries in the order they were added: id, and the factory that makes the value (call it once). */
	public Map<Identifier, Function<Identifier, ? extends T>> getEntries() {
		return Collections.unmodifiableMap(entries);
	}

	public static class Blocks extends DeferredRegister<Block>
	{
		protected Blocks(String namespace) {
			super(Registries.BLOCK, namespace);
		}

		@Override
		@SuppressWarnings("unchecked")
		public <B extends Block> DeferredBlock<B> register(String name, Function<Identifier, ? extends B> factory) {
			return (DeferredBlock<B>) super.register(name, factory);
		}

		@Override
		public <B extends Block> DeferredBlock<B> register(String name, Supplier<? extends B> factory) {
			return register(name, id -> factory.get());
		}

		public <B extends Block> DeferredBlock<B> registerBlock(String name, Function<BlockBehaviour.Properties, ? extends B> factory, Supplier<BlockBehaviour.Properties> properties) {
			return register(name, id -> factory.apply(properties.get().setId(ResourceKey.create(Registries.BLOCK, id))));
		}

		public <B extends Block> DeferredBlock<B> registerBlock(String name, Function<BlockBehaviour.Properties, ? extends B> factory, UnaryOperator<BlockBehaviour.Properties> properties) {
			return registerBlock(name, factory, () -> properties.apply(BlockBehaviour.Properties.of()));
		}

		public <B extends Block> DeferredBlock<B> registerBlock(String name, Function<BlockBehaviour.Properties, ? extends B> factory) {
			return registerBlock(name, factory, UnaryOperator.identity());
		}

		@Override
		protected <I extends Block> DeferredBlock<I> createHolder(ResourceKey<Block> key) {
			return new DeferredBlock<>(key);
		}
	}

	public static class Items extends DeferredRegister<Item>
	{
		protected Items(String namespace) {
			super(Registries.ITEM, namespace);
		}

		@Override
		@SuppressWarnings("unchecked")
		public <I extends Item> DeferredItem<I> register(String name, Function<Identifier, ? extends I> factory) {
			return (DeferredItem<I>) super.register(name, factory);
		}

		@Override
		public <I extends Item> DeferredItem<I> register(String name, Supplier<? extends I> factory) {
			return register(name, id -> factory.get());
		}

		public <I extends Item> DeferredItem<I> registerItem(String name, Function<Item.Properties, ? extends I> factory, Supplier<Item.Properties> properties) {
			return register(name, id -> factory.apply(properties.get().setId(ResourceKey.create(Registries.ITEM, id))));
		}

		public <I extends Item> DeferredItem<I> registerItem(String name, Function<Item.Properties, ? extends I> factory, UnaryOperator<Item.Properties> properties) {
			return registerItem(name, factory, () -> properties.apply(new Item.Properties()));
		}

		public <I extends Item> DeferredItem<I> registerItem(String name, Function<Item.Properties, ? extends I> factory) {
			return registerItem(name, factory, UnaryOperator.identity());
		}

		public DeferredItem<Item> registerSimpleItem(String name, Supplier<Item.Properties> properties) {
			return registerItem(name, Item::new, properties);
		}

		public DeferredItem<Item> registerSimpleItem(String name, UnaryOperator<Item.Properties> properties) {
			return registerItem(name, Item::new, properties);
		}

		public DeferredItem<Item> registerSimpleItem(String name) {
			return registerItem(name, Item::new, UnaryOperator.identity());
		}

		public DeferredItem<BlockItem> registerSimpleBlockItem(String name, Supplier<? extends Block> block, Supplier<Item.Properties> properties) {
			return registerItem(name, p -> new BlockItem(block.get(), p), () -> properties.get().useBlockDescriptionPrefix());
		}

		public DeferredItem<BlockItem> registerSimpleBlockItem(String name, Supplier<? extends Block> block) {
			return registerSimpleBlockItem(name, block, Item.Properties::new);
		}

		public DeferredItem<BlockItem> registerSimpleBlockItem(DeferredBlock<? extends Block> block, Supplier<Item.Properties> properties) {
			return registerSimpleBlockItem(block.getId().getPath(), block, properties);
		}

		public DeferredItem<BlockItem> registerSimpleBlockItem(DeferredBlock<? extends Block> block, UnaryOperator<Item.Properties> properties) {
			return registerSimpleBlockItem(block, () -> properties.apply(new Item.Properties()));
		}

		public DeferredItem<BlockItem> registerSimpleBlockItem(DeferredBlock<? extends Block> block) {
			return registerSimpleBlockItem(block, Item.Properties::new);
		}

		@Override
		protected <I extends Item> DeferredItem<I> createHolder(ResourceKey<Item> key) {
			return new DeferredItem<>(key);
		}
	}

	public static class DataComponents extends DeferredRegister<DataComponentType<?>>
	{
		protected DataComponents(ResourceKey<Registry<DataComponentType<?>>> registryKey, String namespace) {
			super(registryKey, namespace);
		}

		public <D> DeferredHolder<DataComponentType<?>, DataComponentType<D>> registerComponentType(String name, UnaryOperator<DataComponentType.Builder<D>> builder) {
			return register(name, () -> builder.apply(DataComponentType.builder()).build());
		}
	}
}
