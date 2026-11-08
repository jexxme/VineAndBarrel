package jexxme.vineandbarrel.fabric;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import jexxme.vineandbarrel.VineAndBarrel;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.stats.RecipeBookSettings;
import net.minecraft.world.inventory.RecipeBookType;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * The Brew Kettle's own recipe book on Fabric, as NeoForge has it: {@code VINEANDBARREL_BREWING} is added to vanilla's
 * {@link RecipeBookType} (and its search tab to {@code SearchRecipeBookCategory}) by the class tweaker's
 * {@code extend-enum} and the enum mixins. Vanilla keeps each type's book settings (open, filtering) in a fixed field,
 * so the settings mixin keeps ours next to them and saves them with the player ({@link #withBrewing}).
 * <p>
 * On the network vanilla's settings packet stays byte for byte vanilla's, so a client with Vine & Barrel can join a
 * server without it. A server with Vine & Barrel sends the kettle book's settings on join in {@link Payload}; the
 * client changes them with vanilla's own settings packet, which only a server with the kettle's book type receives
 * (only its kettle menu opens that book).
 */
public final class FabricRecipeBook
{
	public static final String BREWING = "VINEANDBARREL_BREWING";

	private static volatile List<RecipeBookCategory> searchIncluded = List.of();

	private FabricRecipeBook() {
	}

	public static RecipeBookType brewingType() {
		return RecipeBookType.valueOf(BREWING);
	}

	/** The categories the kettle's search tab shows (set by the client registration). */
	public static List<RecipeBookCategory> searchIncluded() {
		return searchIncluded;
	}

	public static void setSearchIncluded(List<RecipeBookCategory> categories) {
		searchIncluded = List.copyOf(categories);
	}

	/** The kettle book's settings, kept on every {@link RecipeBookSettings} by the settings mixin. */
	public interface BrewingSettings
	{
		/** The settings, or null while unknown (a fresh object, or one decoded from vanilla's packet). */
		RecipeBookSettings.@Nullable TypeSettings vineandbarrel$knownBrewing();

		void vineandbarrel$setBrewing(RecipeBookSettings.@Nullable TypeSettings settings);

		default RecipeBookSettings.TypeSettings vineandbarrel$brewing() {
			RecipeBookSettings.TypeSettings known = vineandbarrel$knownBrewing();
			return known != null ? known : RecipeBookSettings.TypeSettings.DEFAULT;
		}
	}

	private static BrewingSettings brewing(RecipeBookSettings settings) {
		return (BrewingSettings) (Object) settings;
	}

	private static final MapCodec<RecipeBookSettings.TypeSettings> BREWING_MAP_CODEC = RecordCodecBuilder.mapCodec(builder -> builder.group(
			Codec.BOOL.optionalFieldOf("isVineandbarrelBrewingGuiOpen", false).forGetter(RecipeBookSettings.TypeSettings::open),
			Codec.BOOL.optionalFieldOf("isVineandbarrelBrewingFilteringCraftable", false).forGetter(RecipeBookSettings.TypeSettings::filtering)
	).apply(builder, RecipeBookSettings.TypeSettings::new));

	/** Vanilla's saved settings plus the kettle book's two flags (saved with the player, never sent). */
	public static MapCodec<RecipeBookSettings> withBrewing(MapCodec<RecipeBookSettings> vanilla) {
		return Codec.mapPair(vanilla, BREWING_MAP_CODEC).xmap(pair -> {
			brewing(pair.getFirst()).vineandbarrel$setBrewing(pair.getSecond());
			return pair.getFirst();
		}, settings -> Pair.of(settings, brewing(settings).vineandbarrel$brewing()));
	}

	/** The kettle book's settings for a joining client, from a server with Vine & Barrel. */
	public record Payload(boolean open, boolean filtering) implements CustomPacketPayload
	{
		public static final Type<Payload> TYPE = new Type<>(VineAndBarrel.id("brewing_book_settings"));
		public static final StreamCodec<ByteBuf, Payload> CODEC = RecipeBookSettings.TypeSettings.STREAM_CODEC
				.map(settings -> new Payload(settings.open(), settings.filtering()), payload -> new RecipeBookSettings.TypeSettings(payload.open(), payload.filtering()));

		@Override
		public Type<Payload> type() {
			return TYPE;
		}

		/** On the client: the kettle book's settings as this server saved them for the player. */
		public void applyTo(RecipeBookSettings settings) {
			brewing(settings).vineandbarrel$setBrewing(new RecipeBookSettings.TypeSettings(open, filtering));
		}
	}

	static void register() {
		PayloadTypeRegistry.clientboundPlay().register(Payload.TYPE, Payload.CODEC);
		ServerPlayConnectionEvents.JOIN.register((listener, sender, server) -> {
			if (ServerPlayNetworking.canSend(listener, Payload.TYPE)) {
				RecipeBookSettings.TypeSettings saved = brewing(listener.player.getRecipeBook().getBookSettings()).vineandbarrel$brewing();
				sender.sendPacket(new Payload(saved.open(), saved.filtering()));
			}
		});
	}
}
