package jexxme.vineandbarrel.fabric;

import jexxme.vineandbarrel.VineAndBarrel;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.stats.RecipeBook;
import net.minecraft.stats.RecipeBookSettings;
import net.minecraft.world.inventory.RecipeBookType;

/**
 * The Brew Kettle's own recipe book on Fabric, as NeoForge has it: {@code VINEANDBARREL_BREWING} is added to vanilla's
 * {@link RecipeBookType} by {@code RecipeBookTypeMixin} (and its tabs to the client's {@code RecipeBookCategories} by
 * {@code RecipeBookCategoriesMixin}; see {@code FabricClientRecipeBook}). Vanilla keeps each type's book settings (open,
 * filtering) in a map that it sends type by type, so the settings mixin keeps ours next to it and saves them with the
 * player.
 * <p>
 * On the network vanilla's settings packet stays byte for byte vanilla's, so a client with Vine & Barrel can join a
 * server without it. A server with Vine & Barrel sends the kettle book's settings on join in {@link Payload}; the
 * client changes them with vanilla's own settings packet, which only a server with the kettle's book type receives
 * (only its kettle menu opens that book).
 */
public final class FabricRecipeBook
{
	public static final String BREWING = "VINEANDBARREL_BREWING";
	/** The kettle book's two flags in the player's saved recipe book, next to vanilla's ({@code isFurnaceGuiOpen}, ...). */
	public static final String TAG_OPEN = "isVineandbarrelBrewingGuiOpen";
	public static final String TAG_FILTERING = "isVineandbarrelBrewingFilteringCraftable";

	private FabricRecipeBook() {
	}

	public static RecipeBookType brewingType() {
		return RecipeBookType.valueOf(BREWING);
	}

	public static boolean isBrewing(RecipeBookType type) {
		return type.name().equals(BREWING);
	}

	/** The kettle book's settings, kept on every {@link RecipeBookSettings} by the settings mixin. */
	public interface BrewingSettings
	{
		/** False while unknown (a fresh object, or one decoded from vanilla's packet): then both flags read false. */
		boolean vineandbarrel$brewingKnown();

		boolean vineandbarrel$brewingOpen();

		boolean vineandbarrel$brewingFiltering();

		/** Sets both flags; they are known from then on. */
		void vineandbarrel$setBrewing(boolean open, boolean filtering);
	}

	/** The kettle book's settings for a joining client, from a server with Vine & Barrel. */
	public record Payload(boolean open, boolean filtering)
	{
		public static final ResourceLocation CHANNEL = VineAndBarrel.id("brewing_book_settings");

		public FriendlyByteBuf write() {
			FriendlyByteBuf buffer = PacketByteBufs.create();
			buffer.writeBoolean(open);
			buffer.writeBoolean(filtering);
			return buffer;
		}

		public static Payload read(FriendlyByteBuf buffer) {
			return new Payload(buffer.readBoolean(), buffer.readBoolean());
		}

		/** On the client: the kettle book's settings as this server saved them for the player. */
		public void applyTo(RecipeBook book) {
			// Through the book's own setter (the settings mixin answers it for our type): getBookSettings hands out a copy
			book.setBookSetting(brewingType(), open, filtering);
		}
	}

	static void register() {
		ServerPlayConnectionEvents.JOIN.register((listener, sender, server) -> {
			if (ServerPlayNetworking.canSend(listener, Payload.CHANNEL)) {
				RecipeBook book = listener.player.getRecipeBook();
				sender.sendPacket(Payload.CHANNEL, new Payload(book.isOpen(brewingType()), book.isFiltering(brewingType())).write());
			}
		});
	}
}
