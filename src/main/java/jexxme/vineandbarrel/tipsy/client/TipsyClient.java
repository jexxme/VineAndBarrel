package jexxme.vineandbarrel.tipsy.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import jexxme.vineandbarrel.VineAndBarrel;
import jexxme.vineandbarrel.tipsy.Blackout;
import jexxme.vineandbarrel.tipsy.Tipsy;
import jexxme.vineandbarrel.tipsy.TipsyConfig;
import jexxme.vineandbarrel.tipsy.TipsyRegistry;
import jexxme.vineandbarrel.tipsy.TipsyInfo;
import jexxme.vineandbarrel.tipsy.TipsyVision;
import jexxme.vineandbarrel.tipsy.TipsyWobble;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Pose;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.GatherEffectScreenTooltipsEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.extensions.common.IClientMobEffectExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.util.List;

/**
 * Client side of Tipsy: the hiccup particle, the camera wobble (own client setting, always wins) and the sway other
 * players see (T1). The math lives in {@link TipsyWobble} so it is tested headless.
 */
@EventBusSubscriber(modid = VineAndBarrel.MODID, value = Dist.CLIENT)
public final class TipsyClient
{
	private static final ContextKey<Integer> SWAY_LEVEL = new ContextKey<>(VineAndBarrel.id("tipsy_sway_level"));
	private static final ContextKey<Boolean> SWAY_PUSHED = new ContextKey<>(VineAndBarrel.id("tipsy_sway_pushed"));
	/** Per tick, how far the shown wobble eases toward the real level (about 1.5 s to settle). */
	private static final float EASE = 0.05F;

	private static float wobbleLevel;
	private static float wobbleLevelO;

	private TipsyClient() {
	}

	/** Like FD's milk bottle ("Clears 1 Effect"): drinks that sober you up say so, in blue, under their name. */
	@SubscribeEvent
	public static void onItemTooltip(ItemTooltipEvent event) {
		if (TipsyConfig.enabled() && Tipsy.isSoberingDrink(event.getItemStack())) {
			List<Component> lines = event.getToolTip();
			lines.add(Math.min(1, lines.size()), Component.translatable("tooltip.vineandbarrel.sobering").withStyle(ChatFormatting.BLUE));
		}
	}

	/** Hovering Tipsy in the inventory's effect list says what this level does and what the next drink adds. */
	@SubscribeEvent
	public static void onEffectTooltip(GatherEffectScreenTooltipsEvent event) {
		MobEffectInstance effect = event.getEffectInstance();
		if (!effect.is(TipsyRegistry.TIPSY)) {
			return;
		}
		List<Component> tooltip = event.getTooltip();
		if (tooltip.isEmpty()) {
			// The wide effect list shows name and time itself and has no tooltip; ours needs a title
			tooltip.add(Component.translatable("potion.withAmplifier", effect.getEffect().value().getDisplayName(),
				Component.translatable("enchantment.level." + (effect.getAmplifier() + 1))));
		}
		tooltip.addAll(TipsyInfo.lines(effect.getAmplifier() + 1));
	}

	/**
	 * Tipsy's side effects (and the blackout's) come without an icon, but the inventory lists every effect anyway. For
	 * these vanilla effects the inventory shows an instance only when it has its icon, so a potion's or a command's
	 * still shows exactly as in vanilla. If another mod already claimed them, vanilla behaviour stays.
	 */
	@SubscribeEvent
	public static void registerClientExtensions(RegisterClientExtensionsEvent event) {
		try {
			event.registerMobEffect(new IClientMobEffectExtensions()
			{
				@Override
				public boolean isVisibleInInventory(MobEffectInstance instance) {
					return instance.showIcon();
				}
			}, MobEffects.SLOWNESS, MobEffects.MINING_FATIGUE, MobEffects.WEAKNESS, MobEffects.NAUSEA);
		} catch (IllegalStateException alreadyRegistered) {
			VineAndBarrel.LOGGER.debug("Effect visibility for Tipsy's side effects is already handled by another mod", alreadyRegistered);
		}
	}

	@SubscribeEvent
	public static void registerParticles(RegisterParticleProvidersEvent event) {
		event.registerSpriteSet(TipsyRegistry.HICCUP_PARTICLE.get(), HiccupParticle.Provider::new);
	}

	@SubscribeEvent
	@SuppressWarnings({"unchecked", "rawtypes"})
	public static void registerRenderState(RegisterRenderStateModifiersEvent event) {
		// 26.1: NeoForge 26.1.0 has no registerAvatarEntityModifier; this is what it does in 26.1.1 and later
		event.<Avatar, AvatarRenderState>registerEntityModifier((Class) AvatarRenderer.class, (avatar, state) -> {
			Integer level = avatar.getExistingDataOrNull(TipsyRegistry.SYNCED_LEVEL);
			state.setRenderData(SWAY_LEVEL, level != null && level > 0 ? level : null);
		});
	}

	// ------------------------------------------------------------------ own camera

	@SubscribeEvent
	public static void onClientTick(ClientTickEvent.Post event) {
		LocalPlayer player = Minecraft.getInstance().player;
		float target = player == null ? 0.0F : Tipsy.getLevel(player);
		wobbleLevelO = wobbleLevel;
		wobbleLevel += (target - wobbleLevel) * EASE;
		if (Math.abs(target - wobbleLevel) < 0.01F) {
			wobbleLevel = target;
		}
	}

	@SubscribeEvent
	public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null || event.getCamera().entity() != player) {
			return;
		}
		float partial = (float) event.getPartialTick();
		float level = Mth.lerp(partial, wobbleLevelO, wobbleLevel);
		TipsyWobble.Angles angles = TipsyWobble.camera(level, player.tickCount + partial, TipsyConfig.wobbleIntensity());
		if (angles == TipsyWobble.Angles.NONE) {
			return;
		}
		event.setRoll(event.getRoll() + angles.roll());
		event.setPitch(event.getPitch() + angles.pitch());
		event.setYaw(event.getYaw() + angles.yaw());
	}

	// ------------------------------------------------------------------ tunnel vision and heavy-eyed blinks

	private static final Identifier VIGNETTE = VineAndBarrel.id("textures/gui/tipsy_vignette.png");
	private static final int VIGNETTE_SIZE = 128;

	/** Above the vanilla camera overlays (pumpkin, powder snow), under the crosshair and the HUD, which stay readable. */
	@SubscribeEvent
	public static void registerGuiLayers(RegisterGuiLayersEvent event) {
		event.registerAbove(VanillaGuiLayers.CAMERA_OVERLAYS, VineAndBarrel.id("tipsy_vision"), TipsyClient::renderVision);
		// The blackout covers everything, the HUD included (and is not part of the optional vision effects)
		event.registerAboveAll(VineAndBarrel.id("blackout"), TipsyClient::renderBlackout);
	}

	private static void renderBlackout(GuiGraphicsExtractor gui, DeltaTracker delta) {
		LocalPlayer player = Minecraft.getInstance().player;
		Integer left = player == null ? null : player.getExistingDataOrNull(TipsyRegistry.BLACKOUT);
		if (left == null || left <= 0) {
			return;
		}
		float remaining = left - delta.getGameTimeDeltaPartialTick(false);
		float elapsed = Blackout.TICKS - remaining;
		float dark = elapsed < Blackout.FADE_OUT ? elapsed / Blackout.FADE_OUT
			: remaining < Blackout.FADE_IN ? remaining / Blackout.FADE_IN : 1.0F;
		drawLids(gui, Mth.clamp(dark, 0.0F, 1.0F));
	}

	/** Two lids with a soft edge that meet in the middle; fully closed they cover the whole view. */
	private static void drawLids(GuiGraphicsExtractor gui, float closed) {
		int width = gui.guiWidth();
		int height = gui.guiHeight();
		int feather = Math.max(4, height / 8);
		int lid = Math.round(closed * (height / 2.0F + feather));
		gui.fill(0, 0, width, lid - feather, 0xFF000000);
		gui.fillGradient(0, lid - feather, width, lid, 0xFF000000, 0x00000000);
		gui.fillGradient(0, height - lid, width, height - lid + feather, 0x00000000, 0xFF000000);
		gui.fill(0, height - lid + feather, width, height, 0xFF000000);
	}

	private static void renderVision(GuiGraphicsExtractor gui, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null || !TipsyConfig.visionEffects()) {
			return;
		}
		float partial = delta.getGameTimeDeltaPartialTick(false);
		int width = gui.guiWidth();
		int height = gui.guiHeight();
		float vignette = TipsyVision.vignette(Mth.lerp(partial, wobbleLevelO, wobbleLevel));
		if (vignette > 0.0F) {
			int color = ARGB.color(Math.round(vignette * 255.0F), 0xFFFFFF);
			gui.blit(RenderPipelines.GUI_TEXTURED, VIGNETTE, 0, 0, 0.0F, 0.0F, width, height, VIGNETTE_SIZE, VIGNETTE_SIZE,
				VIGNETTE_SIZE, VIGNETTE_SIZE, color);
		}
		float closed = TipsyVision.eyelids(Tipsy.getLevel(player), player.tickCount + partial, player.getId());
		if (closed > 0.0F) {
			drawLids(gui, closed);
		}
	}

	// ------------------------------------------------------------------ T1: others see you sway

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void onRenderLivingPre(RenderLivingEvent.Pre<?, ?, ?> event) {
		LivingEntityRenderState state = event.getRenderState();
		Integer level = state.getRenderData(SWAY_LEVEL);
		if (level == null || state.hasPose(Pose.SLEEPING) || !TipsyConfig.otherPlayersSway()) {
			return;
		}
		int entityId = state instanceof AvatarRenderState avatar ? avatar.id : 0;
		float sway = TipsyWobble.bodySway(level, state.ageInTicks, entityId);
		float facing = 180.0F - state.bodyRot;
		PoseStack pose = event.getPoseStack();
		pose.pushPose();
		// Lean sideways around the feet, relative to where the body faces
		pose.mulPose(Axis.YP.rotationDegrees(facing));
		pose.mulPose(Axis.ZP.rotationDegrees(sway));
		pose.mulPose(Axis.YP.rotationDegrees(-facing));
		state.setRenderData(SWAY_PUSHED, Boolean.TRUE);
	}

	@SubscribeEvent
	public static void onRenderLivingPost(RenderLivingEvent.Post<?, ?, ?> event) {
		LivingEntityRenderState state = event.getRenderState();
		if (Boolean.TRUE.equals(state.getRenderData(SWAY_PUSHED))) {
			state.setRenderData(SWAY_PUSHED, null);
			event.getPoseStack().popPose();
		}
	}
}
