package jexxme.vineandbarrel.forge;

import jexxme.vineandbarrel.VineAndBarrel;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.RecipeBook;
import net.minecraft.stats.RecipeBookSettings;
import net.minecraft.world.inventory.RecipeBookType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * The Brew Kettle's own recipe book on the network. {@code VINEANDBARREL_BREWING} is a constant of vanilla's
 * {@link RecipeBookType} (made in {@link ForgePlatform}), and Forge 47 keeps its settings (open, filtering) in vanilla's
 * map and saves them with the player. But vanilla sends the settings of every constant, one after the other, in its
 * recipe packet, which every joining player gets: a server without Vine & Barrel sends one type fewer than a client
 * with it reads, and the client is thrown out ("Internal Exception: DecoderException"). So the kettle's type stays out
 * of everything vanilla sends that counts the enum's constants:
 * <ul>
 * <li>The settings packet is byte for byte what it is without the mod ({@code RecipeBookSettingsMixin}). A server with
 * Vine & Barrel sends the kettle book's settings on join in {@link Settings} instead; settings that came from vanilla's
 * packet (which can't carry them) don't overwrite the known ones.</li>
 * <li>The packet a client sends when a book is opened or filtered names the type by its place in the enum. The
 * kettle's type is counted last there, whatever its place ({@code ServerboundRecipeBookChangeSettingsPacketMixin}):
 * mods are constructed side by side, so with another mod that adds a type (Farmer's Delight's cooking pot) the places
 * can differ between the client and the server, and each other's settings would be changed.</li>
 * </ul>
 */
public final class ForgeRecipeBook
{
	public static final String BREWING = "VINEANDBARREL_BREWING";
	private static final String PROTOCOL = "1";

	private ForgeRecipeBook() {
	}

	public static RecipeBookType brewingType() {
		return ForgePlatform.brewingBook();
	}

	/** By name: the mixins ask before {@link ForgePlatform} has made the constant, too. */
	public static boolean isBrewing(RecipeBookType type) {
		return type.name().equals(BREWING);
	}

	/** The types as vanilla's packets count them: every type but the kettle's, in the enum's order. */
	public static RecipeBookType[] withoutBrewing(RecipeBookType[] types) {
		int count = 0;
		for (RecipeBookType type : types) {
			if (!isBrewing(type)) {
				count++;
			}
		}
		RecipeBookType[] others = new RecipeBookType[count];
		int next = 0;
		for (RecipeBookType type : types) {
			if (!isBrewing(type)) {
				others[next++] = type;
			}
		}
		return others;
	}

	/** A type's number in the packet that changes a book's settings: the kettle's type comes after all others. */
	public static int wireIndex(RecipeBookType type) {
		RecipeBookType[] others = withoutBrewing(RecipeBookType.values());
		for (int i = 0; i < others.length; i++) {
			if (others[i] == type) {
				return i;
			}
		}
		return others.length;
	}

	public static RecipeBookType typeAt(int wireIndex) {
		RecipeBookType[] others = withoutBrewing(RecipeBookType.values());
		// (A number past the end fails as vanilla's own read does)
		return wireIndex == others.length ? brewingType() : others[wireIndex];
	}

	/** On every {@link RecipeBookSettings}, by the settings mixin. */
	public interface FromPacket
	{
		/** True for settings read from vanilla's packet, and their copies: they don't know the kettle book's settings. */
		boolean vineandbarrel$brewingUnknown();
	}

	/** The kettle book's settings for a joining client, from a server with Vine & Barrel. */
	public record Settings(boolean open, boolean filtering)
	{
		private static void write(Settings settings, FriendlyByteBuf buffer) {
			buffer.writeBoolean(settings.open);
			buffer.writeBoolean(settings.filtering);
		}

		private static Settings read(FriendlyByteBuf buffer) {
			return new Settings(buffer.readBoolean(), buffer.readBoolean());
		}

		/** On the client: the kettle book's settings as this server saved them for the player. */
		public void applyTo(RecipeBook book) {
			// Through the book's own setter: getBookSettings hands out a copy
			book.setBookSetting(brewingType(), open, filtering);
		}
	}

	static void init() {
		// Like the attachments' channel: a server with the mod wants it on the client, a client joins servers without it.
		// Made here, while the mod is constructed (the mixins load this class at any time, and channels can't be made late)
		SimpleChannel channel = NetworkRegistry.newSimpleChannel(VineAndBarrel.id("brewing_book_settings"), () -> PROTOCOL,
			NetworkRegistry.acceptMissingOr(PROTOCOL), PROTOCOL::equals);
		channel.registerMessage(0, Settings.class, Settings::write, Settings::read, ForgeRecipeBook::onSettings, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
		// After vanilla's own recipe packet of the join
		MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
			if (event.getEntity() instanceof ServerPlayer player && player.connection != null) {
				RecipeBook book = player.getRecipeBook();
				RecipeBookType type = brewingType();
				channel.send(PacketDistributor.PLAYER.with(() -> player), new Settings(book.isOpen(type), book.isFiltering(type)));
			}
		});
	}

	private static void onSettings(Settings settings, Supplier<NetworkEvent.Context> context) {
		NetworkEvent.Context ctx = context.get();
		if (FMLEnvironment.dist == Dist.CLIENT) {
			ctx.enqueueWork(() -> jexxme.vineandbarrel.forge.client.ClientRecipeBook.receive(settings));
		}
		ctx.setPacketHandled(true);
	}
}
