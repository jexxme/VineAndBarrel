package jexxme.vineandbarrel.forge;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import jexxme.vineandbarrel.VineAndBarrel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Adds another loot table's items to a table's loot: NeoForge 21.1's {@code neoforge:add_table} global loot modifier,
 * which Forge 47 does not have, under our own id {@code vineandbarrel:add_table} (Farmer's Delight 1.20.1 does the same
 * with its {@code farmersdelight:add_loot_table}). The added table is rolled raw, as NeoForge's is: no global loot
 * modifiers run on it again.
 */
public class AddTableLootModifier extends LootModifier
{
	public static final DeferredRegister<Codec<? extends IGlobalLootModifier>> SERIALIZERS =
			DeferredRegister.create(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, VineAndBarrel.MODID);

	public static final RegistryObject<Codec<AddTableLootModifier>> CODEC = SERIALIZERS.register("add_table", () ->
			RecordCodecBuilder.create(builder -> codecStart(builder)
					.and(ResourceLocation.CODEC.fieldOf("table").forGetter(AddTableLootModifier::table))
					.apply(builder, AddTableLootModifier::new)));

	private final ResourceLocation table;

	public AddTableLootModifier(LootItemCondition[] conditions, ResourceLocation table) {
		super(conditions);
		this.table = table;
	}

	public static void register(IEventBus modEventBus) {
		SERIALIZERS.register(modEventBus);
	}

	public ResourceLocation table() {
		return table;
	}

	@Override
	@SuppressWarnings("deprecation")
	protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
		LootTable extra = context.getResolver().getLootTable(table);
		// Raw: the modifiers already run on the table this one adds to (getRandomItems would run them again, on the same
		// queried table id, without end)
		extra.getRandomItemsRaw(context, LootTable.createStackSplitter(context.getLevel(), generatedLoot::add));
		return generatedLoot;
	}

	@Override
	public Codec<? extends IGlobalLootModifier> codec() {
		return CODEC.get();
	}
}
