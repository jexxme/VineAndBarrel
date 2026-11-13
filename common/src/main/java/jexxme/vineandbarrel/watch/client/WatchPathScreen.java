package jexxme.vineandbarrel.watch.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import jexxme.vineandbarrel.mixin.ClientAdvancementsAccessor;
import jexxme.vineandbarrel.platform.Services;
import jexxme.vineandbarrel.watch.WatchPath;
import jexxme.vineandbarrel.watch.WatchScene;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientAdvancements;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The Brewer's path: the whole journey from vine to glass as a row of stations, each a "Watch it" scene, ticked from
 * the player's own advancements (done, next for you, not yet). Side scenes hang under the station they belong to (with
 * Farmer's Delight: its stove and ropes). Pick a station for its card (its finished stage, what it is, how far you
 * are), then watch it: it plays on into the stations after it like chapters. The stations and branches are data
 * ({@code watch/path.json}). The wiki is a click away, in the player's language: its home page from the footer's Wiki
 * button, the chosen station's own page from a quiet link on its card.
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
	private static final int BOX = 40, BOX_COMPACT = 28, BRANCH_BOX = 24;
	private static final int DONE_W = 64;
	/** The wiki (the mod's home page in neoforge.mods.toml and fabric.mod.json): English at its root, the rest under /<code>/. */
	private static final String WIKI = "https://jexxme.github.io/VineAndBarrel/";
	/** The game's languages the wiki is translated into, and their place on it (site/src/i18n/locales.mjs); others get English. */
	private static final Map<String, String> WIKI_LANGUAGES = Map.ofEntries(
		Map.entry("de_de", "de"), Map.entry("es_es", "es"), Map.entry("fr_fr", "fr"), Map.entry("it_it", "it"),
		Map.entry("pl_pl", "pl"), Map.entry("pt_br", "pt-br"), Map.entry("tr_tr", "tr"), Map.entry("ru_ru", "ru"),
		Map.entry("uk_ua", "uk"), Map.entry("zh_cn", "zh-cn"), Map.entry("ja_jp", "ja"), Map.entry("ko_kr", "ko"));

	/** One place on the screen: a station of the main line, or a branch under one. */
	private record Spot(ResourceLocation scene, Component name, Optional<ResourceLocation> advancement, Optional<String> note, boolean branch, Optional<String> wiki) {
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
	/** The last click, to tell a double click (this Minecraft version doesn't). */
	private long lastClickAt;
	private int lastClickButton = -1;
	/** The card has its wiki line (the spot has a page, and it fits). */
	private boolean cardWiki;

	public WatchPathScreen(@Nullable Screen parent) {
		super(Component.translatable("watch.vineandbarrel.path"));
		this.parent = parent;
		WatchPath path = WatchIndex.INSTANCE.path();
		for (WatchPath.Station station : path.stations()) {
			if (WatchIndex.INSTANCE.scene(station.scene()).isPresent()) {
				stations.add(new Spot(station.scene(), Component.translatable(station.verb()), station.advancement(), Optional.empty(), false, station.wiki()));
			}
		}
		for (WatchPath.Branch branch : path.branches()) {
			int at = indexOf(branch.after());
			if (at >= 0 && WatchIndex.INSTANCE.scene(branch.scene()).isPresent() && branch.requires().map(Services.PLATFORM::isModLoaded).orElse(true)) {
				branches.add(new Spot(branch.scene(), titleOf(branch.scene()), Optional.empty(), branch.note(), true, branch.wiki()));
				branchAt.add(at);
			}
		}
	}

	/** {@return whether the path has a station to show (its scenes may be missing, say in a trimmed resource pack)} */
	static boolean available() {
		return WatchIndex.INSTANCE.path().stations().stream().anyMatch(station -> WatchIndex.INSTANCE.scene(station.scene()).isPresent());
	}

	private int indexOf(ResourceLocation scene) {
		for (int i = 0; i < stations.size(); i++) {
			if (stations.get(i).scene().equals(scene)) {
				return i;
			}
		}
		return -1;
	}

	private static Component titleOf(ResourceLocation scene) {
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

	private @Nullable Advancement advancement(Spot spot) {
		return spot.advancement().isEmpty() || minecraft.getConnection() == null ? null
			: minecraft.getConnection().getAdvancements().getAdvancements().get(spot.advancement().get());
	}

	/** Done: the player has the station's advancement (the client knows its own progress). */
	private boolean done(Spot spot) {
		Advancement advancement = advancement(spot);
		if (advancement == null) {
			return false;
		}
		ClientAdvancements advancements = minecraft.getConnection().getAdvancements();
		AdvancementProgress progress = ((ClientAdvancementsAccessor) advancements).vineandbarrel$progress().get(advancement);
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
		// the chosen station's wiki page: a grey line under what it is (in the tab order before Watch, as on the card),
		// only where it fits above the button
		Spot spot = spot(selected);
		Component page = Component.translatable("watch.vineandbarrel.path.wiki_page");
		int textX = cardPreview ? colX : cardX + 10;
		int textRight = cardPreview ? cardX + cardW - 10 : colX - 8;
		cardWiki = spot.wiki().isPresent() && textX + font.width(page) <= textRight
			&& cardY + statusY(spot, true) + 9 <= (cardPreview ? buttonsY - 4 : cardY + cardH - 6);
		if (cardWiki) {
			addRenderableWidget(new WikiLink(textX, cardY + wikiY(spot), page, b -> openWiki(spot.wiki().get())));
		}
		addRenderableWidget(Button.builder(Component.translatable("watch.vineandbarrel.path.watch"), b -> watch(selected))
			.bounds(colX, buttonsY, colW, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("watch.vineandbarrel.path.watch_all"), b -> watch(0))
			.bounds(ox + 8, footerY + 12, 100, 20).build());
		// the wiki's home page: a button as wide as its label right after Watch all, only where it fits before the progress
		Component wiki = Component.translatable("watch.vineandbarrel.path.wiki");
		int wikiX = ox + 8 + 100 + 10, wikiW = Math.max(40, font.width(wiki) + 20);
		if (wikiX + wikiW + 10 <= progressX(progress())) {
			addRenderableWidget(Button.builder(wiki, b -> openWiki("")).bounds(wikiX, footerY + 12, wikiW, 20).build());
		}
		Button done = addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> minecraft.setScreen(WatchScreen.outside(parent)))
			.bounds(ox + ow - 8 - DONE_W, footerY + 12, DONE_W, 20).build());
		setInitialFocus(done);
	}

	/** Opens a wiki page ("" for its home page) in the player's language, through the game's "Open this link?" prompt. */
	private void openWiki(String page) {
		String language = WIKI_LANGUAGES.get(minecraft.getLanguageManager().getSelected());
		String url = WIKI + (language == null ? "" : language + "/") + (page.isEmpty() ? "" : page + "/");
		ConfirmLinkScreen.confirmLinkNow(url, this, true);
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
			minecraft.getNarrator().say(CommonComponents.joinForNarration(spot(index).name(), status(index)));
		}
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return true;
	}

	// ------------------------------------------------------------------ drawing

	/**
	 * Everything under the buttons: the window, the path and the chosen station's card. (On this Minecraft version a
	 * screen draws its background itself, and the world behind it is darkened, not blurred.)
	 */
	private void renderBackground(GuiGraphics gui, int mouseX, int mouseY, float a) {
		renderBackground(gui);
		gui.fill(0, 0, width, height, 0x60000000);
		RenderSystem.enableBlend();
		WatchScreen.blitNineSlice(gui, WatchScreen.PANEL, ox, oy, ow, oh, WatchScreen.PANEL_SIZE, WatchScreen.PANEL_BORDER);
		gui.fill(ox + 8, stageY, ox + ow - 8, stageY + stageH, STAGE);
		renderPath(gui, mouseX, mouseY);
	}

	@Override
	public void render(GuiGraphics gui, int mouseX, int mouseY, float a) {
		renderBackground(gui, mouseX, mouseY, a);
		super.render(gui, mouseX, mouseY, a);
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

	private void renderPath(GuiGraphics gui, int mouseX, int mouseY) {
		int next = nextForYou();
		gui.drawString(font, title, ox + 8, oy + 6, TITLE_INK, false);
		RenderSystem.enableBlend();
		WatchScreen.blitNineSlice(gui, WatchScreen.INSET, ox + 6, stageY - 2, ow - 12, stageH + 4, WatchScreen.INSET_SIZE, WatchScreen.INSET_BORDER);
		Component progress = progress();
		gui.drawString(font, progress, progressX(progress), footerY + 18, TITLE_INK, false);

		// the road between the stations: walked (light) up to where you are, the rest still dark
		int y = pathY + box / 2;
		for (int i = 0; i + 1 < stations.size(); i++) {
			boolean walked = done(stations.get(i)) && (done(stations.get(i + 1)) || next == i + 1);
			gui.fill(stationX(i) + box / 2, y - 1, stationX(i + 1) - box / 2, y + 2, walked ? DONE : TRACK);
		}
		for (int b = 0; b < branches.size(); b++) {
			renderBranch(gui, b, y, mouseX, mouseY);
		}
		for (int i = 0; i < stations.size(); i++) {
			Spot station = stations.get(i);
			int x = stationX(i) - box / 2;
			renderStation(gui, i, station, x, pathY, box, done(station), i == next, mouseX, mouseY);
			gui.drawCenteredString(font, station.name().copy().withStyle(ChatFormatting.BOLD), x + box / 2, pathY + box + (compact ? 4 : 6), i == next ? HIGHLIGHT : INK);
			if (!compact) {
				gui.drawCenteredString(font, titleOf(station.scene()), x + box / 2, pathY + box + 17, INK_MUTED);
			}
			if (i == next) {
				gui.drawCenteredString(font, Component.translatable("watch.vineandbarrel.path.next_for_you"), x + box / 2, pathY - (compact ? 12 : 14), HIGHLIGHT);
			}
		}

		// the chosen station, large: its scene's finished stage, what it is, whether you've done it
		gui.fill(cardX, cardY, cardX + cardW, cardY + cardH, 0xC0000000);
		int px0 = cardX + 8, py0 = cardY + 8, px1 = cardX + cardW / 2, py1 = cardY + cardH - 8;
		ScenePlayer shown = cardPreview ? preview(selected) : null;
		if (shown != null && previewView != null) {
			gui.fill(px0, py0, px1, py1, 0xFF161B1E);
			SceneRenderer.render(gui,
				new SceneRenderState(shown, previewView, 0.0F, px0, py0, px1, py1, SceneView.scale(shown.scene(), px1 - px0, py1 - py0) * 0.9F, null));
		}
		Spot spot = spot(selected);
		int colX = cardPreview ? cardX + cardW / 2 + 12 : cardX + 10;
		gui.drawString(font, spot.name().copy().withStyle(ChatFormatting.BOLD), colX, cardY + (compact ? 9 : 12), INK, true);
		Optional<WatchScene> scene = WatchIndex.INSTANCE.scene(spot.scene());
		Component steps = Component.translatable("watch.vineandbarrel.steps", scene.map(s -> s.steps().size()).orElse(0));
		if (spot.branch()) {
			gui.drawString(font, steps, colX, cardY + (compact ? 21 : 24), INK_MUTED, false);
		} else if (compact) {
			gui.drawString(font, Component.empty().append(titleOf(spot.scene())).append(" · ").append(steps), colX, cardY + 21, INK_MUTED, false);
		} else {
			gui.drawString(font, titleOf(spot.scene()), colX, cardY + 24, INK_MUTED, false);
			gui.drawString(font, steps, colX, cardY + 35, INK_MUTED, false);
		}
		gui.drawString(font, status(selected), colX, cardY + statusY(spot, cardWiki), statusColor(selected), true);
	}

	private void renderBranch(GuiGraphics gui, int b, int roadY, int mouseX, int mouseY) {
		Spot branch = branches.get(b);
		int bx = branchX(b);
		int by = branchY(branchRow(b));
		gui.fill(bx, roadY + 2, bx + 2, by, TRACK);
		renderStation(gui, stations.size() + b, branch, bx - BRANCH_BOX / 2 + 1, by, BRANCH_BOX, false, false, mouseX, mouseY);
		// its name beside it, and its note under the name only where it fits inside the stage (never past its edge)
		Component name = branch.name().copy().withStyle(ChatFormatting.BOLD);
		int room = ox + ow - 10 - (bx + 20);
		int labelX = font.width(name) <= room ? bx + 20 : bx - 16 - font.width(name);
		gui.drawString(font, name, labelX, by + 3, INK, true);
		branch.note().map(Component::translatable).ifPresent(note -> {
			boolean right = labelX > bx;
			if (font.width(note) <= (right ? room : bx - 16 - (ox + 10))) {
				gui.drawString(font, note, right ? labelX : bx - 16 - font.width(note), by + 14, INK_MUTED, false);
			}
		});
	}

	/** "2 of 4 done" (or "All done"), right in the footer, before Done. */
	private Component progress() {
		int doneCount = (int) stations.stream().filter(this::done).count();
		int ticked = (int) stations.stream().filter(s -> s.advancement().isPresent()).count();
		return nextForYou() < 0 ? Component.translatable("watch.vineandbarrel.path.all_done")
			: Component.translatable("watch.vineandbarrel.path.progress", doneCount, ticked);
	}

	private int progressX(Component progress) {
		return ox + ow - 8 - DONE_W - 10 - font.width(progress);
	}

	/** The card's wiki line: a line under the last grey line of what the spot is (its scene's steps). */
	private int wikiY(Spot spot) {
		return (!spot.branch() && !compact ? 35 : spot.branch() && !compact ? 24 : 21) + 12;
	}

	/** The card's status line: under the wiki line when there is one. */
	private int statusY(Spot spot, boolean wiki) {
		int plain = compact ? 35 : 54;
		return wiki ? Math.max(plain, wikiY(spot) + 14) : plain;
	}

	private Component status(int index) {
		Spot spot = spot(index);
		if (spot.advancement().isEmpty()) {
			return Component.translatable("watch.vineandbarrel.path.optional");
		}
		if (done(spot)) {
			// a root advancement is the mod's own "you've started": just "Done"
			Advancement advancement = advancement(spot);
			Optional<Component> name = advancement == null || advancement.getParent() == null ? Optional.empty()
				: Optional.ofNullable(advancement.getDisplay()).map(DisplayInfo::getTitle);
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
	private void renderStation(GuiGraphics gui, int index, Spot spot, int x, int y, int size, boolean done, boolean next,
								int mouseX, int mouseY) {
		boolean hover = mouseX >= x && mouseX < x + size && mouseY >= y && mouseY < y + size;
		// a big inventory slot: grey with the slot's own bevel
		gui.fill(x, y, x + size, y + size, 0xFF8B8B8B);
		gui.fill(x, y, x + size, y + 2, 0xFF373737);
		gui.fill(x, y, x + 2, y + size, 0xFF373737);
		gui.fill(x, y + size - 2, x + size, y + size, 0xFFFFFFFF);
		gui.fill(x + size - 2, y, x + size, y + size, 0xFFFFFFFF);
		if (next) {
			gui.renderOutline(x - 2, y - 2, size + 4, size + 4, HIGHLIGHT);
			gui.renderOutline(x - 3, y - 3, size + 6, size + 6, 0xFF000000);
		} else if (index == selected || hover) {
			gui.renderOutline(x - 2, y - 2, size + 4, size + 4, 0xFFFFFFFF);
		}
		int scale = size >= 32 ? 2 : 1;
		gui.pose().pushPose();
		gui.pose().translate(x + (size - 16 * scale) / 2.0F, y + (size - 16 * scale) / 2.0F, 0.0F);
		gui.pose().scale(scale, scale, 1.0F);
		gui.renderItem(WatchIndex.INSTANCE.icon(spot.scene()), 0, 0);
		gui.pose().popPose();
		// over the icon (an item is drawn in front of the screen's plane on this Minecraft version)
		gui.pose().pushPose();
		gui.pose().translate(0.0F, 0.0F, 200.0F);
		if (!done && !next && spot.advancement().isPresent()) {
			// not reached yet: dimmed, and without a tick (the tick and the frame say it, not the dimming alone)
			gui.fill(x + 2, y + 2, x + size - 2, y + size - 2, 0x80373737);
		}
		if (done) {
			int tx = x + size - 9, ty = y - 4;
			gui.fill(tx, ty, tx + 12, ty + 12, 0xFF3B3B3B);
			gui.renderOutline(tx, ty, 12, 12, 0xFFFFFFFF);
			int[][] tick = {{2, 6}, {3, 7}, {4, 8}, {5, 7}, {6, 6}, {7, 5}, {8, 4}, {9, 3}};
			for (int[] p : tick) {
				gui.fill(tx + p[0], ty + p[1], tx + p[0] + 1, ty + p[1] + 2, INK);
			}
		}
		gui.pose().popPose();
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
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		// a double click: the same button again within a quarter of a second
		long clickAt = Util.getMillis();
		boolean doubleClick = button == lastClickButton && clickAt - lastClickAt < 250L;
		lastClickButton = button;
		lastClickAt = clickAt;
		int spot = spotAt(mouseX, mouseY);
		if (spot >= 0) {
			select(spot);
			if (doubleClick) {
				watch(spot);
			}
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean keyPressed(int key, int scanCode, int modifiers) {
		switch (key) {
			case InputConstants.KEY_LEFT -> select(selected - 1);
			case InputConstants.KEY_RIGHT -> select(selected + 1);
			default -> {
				return super.keyPressed(key, scanCode, modifiers);
			}
		}
		return true;
	}

	/**
	 * The card's link as quiet text, not a button: grey, underlined and yellow (as "Next for you") while the mouse is on it
	 * or Tab has reached it.
	 */
	private class WikiLink extends Button
	{
		private final Component underlined;

		WikiLink(int x, int y, Component message, OnPress onPress) {
			super(x, y, font.width(message), font.lineHeight, message, onPress, DEFAULT_NARRATION);
			this.underlined = ComponentUtils.mergeStyles(message.copy(), Style.EMPTY.withUnderlined(true));
		}

		@Override
		public void renderWidget(GuiGraphics gui, int mouseX, int mouseY, float a) {
			boolean lit = isHoveredOrFocused();
			gui.drawString(font, lit ? underlined : getMessage(), getX(), getY(), lit ? HIGHLIGHT : INK_MUTED, lit);
		}
	}

	@Override
	public Component getNarrationMessage() {
		return CommonComponents.joinForNarration(title, spot(selected).name(), status(selected));
	}
}
