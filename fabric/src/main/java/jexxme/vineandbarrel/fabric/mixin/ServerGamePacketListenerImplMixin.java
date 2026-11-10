package jexxme.vineandbarrel.fabric.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import jexxme.vineandbarrel.fabric.FabricChat;
import net.minecraft.network.chat.ChatDecorator;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Slurred chat for a Tipsy player's typed chat message only, the spot where NeoForge posts {@code ServerChatEvent}: the
 * typed text is slurred before the server's chat decorators run on it, so what other chat mods add stays as they made it.
 * The chat packet's handling runs in a lambda of {@code handleChat(ServerboundChatPacket)}: {@code method_44900} is that
 * lambda {@code (ServerboundChatPacket, Optional)} by its intermediary name, which it has both in development and in a
 * released game on this Minecraft (lambdas have no other name in the mappings). Commands that decorate messages too
 * ({@code /me}, {@code /say}, {@code /msg}, {@code /tell}) go through {@code MessageArgument}, not here, so they stay
 * as typed (Fabric API's message decorator event can't tell them apart).
 */
@Mixin(ServerGamePacketListenerImpl.class)
abstract class ServerGamePacketListenerImplMixin
{
	// The lambda keeps its own name, so only the call inside goes through the refmap
	@WrapOperation(method = "method_44900", remap = false,
		at = @At(value = "INVOKE", target = "Lnet/minecraft/network/chat/ChatDecorator;decorate(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/network/chat/Component;)Lnet/minecraft/network/chat/Component;", remap = true))
	private Component vineandbarrel$slurChat(ChatDecorator decorator, ServerPlayer player, Component message, Operation<Component> decorate) {
		return decorate.call(decorator, player, FabricChat.slur(player, message));
	}
}
