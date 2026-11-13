package jexxme.vineandbarrel.watch.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import jexxme.vineandbarrel.VineAndBarrel;
import jexxme.vineandbarrel.platform.Services;
import jexxme.vineandbarrel.watch.WatchScene;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * "Watch it": a short scene of the real blocks, one step at a time, in a window in vanilla's container look. The
 * stage shows the scene; each step's caption sits beside the block it is about (outlined as in the world), tied to it
 * by a short line. One row under the stage: what you need (click for recipes), the numbered steps (click one to jump,
 * hover for its caption; the arrow keys step too), the Brewer's path and Done. A small button in the stage's corner pauses
 * and plays (so does Space, or a click on the scene); drag to turn, scroll to zoom, double-click to set the view back.
 * At the end the scene keeps its last frame, and a small box offers what's next.
 */
public class WatchScreen extends Screen
{
	/** The window's size in GUI pixels, as vanilla's screens have one: the GUI scale sets how big it is on screen. */
	private static final int WINDOW_W = 400, WINDOW_H = 240;

	/** The line and the outline that tie a caption to its block. */
	static final int ACCENT = 0xFFFFFFFF;
	private static final int INK = 0xFFFFFFFF;
	private static final int INK_MUTED = 0xFFA0A0A0;
	private static final int TITLE_INK = 0xFF404040;
	private static final int TITLE_MUTED = 0xFF707070;
	private static final int HIGHLIGHT = 0xFFFFFF55;
	/** The stage when no days pass: a dim cellar. */
	private static final int STAGE = 0xFF1D2327;
	private static final int NIGHT = 0xFF141C30;
	private static final int DAY = 0xFF79A6DC;
	// This Minecraft version has no GUI sprites: each is drawn from its texture file (the nine-slice ones by hand, with
	// the size and border their .mcmeta names)
	static final ResourceLocation PANEL = VineAndBarrel.id("textures/gui/sprites/watch/panel.png");
	static final int PANEL_SIZE = 16, PANEL_BORDER = 4;
	static final ResourceLocation INSET = VineAndBarrel.id("textures/gui/sprites/watch/window.png");
	static final int INSET_SIZE = 8, INSET_BORDER = 2;
	/** A container's slot, out of the chest screen's texture (at 7, 17). */
	private static final ResourceLocation SLOT = new ResourceLocation("textures/gui/container/generic_54.png");
	/** Vanilla's buttons: disabled, plain and highlighted, each 200 x 20, from y = 46 down. */
	private static final ResourceLocation WIDGETS = new ResourceLocation("textures/gui/widgets.png");
	/** The sky's sun (32 pixels) and moon phases (a 4 x 2 sheet of 32-pixel phases; the full moon is the first). */
	private static final ResourceLocation SUN = new ResourceLocation("textures/environment/sun.png");
	private static final ResourceLocation MOON = new ResourceLocation("textures/environment/moon_phases.png");
	/** The sun's and the moon's textures (32 pixels) drawn at twice that. */
	private static final int CELESTIAL = 64;

	private static final int HEADER = 20;
	private static final int FOOTER = 36;
	private static final int CHIP = 20;
	private static final int DONE_W = 64;
	private static final int SLOT_SIZE = 18;
	private static final int LINE_H = 10;
	private static final int TAG_PAD = 6;
	/** A caption's widest line: about 35 characters. */
	private static final int TAG_MAX = 200;
	private static final int FADE_MS = 150;
	private static final int END_W = 156;

	private final ScenePlayer player;
	private final SceneView view;
	/** What the player's own block is doing, said while the scene is at the step it opened at (opened from a block). */
	private final @Nullable Component now;
	private final int nowStep;
	/** Where Escape goes back to: the inventory, JEI page or path the scene was opened from. */
	private final @Nullable Screen parent;
	/** Watching the Brewer's path in order: its stations' scenes, this one among them (null: a single scene). */
	private @Nullable List<ResourceLocation> chapters;

	private int ox, oy, ow, oh;
	private int stageX, stageY, stageW, stageH;
	private int footerY, railX, railY, chipsX;
	private int tagW, tagH;
	private int cardX, cardY, cardW, cardH;
	private boolean tagOnLeft;
	private int tagStep = -1;
	private long stepShownAt;
	/** The game may pause behind this screen: the scene keeps its own clock between ticks. */
	private long lastTick = Util.getNanos();
	private boolean dragging, moved;
	private double pressX, pressY;
	/** The last click, to tell a double click (this Minecraft version doesn't). */
	private long lastClickAt;
	private int lastClickButton = -1;
	private @Nullable Button pauseButton, playButton;
	private final List<Button> endButtons = new ArrayList<>();

	public WatchScreen(ScenePlayer player, @Nullable Component now, @Nullable Screen parent) {
		super(Component.translatable(player.scene().title()));
		this.player = player;
		this.view = new SceneView(player.scene().view());
		this.now = now;
		this.nowStep = player.step();
		this.parent = parent;
	}

	public static boolean open(ResourceLocation id) {
		return open(id, 0, null, null);
	}

	public static boolean open(ResourceLocation id, int step, @Nullable Component now, @Nullable Screen parent) {
		return open(id, step, now, parent, Optional.empty());
	}

	/**
	 * Opens a scene; one written for an aging recipe plays {@code recipe} instead if given (a wort bucket asked for: the
	 * barrel scene pours that wort and taps it into a mug).
	 */
	public static boolean open(ResourceLocation id, int step, @Nullable Component now, @Nullable Screen parent, Optional<ResourceLocation> recipe) {
		Minecraft minecraft = Minecraft.getInstance();
		Optional<WatchScene> scene = WatchIndex.INSTANCE.scene(id);
		if (minecraft.level == null || scene.isEmpty()) {
			return false;
		}
		ScenePlayer player = new ScenePlayer(scene.get(), minecraft.level, recipe);
		if (step > 0) {
			player.jumpTo(step);
		}
		minecraft.setScreen(new WatchScreen(player, now, parent));
		return true;
	}

	/** Plays the path's scenes in order like chapters, starting at {@code id}. */
	static boolean openChapters(ResourceLocation id, List<ResourceLocation> chapters, @Nullable Screen parent) {
		if (!open(id, 0, null, parent)) {
			return false;
		}
		if (Minecraft.getInstance().screen instanceof WatchScreen screen) {
			screen.chapters = List.copyOf(chapters);
			screen.rebuildWidgets();
		}
		return true;
	}

	/** {@return the window's size on a screen this big (GUI pixels): its own, or the screen less a margin if smaller} */
	static int[] windowSize(int width, int height) {
		return new int[] {Math.min(WINDOW_W, width - 16), Math.min(WINDOW_H, height - 16)};
	}

	/** {@return where Done leads from a guide screen: past the scenes and the path, to where the guide was opened} */
	static @Nullable Screen outside(@Nullable Screen from) {
		Screen to = from;
		while (to instanceof WatchScreen || to instanceof WatchPathScreen) {
			to = to instanceof WatchScreen watch ? watch.parent : ((WatchPathScreen) to).parent();
		}
		return to;
	}

	public ScenePlayer player() {
		return player;
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}

	// ------------------------------------------------------------------ layout

	@Override
	protected void init() {
		int[] size = windowSize(width, height);
		ow = size[0];
		oh = size[1];
		ox = (width - ow) / 2;
		oy = (height - oh) / 2;
		stageX = ox + 8;
		stageY = oy + HEADER;
		stageW = ow - 16;
		footerY = oy + oh - FOOTER;
		stageH = footerY - 4 - stageY;
		railY = footerY + 12;

		// The caption is as wide and as tall as the scene's longest one needs, so it never changes size between steps, and
		// never wider than the room beside a block in the middle of the stage (half the stage, less the block's half-width
		// at this scale and the line): it sits beside its block, never over it, and in a narrow stage it wraps instead
		int beside = Math.round(stageW / 2.0F - 0.8F * sceneScale()) - 16 - 8;
		int max = Math.max(90, Math.min(TAG_MAX, Math.min(stageW / 2 - 24, beside)));
		int lines = 1;
		int widest = 0;
		for (int i = 0; i < player.scene().steps().size(); i++) {
			List<FormattedCharSequence> split = font.split(player.caption(i), max);
			lines = Math.max(lines, split.size());
			for (FormattedCharSequence line : split) {
				widest = Math.max(widest, font.width(line));
			}
		}
		tagW = widest + 2 * TAG_PAD;
		tagH = lines * LINE_H + 2 * TAG_PAD - 2;

		// One row under the stage: what you need (left), the numbered steps (middle), the path and Done (right). The steps
		// sit in the middle unless the slots would touch them; then they move right. Only when even that doesn't fit, the
		// slots give way (the brewer's hands still show what is in use).
		int rowY = footerY + 12;
		int steps = player.scene().steps().size();
		int chipsW = steps * (CHIP + 2) - 2;
		int group = chipsW;
		int railW = needs().size() * SLOT_SIZE;
		int left = ox + 8 + railW + 8;
		int right = ox + ow - 8 - DONE_W - 4 - 20 - 8;
		int start = ox + (ow - group) / 2;
		railX = -1;
		if (railW > 0 && left + group <= right) {
			railX = ox + 8;
			start = Math.max(start, left);
		} else {
			start = Math.max(start, ox + 8);
		}
		chipsX = start;
		Button path = addRenderableWidget(icon("watch/big/path", Component.translatable("watch.vineandbarrel.path"), 20, b -> openPath()));
		path.setPosition(ox + ow - 8 - DONE_W - 4 - 20, rowY);
		path.visible = WatchPathScreen.available();
		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> minecraft.setScreen(outside(parent)))
			.bounds(ox + ow - 8 - DONE_W, rowY, DONE_W, 20).build());

		// pause and play: a small button in the stage's corner, so a new player sees how (Space and a click work too)
		pauseButton = addRenderableWidget(icon("watch/big/pause", keyHint("watch.vineandbarrel.pause"), 18, b -> togglePause()));
		pauseButton.setPosition(stageX + 6, stageY + 6);
		playButton = addRenderableWidget(icon("watch/big/play", keyHint("watch.vineandbarrel.play"), 18, b -> togglePause()));
		playButton.setPosition(stageX + 6, stageY + 6);
		layoutEndCard();
		updateButtons();
	}

	private static Button icon(String sprite, Component name, int size, Button.OnPress action) {
		Button button = new IconButton(VineAndBarrel.id("textures/gui/sprites/" + sprite + ".png"), 14, name, size, action);
		button.setTooltip(Tooltip.create(name));
		return button;
	}

	/**
	 * A vanilla button with an icon in its middle instead of its name (the name is still what the narrator reads): what
	 * newer versions have as the sprite icon button.
	 */
	private static final class IconButton extends Button
	{
		private final ResourceLocation icon;
		private final int iconSize;

		IconButton(ResourceLocation icon, int iconSize, Component name, int size, OnPress action) {
			super(0, 0, size, size, name, action, DEFAULT_NARRATION);
			this.icon = icon;
			this.iconSize = iconSize;
		}

		@Override
		public void renderWidget(GuiGraphics gui, int mouseX, int mouseY, float a) {
			super.renderWidget(gui, mouseX, mouseY, a);
			gui.blit(icon, getX() + getWidth() / 2 - iconSize / 2, getY() + getHeight() / 2 - iconSize / 2, 0.0F, 0.0F, iconSize, iconSize, iconSize, iconSize);
		}

		@Override
		public void renderString(GuiGraphics gui, Font font, int color) {
		}
	}

	/**
	 * Draws a nine-slice GUI sprite from its texture file the way newer versions draw one out of the GUI atlas: the
	 * corners as they are, the edges and the middle tiled (not stretched). {@code size}: the square texture's side.
	 */
	static void blitNineSlice(GuiGraphics gui, ResourceLocation texture, int x, int y, int width, int height, int size, int border) {
		int bx = Math.min(border, width / 2), by = Math.min(border, height / 2);
		int innerW = width - 2 * bx, innerH = height - 2 * by;
		int tileW = size - 2 * bx, tileH = size - 2 * by;
		gui.blit(texture, x, y, 0.0F, 0.0F, bx, by, size, size);
		blitTiled(gui, texture, x + bx, y, innerW, by, bx, 0, tileW, by, size);
		gui.blit(texture, x + width - bx, y, size - bx, 0.0F, bx, by, size, size);
		gui.blit(texture, x, y + height - by, 0.0F, size - by, bx, by, size, size);
		blitTiled(gui, texture, x + bx, y + height - by, innerW, by, bx, size - by, tileW, by, size);
		gui.blit(texture, x + width - bx, y + height - by, size - bx, size - by, bx, by, size, size);
		blitTiled(gui, texture, x, y + by, bx, innerH, 0, by, bx, tileH, size);
		blitTiled(gui, texture, x + bx, y + by, innerW, innerH, bx, by, tileW, tileH, size);
		blitTiled(gui, texture, x + width - bx, y + by, bx, innerH, size - bx, by, bx, tileH, size);
	}

	private static void blitTiled(GuiGraphics gui, ResourceLocation texture, int x, int y, int width, int height, int u, int v, int tileW, int tileH,
								  int size) {
		if (tileW <= 0 || tileH <= 0) {
			return;
		}
		for (int i = 0; i < width; i += tileW) {
			int w = Math.min(tileW, width - i);
			for (int k = 0; k < height; k += tileH) {
				gui.blit(texture, x + i, y + k, u, v, w, Math.min(tileH, height - k), size, size);
			}
		}
	}

	/** "Pause (Space)": the action and its key, in the player's language. */
	private static Component keyHint(String action) {
		return Component.translatable(action).append(" (").append(Component.translatable("key.keyboard.space")).append(")");
	}

	/** The end box at the stage's top right: what's next, play again, the path, and the scene's other offers. */
	private void layoutEndCard() {
		endButtons.forEach(this::removeWidget);
		endButtons.clear();
		List<ResourceLocation> also = alsoScenes();
		int alsoLines = Math.max(0, also.size() - 1);
		cardW = END_W;
		cardH = also.isEmpty() ? 76 : 112 + (alsoLines > 0 ? 12 + alsoLines * 18 : 0);
		cardX = stageX + stageW - cardW - 8;
		cardY = stageY + 8;
		int y = cardY + (also.isEmpty() ? 28 : 44);
		if (!also.isEmpty()) {
			ResourceLocation next = also.get(0);
			endButtons.add(addRenderableWidget(Button.builder(Component.translatable("watch.vineandbarrel.watch_next"), b -> {
				if (chapters != null) {
					openChapters(next, chapters, parent);
				} else {
					open(next, 0, null, parent);
				}
			}).bounds(cardX + 8, y, cardW - 16, 20).build()));
			y += 22;
		}
		endButtons.add(addRenderableWidget(Button.builder(Component.translatable("watch.vineandbarrel.replay"), b -> replay())
			.bounds(cardX + 8, y, cardW - 16, 20).build()));
		if (WatchPathScreen.available()) {
			endButtons.add(addRenderableWidget(Button.builder(Component.translatable("watch.vineandbarrel.path"), b -> openPath())
				.bounds(cardX + 8, y + 22, cardW - 16, 20).build()));
		}
	}

	private void updateButtons() {
		boolean playing = !player.isPaused() && !player.isFinished();
		if (pauseButton != null && playButton != null) {
			pauseButton.visible = playing;
			playButton.visible = !playing && !player.isFinished();
		}
		for (Button button : endButtons) {
			button.visible = player.isFinished();
		}
	}

	private void togglePause() {
		if (player.isFinished()) {
			replay();
		} else {
			player.setPaused(!player.isPaused());
		}
		updateButtons();
	}

	private void back() {
		player.jumpTo(player.tick() > 10 || player.step() == 0 ? player.step() : player.step() - 1);
		updateButtons();
	}

	private void next() {
		if (player.step() + 1 < player.scene().steps().size()) {
			player.jumpTo(player.step() + 1);
			updateButtons();
		}
	}

	private void replay() {
		player.restart();
		player.setPaused(false);
		view.reset();
		updateButtons();
	}

	private void openPath() {
		minecraft.setScreen(new WatchPathScreen(this));
	}

	// ------------------------------------------------------------------ ticking

	@Override
	public void tick() {
		int step = player.step();
		boolean finished = player.isFinished();
		player.clientTick();
		lastTick = Util.getNanos();
		if (player.step() != step) {
			// the narrator reads each new step's caption, as it reads a screen's title
			minecraft.getNarrator().say(player.caption());
		}
		if (player.isFinished() != finished) {
			updateButtons();
			if (player.isFinished() && !endButtons.isEmpty()) {
				setFocused(endButtons.get(0));
			}
		}
	}

	/** {@return how far between two scene ticks it is now (the game's own partial tick stands still while it pauses)} */
	private float partialTick() {
		return Mth.clamp((Util.getNanos() - lastTick) / 50_000_000.0F, 0.0F, 1.0F);
	}

	@Override
	public boolean isPauseScreen() {
		return true;
	}

	// ------------------------------------------------------------------ drawing

	/**
	 * Everything under the buttons: the window, the scene and what is written over it. (On this Minecraft version a
	 * screen draws its background itself, and the world behind it is darkened, not blurred.)
	 */
	private void renderBackground(GuiGraphics gui, int mouseX, int mouseY, float frameTicks) {
		renderBackground(gui);
		gui.fill(0, 0, width, height, 0x60000000);
		RenderSystem.enableBlend();
		blitNineSlice(gui, PANEL, ox, oy, ow, oh, PANEL_SIZE, PANEL_BORDER);
		gui.fill(stageX, stageY, stageX + stageW, stageY + stageH, STAGE);

		float a = partialTick();
		// the title where a container has it, top left (watching the path: which station of how many)
		int titleRoom = ow - 16;
		Component chapter = chapters == null ? null
			: Component.translatable("watch.vineandbarrel.path.chapter", chapters.indexOf(sceneId()) + 1, chapters.size());
		if (chapter != null) {
			titleRoom -= font.width(chapter) + 6;
		}
		FormattedCharSequence titleLine = font.width(title) <= titleRoom ? title.getVisualOrderText()
			: Language.getInstance().getVisualOrder(FormattedText.composite(font.substrByWidth(title, titleRoom - font.width("…")), FormattedText.of("…")));
		gui.drawString(font, titleLine, ox + 8, oy + 6, TITLE_INK, false);
		if (chapter != null) {
			gui.drawString(font, chapter, ox + 8 + font.width(titleLine) + 6, oy + 6, TITLE_MUTED, false);
		}

		renderSky(gui, a);
		int sx = sceneX(), sy = sceneY();
		// finished, the scene keeps its last frame: what came of it, and what's next beside it
		SceneRenderer.render(gui,
			new SceneRenderState(player, view, a, sx, sy, sx + sceneW(), sy + sceneH(), sceneScale(), player.isFinished() ? null : anchorBlock(a)));
		RenderSystem.enableBlend();
		blitNineSlice(gui, INSET, stageX - 2, stageY - 2, stageW + 4, stageH + 4, INSET_SIZE, INSET_BORDER);

		int topY = stageY + 8;
		if (now != null && player.step() == nowStep && !player.isFinished()) {
			int w = font.width(now) + 12;
			int x = stageX + (stageW - w) / 2;
			gui.fill(x, topY, x + w, topY + 16, 0xC0000000);
			gui.drawCenteredString(font, now, stageX + stageW / 2, topY + 4, HIGHLIGHT);
			topY += 20;
		}
		if (player.isPaused() && !player.isFinished()) {
			Component paused = Component.translatable("watch.vineandbarrel.paused");
			int w = font.width(paused) + 12;
			gui.fill(stageX + (stageW - w) / 2, topY, stageX + (stageW + w) / 2, topY + 14, 0xA0000000);
			gui.drawCenteredString(font, paused, stageX + stageW / 2, topY + 3, INK);
		}
		if (player.isFinished()) {
			gui.fill(cardX, cardY, cardX + cardW, cardY + cardH, 0xD0000000);
			renderEndCard(gui);
		} else {
			renderTag(gui, a);
		}
		renderRail(gui);
		renderSteps(gui, mouseX, mouseY, a);
	}

	@Override
	public void render(GuiGraphics gui, int mouseX, int mouseY, float frameTicks) {
		renderBackground(gui, mouseX, mouseY, frameTicks);
		super.render(gui, mouseX, mouseY, frameTicks);
		renderHover(gui, mouseX, mouseY);
	}

	/** The scene's own area: the stage less a margin. */
	private int sceneX() {
		return stageX + 8;
	}

	private int sceneY() {
		return stageY + 8;
	}

	private int sceneW() {
		return stageW - 16;
	}

	private int sceneH() {
		return stageH - 16;
	}

	private float sceneScale() {
		return SceneView.scale(player.scene(), sceneW(), sceneH()) * 0.86F;
	}

	private Vec3 centre() {
		return SceneView.centre(player.scene());
	}

	private Vector3f toGui(Vec3 stagePoint) {
		Vector3f v = SceneView.project(SceneView.viewMatrix(view.yaw, view.pitch, view.zoom), stagePoint.subtract(centre()), sceneScale());
		return v.add(sceneX() + sceneW() / 2.0F, sceneY() + sceneH() / 2.0F, 0.0F);
	}

	/** The block the caption is about: the one at its point, or just under it (a pointer floats above what it shows). */
	private @Nullable BlockPos anchorBlock(float a) {
		Optional<Vec3> point = player.anchor(a);
		if (point.isEmpty() || player.currentStep().anchor().flatMap(WatchScene.Anchor::figure).isPresent()) {
			return null;
		}
		BlockPos at = BlockPos.containing(point.get());
		for (int i = 0; i < 2; i++) {
			if (at.getY() == 0 && player.scene().floor().isPresent()) {
				// the floor is never what a caption is about (a block the step puts up isn't there yet)
				return null;
			}
			if (!player.world().getBlockState(at).isAir()) {
				return at;
			}
			at = at.below();
		}
		return null;
	}

	/** Where the caption's line ends: the point the step names, else its block's middle, else the anchor (a figure's hands). */
	private Optional<Vec3> anchorPoint(float a) {
		Optional<Vec3> named = player.currentStep().anchor().flatMap(WatchScene.Anchor::pos);
		if (named.isPresent()) {
			return named;
		}
		BlockPos block = anchorBlock(a);
		if (block != null) {
			VoxelShape shape = player.world().getBlockState(block).getShape(player.world(), block, CollisionContext.empty());
			return Optional.of(shape.isEmpty() ? Vec3.atCenterOf(block) : shape.bounds().getCenter().add(block.getX(), block.getY(), block.getZ()));
		}
		return player.anchor(a);
	}

	private void renderTag(GuiGraphics gui, float a) {
		if (player.tick() < player.captionAt()) {
			// the block it is about isn't there yet (the barrel is still in the brewer's hands): the caption comes with it
			tagStep = -1;
			return;
		}
		if (tagStep != player.step()) {
			tagStep = player.step();
			stepShownAt = Util.getMillis();
		}
		// it fades in and rises a little, unless the player turned Distortion Effects off
		float fade = minecraft.options.screenEffectScale().get() <= 0.0 ? 1.0F
			: Mth.clamp((Util.getMillis() - stepShownAt) / (float) FADE_MS, 0.0F, 1.0F);
		float alpha = 1.0F - (1.0F - fade) * (1.0F - fade);
		int rise = Math.round((1.0F - alpha) * 4.0F);
		List<FormattedCharSequence> lines = font.split(player.caption(), tagW - 2 * TAG_PAD);
		Optional<Vec3> anchor = anchorPoint(a);
		int x, y;
		if (anchor.isPresent()) {
			Vector3f p = toGui(anchor.get());
			int px = Math.round(p.x), py = Math.round(p.y);
			int[] box = anchorBox(a, px, py);
			// The knot sits on the edge of the outlined thing at the height the step names (the tap: low on the barrel), and
			// the line runs straight out to the caption. It never crosses the thing, nor a figure (then it takes the other side).
			int ky = Mth.clamp(py, box[1] + 3, box[3] - 3);
			int right = box[2] + 16, left = box[0] - 16 - tagW;
			boolean fitsRight = right + tagW <= stageX + stageW - 8 && !figureBetween(box[2], right + tagW, ky);
			boolean fitsLeft = left >= stageX + 8 && !figureBetween(left, box[0], ky);
			tagOnLeft = tagOnLeft ? fitsLeft || !fitsRight : !fitsRight && fitsLeft;
			x = Mth.clamp(tagOnLeft ? left : right, stageX + 8, stageX + stageW - 8 - tagW);
			int kx = tagOnLeft ? box[0] : box[2];
			y = Mth.clamp(ky - tagH / 2, stageY + 8, stageY + stageH - tagH - 8);
			int lineY = Mth.clamp(ky, y + 3, y + tagH - 4);
			int edge = tagOnLeft ? x + tagW : x;
			int drawn = Math.round(alpha * Math.abs(edge - kx));
			int x0 = tagOnLeft ? kx - drawn : kx, x1 = tagOnLeft ? kx : kx + drawn;
			int lineColor = mulAlpha(ACCENT, alpha), shadow = mulAlpha(0x80000000, alpha);
			if (lineY != ky) {
				gui.fill(kx + 1, Math.min(ky, lineY) + 1, kx + 2, Math.max(ky, lineY) + 2, shadow);
				gui.fill(kx, Math.min(ky, lineY), kx + 1, Math.max(ky, lineY) + 1, lineColor);
			}
			gui.fill(x0 + 1, lineY + 1, x1 + 2, lineY + 2, shadow);
			gui.fill(x0, lineY, x1 + 1, lineY + 1, lineColor);
			gui.fill(kx - 1, ky - 1, kx + 2, ky + 2, lineColor);
		} else {
			x = stageX + (stageW - tagW) / 2;
			y = stageY + stageH - tagH - 12;
		}
		y += rise;
		gui.fill(x, y, x + tagW, y + tagH, mulAlpha(0xC0000000, alpha));
		int ty = y + TAG_PAD;
		for (FormattedCharSequence line : lines) {
			gui.drawString(font, line, x + TAG_PAD + (tagOnLeft ? 0 : 1), ty, mulAlpha(INK, Math.max(alpha, 0.05F)), false);
			ty += LINE_H;
		}
	}

	/** Whether a figure stands across a caption's line (from x0 to x1 at height y) on the screen. */
	private boolean figureBetween(int x0, int x1, int y) {
		for (SceneFigure figure : player.figures()) {
			Vec3 feet = figure.entity().position().subtract(ScenePlayer.PARTICLE_ORIGIN);
			int[] box = screenBox(feet.add(-0.3, 0.0, -0.3), feet.add(0.3, 1.9, 0.3));
			if (y >= box[1] - 4 && y <= box[3] + 4 && Math.max(x0, box[0]) < Math.min(x1, box[2])) {
				return true;
			}
		}
		return false;
	}

	/** The screen box (x0, y0, x1, y1) around what the caption is about: its block's shape, or a figure's body. */
	private int[] anchorBox(float a, int px, int py) {
		BlockPos block = anchorBlock(a);
		if (block != null) {
			VoxelShape shape = player.world().getBlockState(block).getShape(player.world(), block, CollisionContext.empty());
			AABB box = shape.isEmpty() ? new AABB(block) : shape.bounds().move(block);
			return screenBox(new Vec3(box.minX, box.minY, box.minZ), new Vec3(box.maxX, box.maxY, box.maxZ));
		}
		if (player.currentStep().anchor().flatMap(WatchScene.Anchor::figure).isPresent()) {
			Vec3 feet = player.anchor(a).orElseThrow().subtract(0.0, 1.05, 0.0);
			return screenBox(feet.add(-0.3, 0.0, -0.3), feet.add(0.3, 1.9, 0.3));
		}
		return new int[] {px - 4, py - 4, px + 4, py + 4};
	}

	private int[] screenBox(Vec3 min, Vec3 max) {
		int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, y1 = Integer.MIN_VALUE;
		for (int i = 0; i < 8; i++) {
			Vector3f c = toGui(new Vec3((i & 1) == 0 ? min.x : max.x, (i & 2) == 0 ? min.y : max.y, (i & 4) == 0 ? min.z : max.z));
			x0 = Math.min(x0, Math.round(c.x));
			y0 = Math.min(y0, Math.round(c.y));
			x1 = Math.max(x1, Math.round(c.x));
			y1 = Math.max(y1, Math.round(c.y));
		}
		return new int[] {x0, y0, x1, y1};
	}

	/** The steps as vanilla buttons: playing = highlighted with how far it has played, done = plain, ahead = disabled. */
	private void renderSteps(GuiGraphics gui, int mouseX, int mouseY, float a) {
		int steps = player.scene().steps().size();
		int hovered = stepAt(mouseX, mouseY);
		int y = footerY + 12;
		for (int i = 0; i < steps; i++) {
			int x = stepX(i);
			boolean current = i == player.step() && !player.isFinished();
			boolean past = player.isFinished() || i < player.step();
			// as vanilla draws a button of this size on this Minecraft version: highlighted, plain or disabled
			int look = current || i == hovered ? 2 : past ? 1 : 0;
			gui.blitNineSliced(WIDGETS, x, y, CHIP, CHIP, 20, 4, 200, 20, 0, 46 + look * 20);
			if (current) {
				gui.fill(x + 3, y + CHIP - 4, x + 3 + Math.round((CHIP - 6) * player.stepProgress(a)), y + CHIP - 3, INK);
			}
			Component number = Component.literal(String.valueOf(i + 1));
			if (current) {
				number = number.copy().withStyle(ChatFormatting.BOLD);
			}
			gui.drawString(font, number, x + (CHIP - font.width(number)) / 2 + 1, y + 6, past || current ? INK : INK_MUTED, true);
		}
	}

	private int stepX(int i) {
		return chipsX + i * (CHIP + 2);
	}

	private int stepAt(double x, double y) {
		if (y < footerY + 12 || y >= footerY + 12 + CHIP) {
			return -1;
		}
		for (int i = 0; i < player.scene().steps().size(); i++) {
			if (x >= stepX(i) && x < stepX(i) + CHIP) {
				return i;
			}
		}
		return -1;
	}

	// ------------------------------------------------------------------ what you need

	private List<ItemStack> needs() {
		return player.scene().needs().stream().map(player.parts()::item).filter(stack -> !stack.isEmpty()).toList();
	}

	/** "What you need": a container's row of slots with its label above; what the brewer holds now is framed. */
	private void renderRail(GuiGraphics gui) {
		if (railX < 0) {
			return;
		}
		gui.drawString(font, Component.translatable("watch.vineandbarrel.needs"), railX, footerY + 2, TITLE_INK, false);
		List<ItemStack> held = player.heldItems();
		int x = railX;
		for (ItemStack stack : needs()) {
			gui.blit(SLOT, x, railY, 7, 17, SLOT_SIZE, SLOT_SIZE);
			gui.renderItem(stack, x + 1, railY + 1);
			if (held.stream().anyMatch(h -> ItemStack.isSameItem(h, stack))) {
				gui.renderOutline(x - 1, railY - 1, SLOT_SIZE + 2, SLOT_SIZE + 2, INK);
				gui.renderOutline(x - 2, railY - 2, SLOT_SIZE + 4, SLOT_SIZE + 4, 0xFF000000);
			}
			x += SLOT_SIZE;
		}
	}

	private int railSlotAt(double mx, double my) {
		if (railX < 0 || my < railY || my >= railY + SLOT_SIZE || mx < railX) {
			return -1;
		}
		int i = (int) Math.floor((mx - railX) / SLOT_SIZE);
		return i < needs().size() ? i : -1;
	}

	private static boolean jei() {
		return Services.PLATFORM.isModLoaded("jei");
	}

	// ------------------------------------------------------------------ hover

	private void renderHover(GuiGraphics gui, int mouseX, int mouseY) {
		int step = stepAt(mouseX, mouseY);
		if (step >= 0) {
			int steps = player.scene().steps().size();
			gui.renderComponentTooltip(font, List.of(
				Component.translatable("watch.vineandbarrel.step", step + 1, steps).withStyle(s -> s.withColor(HIGHLIGHT)),
				player.caption(step)), mouseX, mouseY);
		}
		if (dragging && moved) {
			return;
		}
		int slot = railSlotAt(mouseX, mouseY);
		if (slot >= 0) {
			ItemStack stack = needs().get(slot);
			List<Component> lines = new ArrayList<>(Screen.getTooltipFromItem(minecraft, stack));
			if (jei()) {
				lines.add(Component.translatable("watch.vineandbarrel.click_recipes").withStyle(s -> s.withColor(INK_MUTED)));
			}
			gui.renderComponentTooltip(font, lines, mouseX, mouseY);
			return;
		}
		if (!isInStage(mouseX, mouseY) || player.isFinished() && isInCard(mouseX, mouseY) || isOverButton(mouseX, mouseY)) {
			return;
		}
		ItemStack inHand = heldUnder(mouseX, mouseY);
		if (!inHand.isEmpty()) {
			gui.renderTooltip(font, inHand, mouseX, mouseY);
			return;
		}
		BlockPos hovered = blockUnder(mouseX, mouseY);
		if (hovered != null) {
			BlockState state = player.world().getBlockState(hovered);
			List<Component> lines = new ArrayList<>();
			lines.add(state.getBlock().getName());
			if (sceneOf(state).isPresent()) {
				lines.add(Component.translatable("watch.vineandbarrel.click_watch").withStyle(s -> s.withColor(INK_MUTED)));
			}
			gui.renderComponentTooltip(font, lines, mouseX, mouseY);
		}
	}

	private boolean isOverButton(double x, double y) {
		return pauseButton != null && pauseButton.isMouseOver(x, y) || playButton != null && playButton.isMouseOver(x, y);
	}

	/** The scene a block on the stage has of its own (not this one), if any. */
	private Optional<ResourceLocation> sceneOf(BlockState state) {
		return WatchIndex.INSTANCE.forItem(new ItemStack(state.getBlock().asItem())).filter(id -> !id.equals(sceneId()));
	}

	/** What a figure holds, if the mouse is on its hands. */
	private ItemStack heldUnder(double mx, double my) {
		for (SceneFigure figure : player.figures()) {
			ItemStack stack = figure.entity().getMainHandItem();
			if (!stack.isEmpty()) {
				Vector3f hand = toGui(figure.entity().position().subtract(ScenePlayer.PARTICLE_ORIGIN).add(0.0, 1.0, 0.0));
				if (Math.abs(mx - hand.x) < 12 && Math.abs(my - hand.y) < 12) {
					return stack;
				}
			}
		}
		return ItemStack.EMPTY;
	}

	private @Nullable BlockPos blockUnder(double mouseX, double mouseY) {
		Matrix4f inverse = SceneView.viewMatrix(view.yaw, view.pitch, view.zoom).invert();
		float scale = sceneScale();
		float mx = (float) (mouseX - (sceneX() + sceneW() / 2.0F)) / scale;
		float my = (float) (mouseY - (sceneY() + sceneH() / 2.0F)) / scale;
		Vector3f near = inverse.transformPosition(mx, my, -50.0F, new Vector3f());
		Vector3f far = inverse.transformPosition(mx, my, 50.0F, new Vector3f());
		Vec3 from = centre().add(near.x, near.y, near.z);
		Vec3 to = centre().add(far.x, far.y, far.z);
		BlockPos best = null;
		double bestDistance = Double.MAX_VALUE;
		for (Map.Entry<BlockPos, BlockState> entry : player.world().blocks().entrySet()) {
			if (entry.getKey().getY() == 0 && player.scene().floor().isPresent()) {
				continue;
			}
			VoxelShape shape = entry.getValue().getShape(player.world(), entry.getKey(), CollisionContext.empty());
			BlockHitResult hit = shape.clip(from, to, entry.getKey());
			if (hit != null) {
				double distance = hit.getLocation().distanceToSqr(from);
				if (distance < bestDistance) {
					bestDistance = distance;
					best = entry.getKey();
				}
			}
		}
		return best;
	}

	private boolean isInStage(double x, double y) {
		return x >= stageX && x < stageX + stageW && y >= stageY && y < stageY + stageH;
	}

	private boolean isInCard(double x, double y) {
		return x >= cardX && x < cardX + cardW && y >= cardY && y < cardY + cardH;
	}

	// ------------------------------------------------------------------ sky and end box

	/**
	 * Days passing: the sky fills the stage and the sun or the moon crosses it, once each per day (the day count is the
	 * crossings, and the caption where it matters; the small marks that counted them read as stray squares).
	 */
	private void renderSky(GuiGraphics gui, float a) {
		Optional<ScenePlayer.Lapse> lapse = player.timelapse(a);
		if (lapse.isEmpty()) {
			return;
		}
		float day = lapse.get().dayFraction();
		float light = Mth.clamp(Mth.sin(day * Mth.TWO_PI) * 1.6F + 0.5F, 0.0F, 1.0F);
		gui.fill(stageX, stageY, stageX + stageW, stageY + stageH, (FastColor.ARGB32.lerp(light, NIGHT, DAY) & 0xFFFFFF) | 0xA0000000);
		boolean sunUp = day < 0.5F;
		float along = sunUp ? day * 2.0F : (day - 0.5F) * 2.0F;
		// the game's own sun and full moon (the player's resource pack's), at twice their pixels, added onto the sky the
		// way the sky draws them: their black adds nothing and the sun's glow fades out around it (the disc is 16 pixels)
		int size = CELESTIAL;
		int x = stageX + 8 + (int) (along * (stageW - 16 - size));
		// a low arc in the stage's upper part: it rises from under the top third and stays above the captions beside the stage's middle
		// (the texture's outer 18 of 64 pixels are black: at the top of the arc its glow begins 4 pixels into the stage)
		int top = stageY - 14, low = stageY + Math.round(stageH * 0.38F) - size / 2;
		int y = low - (int) (Mth.sin(along * Mth.PI) * (low - top));
		// this Minecraft version's GUI has no additive pipeline: the blend the sky draws them with (source alpha, one), set
		// by hand; the full moon is the first quarter of the phases' sheet
		gui.flush();
		RenderSystem.enableBlend();
		RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE, GlStateManager.SourceFactor.ONE,
			GlStateManager.DestFactor.ZERO);
		if (sunUp) {
			gui.blit(SUN, x, y, size, size, 0.0F, 0.0F, 32, 32, 32, 32);
		} else {
			gui.blit(MOON, x, y, size, size, 0.0F, 0.0F, 32, 32, 128, 64);
		}
		RenderSystem.defaultBlendFunc();
	}

	private void renderEndCard(GuiGraphics gui) {
		List<ResourceLocation> also = alsoScenes();
		if (also.isEmpty()) {
			gui.drawString(font, Component.translatable("watch.vineandbarrel.finished"), cardX + 8, cardY + 10, INK, true);
			return;
		}
		ResourceLocation next = also.get(0);
		gui.drawString(font, Component.translatable("watch.vineandbarrel.next_up"), cardX + 8, cardY + 6, INK_MUTED, false);
		gui.renderItem(WatchIndex.INSTANCE.icon(next), cardX + 8, cardY + 18);
		gui.drawString(font, titleOf(next).copy().withStyle(ChatFormatting.BOLD), cardX + 28, cardY + 18, INK, true);
		int steps = WatchIndex.INSTANCE.scene(next).map(scene -> scene.steps().size()).orElse(0);
		gui.drawString(font, Component.translatable("watch.vineandbarrel.steps", steps), cardX + 28, cardY + 29, INK_MUTED, false);
		if (also.size() > 1) {
			gui.drawString(font, Component.translatable("watch.vineandbarrel.also"), cardX + 8, cardY + 114, INK_MUTED, false);
			for (int i = 1; i < also.size(); i++) {
				int y = alsoY(i);
				gui.renderItem(WatchIndex.INSTANCE.icon(also.get(i)), cardX + 8, y);
				gui.drawString(font, titleOf(also.get(i)), cardX + 28, y + 4, INK, false);
			}
		}
	}

	private int alsoY(int i) {
		return cardY + 124 + (i - 1) * 18;
	}

	private static Component titleOf(ResourceLocation scene) {
		return WatchIndex.INSTANCE.scene(scene).map(s -> (Component) Component.translatable(s.title())).orElse(Component.empty());
	}

	/** {@return what the end offers: watching the path, its next station; else the scene's own offers that are here} */
	private List<ResourceLocation> alsoScenes() {
		if (chapters != null) {
			int at = chapters.indexOf(sceneId());
			return at >= 0 && at + 1 < chapters.size() ? List.of(chapters.get(at + 1)) : List.of();
		}
		return player.scene().also().stream()
			.filter(id -> WatchIndex.INSTANCE.scene(id).isPresent() && !WatchIndex.INSTANCE.icon(id).isEmpty())
			.toList();
	}

	private @Nullable ResourceLocation sceneId() {
		return WatchIndex.INSTANCE.idOf(player.scene());
	}

	private static int mulAlpha(int color, float alpha) {
		return ((int) (((color >>> 24) & 0xFF) * alpha) << 24) | (color & 0xFFFFFF);
	}

	// ------------------------------------------------------------------ input

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		// a double click: the same button again within a quarter of a second
		long clickAt = Util.getMillis();
		boolean doubleClick = button == lastClickButton && clickAt - lastClickAt < 250L;
		lastClickButton = button;
		lastClickAt = clickAt;
		if (super.mouseClicked(mouseX, mouseY, button)) {
			return true;
		}
		if (button != InputConstants.MOUSE_BUTTON_LEFT) {
			return false;
		}
		int step = stepAt(mouseX, mouseY);
		if (step >= 0) {
			player.jumpTo(step);
			updateButtons();
			return true;
		}
		int slot = railSlotAt(mouseX, mouseY);
		if (slot >= 0) {
			return jei() && jexxme.vineandbarrel.compat.jei.VBJeiPlugin.showRecipes(needs().get(slot));
		}
		if (player.isFinished() && isInCard(mouseX, mouseY)) {
			List<ResourceLocation> also = alsoScenes();
			for (int i = 1; i < also.size(); i++) {
				int y = alsoY(i);
				if (mouseX >= cardX + 8 && mouseX < cardX + cardW - 8 && mouseY >= y && mouseY < y + 16) {
					return open(also.get(i), 0, null, parent);
				}
			}
			return true;
		}
		if (isInStage(mouseX, mouseY)) {
			if (doubleClick) {
				view.reset();
				return true;
			}
			BlockPos hovered = blockUnder(mouseX, mouseY);
			if (hovered != null) {
				Optional<ResourceLocation> own = sceneOf(player.world().getBlockState(hovered));
				if (own.isPresent()) {
					return open(own.get(), 0, null, this);
				}
			}
			dragging = true;
			moved = false;
			pressX = mouseX;
			pressY = mouseY;
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
		if (dragging) {
			view.turn((float) dx * 1.2F, (float) dy * 0.8F);
			moved |= Math.abs(mouseX - pressX) + Math.abs(mouseY - pressY) > 3;
			return true;
		}
		return super.mouseDragged(mouseX, mouseY, button, dx, dy);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if (dragging && !moved && !player.isFinished()) {
			// a click on the scene, not a turn: pause or play
			togglePause();
		}
		dragging = false;
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollY) {
		if (isInStage(x, y) && scrollY != 0.0) {
			view.zoomBy(scrollY > 0.0 ? 1.1F : 1.0F / 1.1F);
			return true;
		}
		return super.mouseScrolled(x, y, scrollY);
	}

	@Override
	public boolean keyPressed(int key, int scanCode, int modifiers) {
		switch (key) {
			case InputConstants.KEY_LEFT -> back();
			case InputConstants.KEY_RIGHT -> next();
			case InputConstants.KEY_SPACE -> togglePause();
			case InputConstants.KEY_R -> replay();
			case InputConstants.KEY_HOME -> view.reset();
			default -> {
				return super.keyPressed(key, scanCode, modifiers);
			}
		}
		return true;
	}

	@Override
	public Component getNarrationMessage() {
		Component step = Component.translatable("watch.vineandbarrel.step", player.step() + 1, player.scene().steps().size());
		Component caption = player.caption();
		return now != null ? CommonComponents.joinForNarration(title, step, now, caption) : CommonComponents.joinForNarration(title, step, caption);
	}
}
