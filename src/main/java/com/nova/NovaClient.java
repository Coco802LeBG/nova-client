package com.nova;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public class NovaClient implements ClientModInitializer {
	public static KeyMapping MENU, WIN, LOSS, RESET;

	@Override
	public void onInitializeClient() {
		KeyMapping.Category cat = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("nova", "main"));
		MENU = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.nova.menu", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, cat));
		WIN = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.nova.win", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_KP_1, cat));
		LOSS = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.nova.loss", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_KP_2, cat));

		RESET = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.nova.reset", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_KP_0, cat));

		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			while (MENU.consumeClick()) mc.setScreen(new NovaScreen());
			while (WIN.consumeClick()) Session.I.manual(true);
			while (LOSS.consumeClick()) Session.I.manual(false);
			while (RESET.consumeClick()) Session.I.resetProfit();
			Session.I.tick(mc);
		});

		ClientReceiveMessageEvents.GAME.register((msg, overlay) -> {
			Session.I.onChat(msg.getString());
		});

		ClientReceiveMessageEvents.MODIFY_GAME.register((msg, overlay) -> overlay ? msg : Nick.apply(msg));
		ClientSendMessageEvents.COMMAND.register(Session.I::onCommand);

		HudElementRegistry.attachElementAfter(VanillaHudElements.CHAT, Identifier.fromNamespaceAndPath("nova", "hud"), NovaHud::render);
	}
}
