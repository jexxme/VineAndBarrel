package jexxme.vineandbarrel.watch.client;

import com.mojang.blaze3d.platform.InputConstants;
import jexxme.vineandbarrel.VineAndBarrel;
import jexxme.vineandbarrel.mixin.GuiGraphicsExtractorAccessor;
import jexxme.vineandbarrel.watch.WatchScene;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * "Watch it": a short scene of the real blocks, one step at a time. A wide vanilla panel with a dark window onto a
 * small stage, the step's one caption line under it and a scrub bar (one segment per step, click to jump). Drag to
 * turn, scroll to zoom, hover a block for its name; arrow keys step, space pauses, R plays it again.
 */
public class WatchScreen extends Screen
{
	private static final Identifier PANEL = VineAndBarrel.id("watch/panel");
	private static final Identifier WINDOW = VineAndBarrel.id("watch/window");
	private static final Identifier POINTER = VineAndBarrel.id("watch/pointer");
	private static final int INK = 0xFF404040;
	private static final int INK_MUTED = 0xFF606060;
	/** The window when no days pass: a dim cellar. */
	private static final int CELLAR = 0xFF1D2327;
	private static final int NIGHT = 0xFF141C30;
	private static final int DAY = 0xFF79A6DC;
	private static final int BAR = 0xFF8B8B8B;
	private static final int BAR_FILLED = 0xFF404040;
	private static final int PAD = 8;
	private static final int TITLE_H = 16;
	private static final int BAR_H = 3;
	private static final int LINE_H = 10;
	private static final int BUTTON = 12;

	private static final int NOW = 0xFFFFFF55;
	private static final int ICON = 16;

	private final ScenePlayer player;
	private final SceneView view;
	/** What the player's own block is doing, said while the scene is at the step it opened at (opened from a block). */
	private final @Nullable Component now;
	private final int nowStep;
	/** Where Escape goes back to: the inventory or JEI page the scene was opened from. */
	private final @Nullable Screen parent;
	private int panelX, panelY, panelW, panelH;
	private int winX, winY, winW, winH;
	/** How wide a caption may run (room is kept for "Also watch" on its right) and how many lines the longest needs. */
	private int captionW, captionLines;
	private boolean dragging;
	private @Nullable SpriteIconButton pause, play;

	public WatchScreen(ScenePlayer player, @Nullable Component now, @Nullable Screen parent) {
		super(Component.translatable(player.scene().title()));
		this.player = player;
		this.view = new SceneView(player.scene().view());
		this.now = now;
		this.nowStep = player.step();
		this.parent = parent;
	}

	/** Opens a scene from the start ({@code vineandbarrel:aging_barrel} is {@code assets/vineandbarrel/watch/aging_barrel.json}). */
	public static boolean open(Identifier id) {
		return open(id, 0, null, null);
	}

	/**
	 * Opens a scene at a step (0 = the first), maybe with a line about the player's own block, going back to
	 * {@code parent} when closed.
	 */
	public static boolean open(Identifier id, int step, @Nullable Component now, @Nullable Screen parent) {
		Minecraft minecraft = Minecraft.getInstance();
		Optional<WatchScene> scene = WatchIndex.INSTANCE.scene(id);
		if (minecraft.level == null || scene.isEmpty()) {
			return false;
		}
		ScenePlayer player = new ScenePlayer(scene.get(), minecraft.level);
		if (step > 0) {
			player.jumpTo(step);
		}
		minecraft.gui.setScreen(new WatchScreen(player, now, parent));
		return true;
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}

	/** {@return the scene playing here} */
	public ScenePlayer player() {
		return player;
	}

	@Override
	protected void init() {
		panelW = Mth.clamp(width * 2 / 3, Math.min(width - 16, 300), 520);
		panelH = Math.min(height - 16, Math.max(panelW * 3 / 4, 200));
		panelX = (width - panelW) / 2;
		panelY = (height - panelH) / 2;
		winX = panelX + PAD;
		winY = panelY + PAD + TITLE_H;
		winW = panelW - 2 * PAD;
		// the longest caption (in this language) sets the caption area's height, so nothing jumps between steps
		captionW = winW - (alsoScenes().isEmpty() ? 0 : alsoWidth() + 8);
		captionLines = 1;
		for (WatchScene.Step step : player.scene().steps()) {
			captionLines = Math.max(captionLines, font.split(Component.translatable(step.caption()), captionW).size());
		}
		winH = panelH - 2 * PAD - TITLE_H - BAR_H - captionArea();

		// back, pause or play, next, replay: the title row's right end
		int y = panelY + PAD - 3;
		int x = panelX + panelW - PAD - BUTTON;
		addRenderableWidget(control("replay", x, y, () -> {
			player.restart();
			view.reset();
		}));
		x -= BUTTON + 2;
		addRenderableWidget(control("next", x, y, () -> {
			if (player.step() + 1 < player.scene().steps().size()) {
				player.jumpTo(player.step() + 1);
			}
		}));
		x -= BUTTON + 2;
		pause = addRenderableWidget(control("pause", x, y, () -> player.setPaused(true)));
		play = addRenderableWidget(control("play", x, y, () -> {
			if (player.isFinished()) {
				player.restart();
			}
			player.setPaused(false);
		}));
		x -= BUTTON + 2;
		addRenderableWidget(control("back", x, y, this::back));
		updatePlayPause();
	}

	private SpriteIconButton control(String name, int x, int y, Runnable action) {
		SpriteIconButton button = SpriteIconButton.builder(Component.translatable("watch.vineandbarrel." + name), b -> {
			action.run();
			updatePlayPause();
		}, true).sprite(VineAndBarrel.id("watch/" + name), 8, 8).size(BUTTON, BUTTON).withTootip().build();
		button.setPosition(x, y);
		return button;
	}

	/** Pause while it plays, play once it is paused or over. */
	private void updatePlayPause() {
		if (pause != null && play != null) {
			boolean playing = !player.isPaused() && !player.isFinished();
			pause.visible = playing;
			play.visible = !playing;
		}
	}

	/** Back: to the start of this step, or to the step before if this one has only just begun. */
	private void back() {
		player.jumpTo(player.tick() > 10 || player.step() == 0 ? player.step() : player.step() - 1);
	}

	private int captionArea() {
		return 6 + captionLines * LINE_H;
	}

	@Override
	public void tick() {
		int step = player.step();
		player.clientTick();
		updatePlayPause();
		if (player.step() != step) {
			// the narrator reads each new step's caption, as it reads a screen's title
			minecraft.getNarrator().saySystemQueued(Component.translatable(player.currentStep().caption()));
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public boolean isInGameUi() {
		return true;
	}

	private float sceneScale() {
		return SceneView.scale(player.scene(), winW, winH);
	}

	private Vec3 centre() {
		return SceneView.centre(player.scene());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor gui, int mouseX, int mouseY, float a) {
		gui.blitSprite(RenderPipelines.GUI_TEXTURED, PANEL, panelX, panelY, panelW, panelH);
		WatchScene scene = player.scene();
		Component counter = Component.literal((player.step() + 1) + " / " + scene.steps().size());
		int controls = 4 * (BUTTON + 2) - 2;
		int counterX = panelX + panelW - PAD - controls - 6 - font.width(counter);
		gui.text(font, counter, counterX, panelY + PAD + 1, INK_MUTED, false);
		// a title too long for its room (a long translation) ends in an ellipsis rather than running into the counter
		int titleRoom = counterX - 8 - (panelX + PAD);
		FormattedCharSequence titleLine = font.width(title) <= titleRoom ? title.getVisualOrderText()
			: Language.getInstance().getVisualOrder(FormattedText.composite(font.substrByWidth(title, titleRoom - font.width("…")), FormattedText.of("…")));
		gui.text(font, titleLine, panelX + PAD, panelY + PAD + 1, INK, false);
		super.extractRenderState(gui, mouseX, mouseY, a);

		extractSky(gui, a);
		((GuiGraphicsExtractorAccessor) gui).vineandbarrel$guiRenderState().addPicturesInPictureState(
			new SceneRenderState(player, view, a, winX, winY, winX + winW, winY + winH, sceneScale(), null));
		gui.nextStratum();
		gui.blitSprite(RenderPipelines.GUI_TEXTURED, WINDOW, winX - 2, winY - 2, winW + 4, winH + 4);
		extractPointer(gui, a);
		extractScrubBar(gui, a);
		Component caption = Component.translatable(player.currentStep().caption());
		int lineY = winY + winH + BAR_H + 6;
		for (FormattedCharSequence line : font.split(caption, captionW)) {
			gui.text(font, line, panelX + PAD, lineY, INK, false);
			lineY += LINE_H;
		}
		if (now != null && player.step() == nowStep) {
			List<FormattedCharSequence> lines = font.split(now, winW - 24);
			int w = lines.stream().mapToInt(font::width).max().orElse(0) + 8;
			int x = winX + (winW - w) / 2;
			gui.fill(x, winY + 4, x + w, winY + 7 + lines.size() * LINE_H + 2, 0xB0000000);
			int y = winY + 7;
			for (FormattedCharSequence line : lines) {
				gui.centeredText(font, line, winX + winW / 2, y, NOW);
				y += LINE_H;
			}
		}
		extractAlso(gui, mouseX, mouseY);

		if (dragging) {
			gui.requestCursor(CursorTypes.RESIZE_ALL);
		} else if (isInWindow(mouseX, mouseY)) {
			BlockPos hovered = blockUnder(mouseX, mouseY);
			if (hovered != null) {
				gui.setTooltipForNextFrame(font, player.world().getBlockState(hovered).getBlock().getName(), mouseX, mouseY);
			}
		} else if (segmentAt(mouseX, mouseY) >= 0) {
			gui.requestCursor(CursorTypes.POINTING_HAND);
		}
	}

	/** The scrub bar's segment under the mouse, or -1. */
	private int segmentAt(double x, double y) {
		int barY = winY + winH + 3;
		if (y < barY - 3 || y >= barY + BAR_H + 3 || x < winX || x >= winX + winW) {
			return -1;
		}
		int steps = player.scene().steps().size();
		for (int i = 0; i < steps; i++) {
			if (x < segmentX(i + 1)) {
				return i;
			}
		}
		return steps - 1;
	}

	private int alsoWidth() {
		return font.width(Component.translatable("watch.vineandbarrel.also")) + 4 + alsoScenes().size() * (ICON + 2);
	}

	/** At the end: "Also watch" and the other scenes' icons, on the caption line's right. */
	private void extractAlso(GuiGraphicsExtractor gui, int mouseX, int mouseY) {
		List<Identifier> also = alsoScenes();
		if (!player.isFinished() || also.isEmpty()) {
			return;
		}
		int y = winY + winH + BAR_H + 2;
		int x = panelX + panelW - PAD - also.size() * (ICON + 2);
		Component label = Component.translatable("watch.vineandbarrel.also");
		gui.text(font, label, x - 4 - font.width(label), y + 4, INK_MUTED, false);
		for (Identifier id : also) {
			gui.item(WatchIndex.INSTANCE.icon(id), x, y);
			if (mouseX >= x && mouseX < x + ICON && mouseY >= y && mouseY < y + ICON) {
				gui.fill(x, y, x + ICON, y + ICON, 0x80FFFFFF);
				gui.requestCursor(CursorTypes.POINTING_HAND);
				WatchIndex.INSTANCE.scene(id).ifPresent(scene ->
					gui.setTooltipForNextFrame(font, Component.translatable(scene.title()), mouseX, mouseY));
			}
			x += ICON + 2;
		}
	}

	/** The scenes offered at the end that exist (a resource pack may have left one out) and have an icon. */
	private List<Identifier> alsoScenes() {
		return player.scene().also().stream()
			.filter(id -> WatchIndex.INSTANCE.scene(id).isPresent() && !WatchIndex.INSTANCE.icon(id).isEmpty())
			.toList();
	}

	/** The window's sky: a dim cellar, or while days pass, the days and nights going by with the sun and the moon. */
	private void extractSky(GuiGraphicsExtractor gui, float a) {
		Optional<ScenePlayer.Lapse> lapse = player.timelapse(a);
		if (lapse.isEmpty()) {
			gui.fill(winX, winY, winX + winW, winY + winH, CELLAR);
			return;
		}
		float day = lapse.get().dayFraction();
		// sunrise at 0, noon at 0.25, sunset at 0.5, midnight at 0.75
		float light = Mth.clamp(Mth.sin(day * Mth.TWO_PI) * 1.6F + 0.5F, 0.0F, 1.0F);
		gui.fill(winX, winY, winX + winW, winY + winH, ARGB.srgbLerp(light, NIGHT, DAY));
		boolean sunUp = day < 0.5F;
		float along = sunUp ? day * 2.0F : (day - 0.5F) * 2.0F;
		int size = 12;
		int x = winX + 8 + (int) (along * (winW - 16 - size));
		int y = winY + winH / 2 - (int) (Mth.sin(along * Mth.PI) * (winH / 2.0F - 12)) - size / 2;
		// a square sun and moon, the way the sky draws them
		if (sunUp) {
			gui.fill(x, y, x + size, y + size, 0xFFFFE36A);
			gui.fill(x + 2, y + 2, x + size - 2, y + size - 2, 0xFFFFF6C4);
		} else {
			gui.fill(x, y, x + size, y + size, 0xFFDCE3EC);
			gui.fill(x + 3, y + 2, x + 6, y + 5, 0xFFB4BCC8);
			gui.fill(x + 6, y + 7, x + 9, y + 10, 0xFFB4BCC8);
		}
		// one small mark per day that has passed: what the window shows, never how many are left
		int passed = (int) (lapse.get().days() * lapse.get().progress());
		for (int d = 0; d < Mth.ceil(lapse.get().days()); d++) {
			int dx = winX + 6 + d * 8;
			gui.fill(dx, winY + 6, dx + 4, winY + 10, d < passed ? 0xFFFFE36A : 0x40FFFFFF);
		}
	}

	private void extractPointer(GuiGraphicsExtractor gui, float a) {
		Optional<Vec3> target = player.pointer();
		if (target.isEmpty()) {
			return;
		}
		Vector3f at = toGui(target.get());
		float bob = Mth.sin((player.tick() + a) * 0.25F) * 2.0F;
		int x = Math.round(at.x) - 4;
		int y = Math.round(at.y + bob) - 12;
		if (x >= winX && x + 9 <= winX + winW && y >= winY && y + 7 <= winY + winH) {
			gui.blitSprite(RenderPipelines.GUI_TEXTURED, POINTER, x, y, 9, 7);
		}
	}

	private void extractScrubBar(GuiGraphicsExtractor gui, float a) {
		int steps = player.scene().steps().size();
		int y = winY + winH + 3;
		for (int i = 0; i < steps; i++) {
			int x0 = segmentX(i);
			int x1 = segmentX(i + 1) - 2;
			gui.fill(x0, y, x1, y + BAR_H, BAR);
			float filled = i < player.step() ? 1.0F : i == player.step() ? player.stepProgress(a) : 0.0F;
			if (filled > 0.0F) {
				gui.fill(x0, y, x0 + Math.round((x1 - x0) * filled), y + BAR_H, BAR_FILLED);
			}
		}
	}

	private int segmentX(int i) {
		return winX + Math.round((float) i * (winW + 2) / player.scene().steps().size());
	}

	// ------------------------------------------------------------------ the view's math (the same as the renderer's)

	private Matrix4f viewMatrix() {
		return SceneView.viewMatrix(view.yaw, view.pitch, view.zoom);
	}

	/** Where a stage point is on the screen. */
	private Vector3f toGui(Vec3 stagePoint) {
		Vector3f v = SceneView.project(viewMatrix(), stagePoint.subtract(centre()), sceneScale());
		return v.add(winX + winW / 2.0F, winY + winH / 2.0F, 0.0F);
	}

	/** The block under the mouse: a ray from the screen into the stage, the nearest block shape it hits. */
	private @Nullable BlockPos blockUnder(double mouseX, double mouseY) {
		Matrix4f inverse = viewMatrix().invert();
		float scale = sceneScale();
		float mx = (float) (mouseX - (winX + winW / 2.0F)) / scale;
		float my = (float) (mouseY - (winY + winH / 2.0F)) / scale;
		Vector3f near = inverse.transformPosition(mx, my, -50.0F, new Vector3f());
		Vector3f far = inverse.transformPosition(mx, my, 50.0F, new Vector3f());
		Vec3 from = centre().add(near.x, near.y, near.z);
		Vec3 to = centre().add(far.x, far.y, far.z);
		BlockPos best = null;
		double bestDistance = Double.MAX_VALUE;
		for (Map.Entry<BlockPos, BlockState> entry : player.world().blocks().entrySet()) {
			if (entry.getKey().getY() == 0 && player.scene().floor().isPresent()) {
				// the floor is the stage, not something to name
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

	private boolean isInWindow(double x, double y) {
		return x >= winX && x < winX + winW && y >= winY && y < winY + winH;
	}

	// ------------------------------------------------------------------ input

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && player.isFinished()) {
			List<Identifier> also = alsoScenes();
			int y = winY + winH + BAR_H + 2;
			int x = panelX + panelW - PAD - also.size() * (ICON + 2);
			for (Identifier id : also) {
				if (event.x() >= x && event.x() < x + ICON && event.y() >= y && event.y() < y + ICON) {
					return open(id, 0, null, parent);
				}
				x += ICON + 2;
			}
		}
		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
			int segment = segmentAt(event.x(), event.y());
			if (segment >= 0) {
				player.jumpTo(segment);
				updatePlayPause();
				return true;
			}
			if (isInWindow(event.x(), event.y())) {
				dragging = true;
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (dragging) {
			view.turn((float) dx * 1.2F, (float) dy * 0.8F);
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		dragging = false;
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		if (isInWindow(x, y) && scrollY != 0.0) {
			view.zoomBy(scrollY > 0.0 ? 1.1F : 1.0F / 1.1F);
			return true;
		}
		return super.mouseScrolled(x, y, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		switch (event.key()) {
			case InputConstants.KEY_LEFT -> {
				back();
				return true;
			}
			case InputConstants.KEY_RIGHT -> {
				if (player.step() + 1 < player.scene().steps().size()) {
					player.jumpTo(player.step() + 1);
				}
				return true;
			}
			case InputConstants.KEY_SPACE -> {
				if (player.isFinished()) {
					player.restart();
				}
				player.setPaused(!player.isPaused() && !player.isFinished());
				updatePlayPause();
				return true;
			}
			case InputConstants.KEY_R -> {
				player.restart();
				view.reset();
				return true;
			}
			default -> {
				return super.keyPressed(event);
			}
		}
	}

	@Override
	public Component getNarrationMessage() {
		Component caption = Component.translatable(player.currentStep().caption());
		return now != null ? CommonComponents.joinForNarration(title, now, caption) : CommonComponents.joinForNarration(title, caption);
	}
}
