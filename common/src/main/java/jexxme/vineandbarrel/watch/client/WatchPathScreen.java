package jexxme.vineandbarrel.watch.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import jexxme.vineandbarrel.VineAndBarrel;
import jexxme.vineandbarrel.mixin.GuiGraphicsExtractorAccessor;
import jexxme.vineandbarrel.platform.Services;
import jexxme.vineandbarrel.watch.WatchPath;
import jexxme.vineandbarrel.watch.WatchScene;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ClientAdvancements;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The Brewer's path: the whole journey from vine to glass as a row of stations, each a "Watch it" scene, ticked from
 * the player's own advancements (done, next for you, not yet). Side scenes hang under the station they belong to (with
 * Farmer's Delight: its stove and ropes). Pick a station for its card (its finished stage, what it is, how far you
 * are), then watch it: it plays on into the stations after it like chapters. The stations and branches are data
 * ({@code watch/path.json}).
 */
public class WatchPathScreen extends Screen
{
	private static final int INK = 0xFFFFFFFF;
	private static final int INK_MUTED = 0xFFA0A0A0;
	private static final int DONE = 0xFFC6C6C6;
	private static final int HIGHLIGHT = 0xFFFFFF55;
	private static final int TITLE_INK = 0xFF404040;
	private static final int STAGE = 0xFF1D2327;
	private static final int TRACK = 0xFF555555;
	private static final Identifier PANEL = VineAndBarrel.id("watch/panel");
	private static final Identifier INSET = VineAndBarrel.id("watch/window");
	private static final int BOX = 40, BOX_COMPACT = 28, BRANCH_BOX = 24;
	private static final int DONE_W = 64;

	/** One place on the screen: a station of the main line, or a branch under one. */
	private record Spot(Identifier scene, Component name, Optional<Identifier> advancement, Optional<String> note, boolean branch) {
	}

	private final @Nullable Screen parent;
	private final List<Spot> stations = new ArrayList<>();
	private final List<Spot> branches = new ArrayList<>();
	/** For each branch, the station it hangs under. */
	private final List<Integer> branchAt = new ArrayList<>();
	private int ox, oy, ow, oh, stageY, stageH, footerY;
	/** A short window (GUI scale 3 or 4): smaller stations without their scene's name under them. */
	private boolean compact;
	/** The card has room for the chosen station's preview on its left. */
	private boolean cardPreview;
	private int box = BOX;
	private int pathY;
	private int selected = -1;
	private @Nullable ScenePlayer preview;
	private @Nullable SceneView previewView;
	private int previewFor = -2;
	private int cardX, cardY, cardW, cardH;

	public WatchPathScreen(@Nullable Screen parent) {
		super(Component.translatable("watch.vineandbarrel.path"));
		this.parent = parent;
		WatchPath path = WatchIndex.INSTANCE.path();
		for (WatchPath.Station station : path.stations()) {
			if (WatchIndex.INSTANCE.scene(station.scene()).isPresent()) {
				stations.add(new Spot(station.scene(), Component.translatable(station.verb()), station.advancement(), Optional.empty(), false));
			}
		}
		for (WatchPath.Branch branch : path.branches()) {
			int at = indexOf(branch.after());
			if (at >= 0 && WatchIndex.INSTANCE.scene(branch.scene()).isPresent() && branch.requires().map(Services.PLATFORM::isModLoaded).orElse(true)) {
				branches.add(new Spot(branch.scene(), titleOf(branch.scene()), Optional.empty(), branch.note(), true));
				branchAt.add(at);
			}
		}
	}

	/** {@return whether the path has a station to show (its scenes may be missing, say in a trimmed resource pack)} */
	static boolean available() {
		return WatchIndex.INSTANCE.path().stations().stream().anyMatch(station -> WatchIndex.INSTANCE.scene(station.scene()).isPresent());
	}

	private int indexOf(Identifier scene) {
		for (int i = 0; i < stations.size(); i++) {
			if (stations.get(i).scene().equals(scene)) {
				return i;
			}
		}
		return -1;
	}

	private static Component titleOf(Identifier scene) {
		return WatchIndex.INSTANCE.scene(scene).map(s -> (Component) Component.translatable(s.title())).orElse(Component.empty());
	}

	@Nullable Screen parent() {
		return parent;
	}

	private int spots() {
		return stations.size() + branches.size();
	}

	private Spot spot(int index) {
		return index < stations.size() ? stations.get(index) : branches.get(index - stations.size());
	}

	private @Nullable AdvancementHolder advancement(Spot spot) {
		return spot.advancement().isEmpty() || minecraft.getConnection() == null ? null
			: minecraft.getConnection().getAdvancements().get(spot.advancement().get());
	}

	/** Done: the player has the station's advancement (the client knows its own progress). */
	private boolean done(Spot spot) {
		AdvancementHolder holder = advancement(spot);
		if (holder == null) {
			return false;
		}
		ClientAdvancements advancements = minecraft.getConnection().getAdvancements();
		AdvancementProgress progress = advancements.progress().get(holder);
		return progress != null && progress.isDone();
	}

	/** The first station with an advancement not done yet: where you are, next for you (-1 once all are done). */
	private int nextForYou() {
		for (int i = 0; i < stations.size(); i++) {
			if (stations.get(i).advancement().isPresent() && !done(stations.get(i))) {
				return i;
			}
		}
		return -1;
	}

	// ------------------------------------------------------------------ layout

	@Override
	protected void init() {
		int[] size = WatchScreen.windowSize(width, height);
		ow = size[0];
		oh = size[1];
		ox = (width - ow) / 2;
		oy = (height - oh) / 2;
		stageY = oy + 20;
		footerY = oy + oh - 36;
		stageH = footerY - 4 - stageY;
		// the full layout needs room for the stations' names and the card's text above its two buttons
		compact = stageH < (branches.isEmpty() ? 255 : 290);
		box = compact ? BOX_COMPACT : BOX;
		pathY = stageY + (compact ? 18 : 40);
		if (selected < 0) {
			selected = Math.max(0, nextForYou());
		}

		// the chosen station's card under the path (and its side paths), as tall as the room allows; with room, its
		// preview on the left and the text and buttons on the right, else the text on the left and the buttons on the right
		cardW = Math.min(ow - 48, 520);
		cardY = branches.isEmpty() ? pathY + box + (compact ? 22 : 40) : branchY(branchRows() - 1) + BRANCH_BOX + (compact ? 6 : 16);
		cardH = Math.min(stageY + stageH - 12 - cardY, 220);
		cardX = ox + (ow - cardW) / 2;
		cardPreview = cardH >= 94;
		int colX = cardX + cardW / 2 + 12, colW = cardX + cardW - 10 - colX;
		// one button: a station plays on into the next one from its end card (like chapters), a side scene plays alone
		int buttonsY = cardPreview ? cardY + cardH - 28 : cardY + 8;
		addRenderableWidget(Button.builder(Component.translatable("watch.vineandbarrel.path.watch"), b -> watch(selected))
			.bounds(colX, buttonsY, colW, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("watch.vineandbarrel.path.watch_all"), b -> watch(0))
			.bounds(ox + 8, footerY + 12, 100, 20).build());
		Button done = addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> minecraft.gui.setScreen(WatchScreen.outside(parent)))
			.bounds(ox + ow - 8 - DONE_W, footerY + 12, DONE_W, 20).build());
		setInitialFocus(done);
	}

	/** Watches a spot: a station and the ones after it like chapters (each end card leads on), a side scene alone. */
	private void watch(int from) {
		if (from < 0 || from >= spots()) {
			return;
		}
		if (from >= stations.size()) {
			WatchScreen.open(spot(from).scene(), 0, null, this);
		} else {
			WatchScreen.openChapters(stations.get(from).scene(), stations.stream().map(Spot::scene).toList(), this);
		}
	}

	private void select(int index) {
		if (index != selected && index >= 0 && index < spots()) {
			selected = index;
			rebuildWidgets();
			minecraft.getNarrator().saySystemQueued(CommonComponents.joinForNarration(spot(index).name(), status(index)));
		}
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return true;
	}

	@Override
	public boolean isInGameUi() {
		return true;
	}

	// ------------------------------------------------------------------ drawing

	@Override
	public void extractBackground(GuiGraphicsExtractor gui, int mouseX, int mouseY, float a) {
		extractBlurredBackground(gui);
		extractMenuBackground(gui);
		gui.fill(0, 0, width, height, 0x60000000);
		gui.blitSprite(RenderPipelines.GUI_TEXTURED, PANEL, ox, oy, ow, oh);
		gui.fill(ox + 8, stageY, ox + ow - 8, stageY + stageH, STAGE);
	}

	private int stationX(int i) {
		int margin = 72;
		return ox + margin + Math.round((float) i * (ow - 2 * margin) / Math.max(1, stations.size() - 1));
	}

	/** A branch's spot: under the road between its station and the next (under the station itself if it is the last). */
	private int branchX(int b) {
		int at = branchAt.get(b);
		return at + 1 < stations.size() ? (stationX(at) + stationX(at + 1)) / 2 : stationX(at);
	}

	/** Branches under the same station stack: each in its own row. */
	private int branchRow(int b) {
		int row = 0;
		for (int i = 0; i < b; i++) {
			row += branchAt.get(i).equals(branchAt.get(b)) ? 1 : 0;
		}
		return row;
	}

	private int branchRows() {
		int rows = 0;
		for (int b = 0; b < branches.size(); b++) {
			rows = Math.max(rows, branchRow(b) + 1);
		}
		return rows;
	}

	private int branchY(int row) {
		return pathY + box + (compact ? 20 : 34) + row * (BRANCH_BOX + 6);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor gui, int mouseX, int mouseY, float a) {
		int next = nextForYou();
		int doneCount = (int) stations.stream().filter(this::done).count();
		int ticked = (int) stations.stream().filter(s -> s.advancement().isPresent()).count();
		gui.text(font, title, ox + 8, oy + 6, TITLE_INK, false);
		gui.blitSprite(RenderPipelines.GUI_TEXTURED, INSET, ox + 6, stageY - 2, ow - 12, stageH + 4);
		Component progress = next < 0 ? Component.translatable("watch.vineandbarrel.path.all_done")
			: Component.translatable("watch.vineandbarrel.path.progress", doneCount, ticked);
		gui.text(font, progress, ox + ow - 8 - DONE_W - 10 - font.width(progress), footerY + 18, TITLE_INK, false);

		// the road between the stations: walked (light) up to where you are, the rest still dark
		int y = pathY + box / 2;
		for (int i = 0; i + 1 < stations.size(); i++) {
			boolean walked = done(stations.get(i)) && (done(stations.get(i + 1)) || next == i + 1);
			gui.fill(stationX(i) + box / 2, y - 1, stationX(i + 1) - box / 2, y + 2, walked ? DONE : TRACK);
		}
		for (int b = 0; b < branches.size(); b++) {
			extractBranch(gui, b, y, mouseX, mouseY);
		}
		for (int i = 0; i < stations.size(); i++) {
			Spot station = stations.get(i);
			int x = stationX(i) - box / 2;
			extractStation(gui, i, station, x, pathY, box, done(station), i == next, mouseX, mouseY);
			gui.centeredText(font, station.name().copy().withStyle(ChatFormatting.BOLD), x + box / 2, pathY + box + (compact ? 4 : 6), i == next ? HIGHLIGHT : INK);
			if (!compact) {
				gui.centeredText(font, titleOf(station.scene()), x + box / 2, pathY + box + 17, INK_MUTED);
			}
			if (i == next) {
				gui.centeredText(font, Component.translatable("watch.vineandbarrel.path.next_for_you"), x + box / 2, pathY - (compact ? 12 : 14), HIGHLIGHT);
			}
		}

		// the chosen station, large: its scene's finished stage, what it is, whether you've done it
		gui.fill(cardX, cardY, cardX + cardW, cardY + cardH, 0xC0000000);
		int px0 = cardX + 8, py0 = cardY + 8, px1 = cardX + cardW / 2, py1 = cardY + cardH - 8;
		ScenePlayer shown = cardPreview ? preview(selected) : null;
		if (shown != null && previewView != null) {
			gui.fill(px0, py0, px1, py1, 0xFF161B1E);
			((GuiGraphicsExtractorAccessor) gui).vineandbarrel$guiRenderState().addPicturesInPictureState(
				new SceneRenderState(shown, previewView, 0.0F, px0, py0, px1, py1, SceneView.scale(shown.scene(), px1 - px0, py1 - py0) * 0.9F, null, null));
		}
		gui.nextStratum();
		Spot spot = spot(selected);
		int colX = cardPreview ? cardX + cardW / 2 + 12 : cardX + 10;
		gui.text(font, spot.name().copy().withStyle(ChatFormatting.BOLD), colX, cardY + (compact ? 9 : 12), INK, true);
		Optional<WatchScene> scene = WatchIndex.INSTANCE.scene(spot.scene());
		Component steps = Component.translatable("watch.vineandbarrel.steps", scene.map(s -> s.steps().size()).orElse(0));
		if (spot.branch()) {
			gui.text(font, steps, colX, cardY + (compact ? 21 : 24), INK_MUTED, false);
		} else if (compact) {
			gui.text(font, Component.empty().append(titleOf(spot.scene())).append(" · ").append(steps), colX, cardY + 21, INK_MUTED, false);
		} else {
			gui.text(font, titleOf(spot.scene()), colX, cardY + 24, INK_MUTED, false);
			gui.text(font, steps, colX, cardY + 35, INK_MUTED, false);
		}
		gui.text(font, status(selected), colX, cardY + (compact ? 35 : 54), statusColor(selected), true);
		super.extractRenderState(gui, mouseX, mouseY, a);
		if (spotAt(mouseX, mouseY) >= 0) {
			gui.requestCursor(CursorTypes.POINTING_HAND);
		}
	}

	private void extractBranch(GuiGraphicsExtractor gui, int b, int roadY, int mouseX, int mouseY) {
		Spot branch = branches.get(b);
		int bx = branchX(b);
		int by = branchY(branchRow(b));
		gui.fill(bx, roadY + 2, bx + 2, by, TRACK);
		extractStation(gui, stations.size() + b, branch, bx - BRANCH_BOX / 2 + 1, by, BRANCH_BOX, false, false, mouseX, mouseY);
		// its name beside it, and its note under the name only where it fits inside the stage (never past its edge)
		Component name = branch.name().copy().withStyle(ChatFormatting.BOLD);
		int room = ox + ow - 10 - (bx + 20);
		int labelX = font.width(name) <= room ? bx + 20 : bx - 16 - font.width(name);
		gui.text(font, name, labelX, by + 3, INK, true);
		branch.note().map(Component::translatable).ifPresent(note -> {
			boolean right = labelX > bx;
			if (font.width(note) <= (right ? room : bx - 16 - (ox + 10))) {
				gui.text(font, note, right ? labelX : bx - 16 - font.width(note), by + 14, INK_MUTED, false);
			}
		});
	}

	private Component status(int index) {
		Spot spot = spot(index);
		if (spot.advancement().isEmpty()) {
			return Component.translatable("watch.vineandbarrel.path.optional");
		}
		if (done(spot)) {
			// a root advancement is the mod's own "you've started": just "Done"
			AdvancementHolder holder = advancement(spot);
			Optional<Component> name = holder == null || holder.value().parent().isEmpty() ? Optional.empty()
				: holder.value().display().map(DisplayInfo::title);
			return name.map(n -> (Component) Component.translatable("watch.vineandbarrel.path.done_with", n))
				.orElseGet(() -> Component.translatable("watch.vineandbarrel.path.done"));
		}
		return index == nextForYou() ? Component.translatable("watch.vineandbarrel.path.next_for_you") : Component.translatable("watch.vineandbarrel.path.not_yet");
	}

	private int statusColor(int index) {
		Spot spot = spot(index);
		return spot.advancement().isEmpty() ? INK_MUTED : done(spot) ? DONE : index == nextForYou() ? HIGHLIGHT : INK_MUTED;
	}

	/** One station: its scene's icon in a big slot; done = ticked, next for you = framed in yellow, not yet = dim. */
	private void extractStation(GuiGraphicsExtractor gui, int index, Spot spot, int x, int y, int size, boolean done, boolean next,
								int mouseX, int mouseY) {
		boolean hover = mouseX >= x && mouseX < x + size && mouseY >= y && mouseY < y + size;
		// a big inventory slot: grey with the slot's own bevel
		gui.fill(x, y, x + size, y + size, 0xFF8B8B8B);
		gui.fill(x, y, x + size, y + 2, 0xFF373737);
		gui.fill(x, y, x + 2, y + size, 0xFF373737);
		gui.fill(x, y + size - 2, x + size, y + size, 0xFFFFFFFF);
		gui.fill(x + size - 2, y, x + size, y + size, 0xFFFFFFFF);
		if (next) {
			gui.outline(x - 2, y - 2, size + 4, size + 4, HIGHLIGHT);
			gui.outline(x - 3, y - 3, size + 6, size + 6, 0xFF000000);
		} else if (index == selected || hover) {
			gui.outline(x - 2, y - 2, size + 4, size + 4, 0xFFFFFFFF);
		}
		int scale = size >= 32 ? 2 : 1;
		gui.pose().pushMatrix();
		gui.pose().translate(x + (size - 16 * scale) / 2.0F, y + (size - 16 * scale) / 2.0F);
		gui.pose().scale(scale, scale);
		gui.item(WatchIndex.INSTANCE.icon(spot.scene()), 0, 0);
		gui.pose().popMatrix();
		if (!done && !next && spot.advancement().isPresent()) {
			// not reached yet: dimmed, and without a tick (the tick and the frame say it, not the dimming alone)
			gui.fill(x + 2, y + 2, x + size - 2, y + size - 2, 0x80373737);
		}
		if (done) {
			int tx = x + size - 9, ty = y - 4;
			gui.fill(tx, ty, tx + 12, ty + 12, 0xFF3B3B3B);
			gui.outline(tx, ty, 12, 12, 0xFFFFFFFF);
			int[][] tick = {{2, 6}, {3, 7}, {4, 8}, {5, 7}, {6, 6}, {7, 5}, {8, 4}, {9, 3}};
			for (int[] p : tick) {
				gui.fill(tx + p[0], ty + p[1], tx + p[0] + 1, ty + p[1] + 2, INK);
			}
		}
	}

	private int spotAt(double mx, double my) {
		for (int i = 0; i < stations.size(); i++) {
			int x = stationX(i) - box / 2;
			if (mx >= x && mx < x + box && my >= pathY && my < pathY + box + (compact ? 14 : 28)) {
				return i;
			}
		}
		for (int b = 0; b < branches.size(); b++) {
			int x = branchX(b) - BRANCH_BOX / 2 + 1, y = branchY(branchRow(b));
			if (mx >= x && mx < x + BRANCH_BOX && my >= y && my < y + BRANCH_BOX) {
				return stations.size() + b;
			}
		}
		return -1;
	}

	/** The chosen station's scene as it ends, still: what you'll have made. */
	private @Nullable ScenePlayer preview(int index) {
		if (minecraft.level == null) {
			return null;
		}
		if (previewFor != index) {
			Optional<WatchScene> scene = WatchIndex.INSTANCE.scene(spot(index).scene());
			preview = null;
			if (scene.isPresent()) {
				preview = new ScenePlayer(scene.get(), minecraft.level);
				preview.jumpTo(scene.get().steps().size() - 1);
				preview.setPaused(true);
				previewView = new SceneView(scene.get().view());
			}
			previewFor = index;
		}
		return preview;
	}

	// ------------------------------------------------------------------ input

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		int spot = spotAt(event.x(), event.y());
		if (spot >= 0) {
			select(spot);
			if (doubleClick) {
				watch(spot);
			}
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		switch (event.key()) {
			case InputConstants.KEY_LEFT -> select(selected - 1);
			case InputConstants.KEY_RIGHT -> select(selected + 1);
			default -> {
				return super.keyPressed(event);
			}
		}
		return true;
	}

	@Override
	public Component getNarrationMessage() {
		return CommonComponents.joinForNarration(title, spot(selected).name(), status(selected));
	}
}
