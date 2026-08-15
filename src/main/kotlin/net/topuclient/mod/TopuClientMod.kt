package net.topuclient.mod

import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback
import net.fabricmc.fabric.api.event.player.AttackEntityCallback
import net.minecraft.client.MinecraftClient
import net.minecraft.client.option.KeyBinding
import net.minecraft.client.util.InputUtil
import net.minecraft.item.AxeItem
import net.minecraft.item.SwordItem
import net.minecraft.util.ActionResult
import org.lwjgl.glfw.GLFW

class TopuClientMod : ClientModInitializer {
    override fun onInitializeClient() {
        TopuConfig.load()

        val configKey = KeyBindingHelper.registerKeyBinding(
            KeyBinding("key.topuclient.menu", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, "category.topuclient")
        )

        val layoutKey = KeyBindingHelper.registerKeyBinding(
            KeyBinding("key.topuclient.layout", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_0, "category.topuclient")
        )

        ClientTickEvents.END_CLIENT_TICK.register { client ->
            if (client.player == null) return@register
            
            // Check for Left Click input to track CPS
            if (client.options.attackKey.isPressed) {
                TopuHudRenderer.registerLeftClick()
            }

            while (configKey.wasPressed()) { if (client.currentScreen == null) client.setScreen(TopuConfigScreen()) }
            while (layoutKey.wasPressed()) { if (client.currentScreen == null) client.setScreen(TopuHudLayoutScreen()) }
        }

        HudRenderCallback.EVENT.register(TopuHudRenderer)

        // 1.9+ Smart Attack Blocker
        AttackEntityCallback.EVENT.register { player, world, hand, entity, hitResult ->
            val client = MinecraftClient.getInstance()
            if (world.isClient && player == client.player && TopuConfig.smartAttack.enabled) {
                val heldItem = player.getStackInHand(hand).item
                val cooldownProgress = player.getAttackCooldownProgress(0.5f)

                when (heldItem) {
                    is SwordItem -> if (cooldownProgress < 0.80f) return@register ActionResult.FAIL
                    is AxeItem -> if (cooldownProgress < 1.0f) return@register ActionResult.FAIL
                }
            }
            ActionResult.PASS
        }
    }
}
