package jexxme.vineandbarrel.watch.client;

import com.mojang.blaze3d.platform.InputConstants;
import jexxme.vineandbarrel.mixin.AbstractContainerScreenAccessor;
import jexxme.vineandbarrel.platform.client.ClientRegistrar;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Client side of the "Watch it" scenes: the renderer, the scene index, the key (G, rebindable) and where it opens a
 * scene: over an item (an inventory slot or JEI's lists, the tooltip says so) or at one of our blocks in the world, which
 * opens the scene at the step that block is on. Nothing opens by itself.
 */
public final class WatchClient
{
	public static final KeyMapping KEY = new KeyMapping("key.vineandbarrel.watch", InputConstants.KEY_G,
		KeyMapping.Category.INVENTORY);

	/** The item under the mouse in JEI's lists, when JEI is there (its plugin sets this). */
	private static Supplier<ItemStack> jeiHovered = () -> ItemStack.EMPTY;
	/** Whether JEI's search field is being typed in. */
	private static BooleanSupplier jeiTyping = () -> false;

	private WatchClient() {
	}

	public static void register(ClientRegistrar registrar) {
		registrar.pictureInPicture(SceneRenderState.class, SceneRenderer::new);
		registrar.keyMapping(KEY);
		registrar.reloadListener(WatchIndex.ID, WatchIndex.INSTANCE);
	}

	public static void setJei(Supplier<ItemStack> hovered, BooleanSupplier typing) {
		jeiHovered = hovered;
		jeiTyping = typing;
	}

	/** The tooltip's last line on an item that opens a scene: "Press [G] to watch". */
	public static void onTooltip(ItemStack stack, List<Component> lines) {
		if (KEY.isUnbound() || Minecraft.getInstance().level == null || WatchIndex.INSTANCE.forItem(stack).isEmpty()
			|| Minecraft.getInstance().gui.screen() instanceof WatchScreen) {
			return;
		}
		Component key = KEY.getTranslatedKeyMessage().copy().withStyle(ChatFormatting.YELLOW);
		lines.add(Component.translatable("watch.vineandbarrel.press", key).withStyle(ChatFormatting.GRAY));
	}

	/** A key pressed in a screen: the watch key over an item opens its scene (and Escape comes back here). */
	public static boolean onScreenKey(Screen screen, KeyEvent event) {
		if (screen instanceof WatchScreen || !KEY.matches(event) || isTyping(screen)) {
			return false;
		}
		ItemStack hovered = jeiHovered.get();
		if (hovered.isEmpty() && screen instanceof AbstractContainerScreen<?> container) {
			Slot slot = ((AbstractContainerScreenAccessor) container).vineandbarrel$hoveredSlot();
			if (slot != null) {
				hovered = slot.getItem();
			}
		}
		Optional<net.minecraft.resources.Identifier> scene = WatchIndex.INSTANCE.forItem(hovered);
		return scene.isPresent() && WatchScreen.open(scene.get(), 0, null, screen);
	}

	/** Whether a key now types into a text field (a search box, a sign, the anvil's name): then it isn't the watch key. */
	private static boolean isTyping(Screen screen) {
		if (screen.getFocused() instanceof EditBox || jeiTyping.getAsBoolean()) {
			return true;
		}
		for (GuiEventListener child : screen.children()) {
			if (child.capturesInput()) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Each client tick: the watch key pressed in the world at one of our blocks opens its scene where that block is; at
	 * anything else (no block, or one without a scene) it opens the Brewer's path.
	 */
	public static void onClientTick() {
		Minecraft minecraft = Minecraft.getInstance();
		while (KEY.consumeClick()) {
			if (minecraft.gui.screen() != null || minecraft.level == null) {
				continue;
			}
			Optional<WatchIndex.BlockEntry> entry = Optional.empty();
			if (minecraft.hitResult != null && minecraft.hitResult.getType() == HitResult.Type.BLOCK) {
				var pos = ((BlockHitResult) minecraft.hitResult).getBlockPos();
				entry = WatchIndex.INSTANCE.forBlock(minecraft.level, pos, minecraft.level.getBlockState(pos));
			}
			if (entry.isPresent()) {
				WatchScreen.open(entry.get().scene(), entry.get().step(), entry.get().now().map(Component::translatable).orElse(null), null);
			} else if (WatchPathScreen.available()) {
				minecraft.gui.setScreen(new WatchPathScreen(null));
			}
		}
	}
}
