package net.topuclient.mod

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.client.sound.PositionedSoundInstance
import net.minecraft.sound.SoundEvents
import net.minecraft.text.Text
import org.lwjgl.glfw.GLFW
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList

object TopuHudRenderer : HudRenderCallback {
    private val clickTimestamps = ArrayList<Long>()
    private var lastWarningSoundTime: Long = 0

    fun registerLeftClick() {
        clickTimestamps.add(System.currentTimeMillis())
    }

    private fun getCps(): Int {
        val now = System.currentTimeMillis()
        clickTimestamps.removeIf { it < now - 1000 }
        return clickTimestamps.size
    }

    override fun onHudRender(context: DrawContext, tickDelta: Float) {
        val client = MinecraftClient.getInstance()
        val player = client.player ?: return
        if (client.options.hudHidden) return

        fun drawTextElement(element: TopuConfig.HudElement, text: String) {
            if (!element.enabled) return
            context.matrices.push()
            context.matrices.translate(element.x.toFloat(), element.y.toFloat(), 0f)
            context.matrices.scale(element.scale, element.scale, 1.0f)
            context.drawText(client.textRenderer, text, 0, 0, 0xFFFFFF, true)
            context.matrices.pop()
        }

        drawTextElement(TopuConfig.fps, "§bFPS: §f${MinecraftClient.getCurrentFps()}")
        
        val latency = client.networkHandler?.getPlayerListEntry(player.uuid)?.latency ?: 0
        drawTextElement(TopuConfig.ping, "§bPing: §f${latency}ms")
        drawTextElement(TopuConfig.cps, "§bCPS: §f${getCps()}")
        drawTextElement(TopuConfig.tps, "§bServer TPS: §f20.0")
        drawTextElement(TopuConfig.coords, "§bCoords: §fX: ${player.blockX} Y: ${player.blockY} Z: ${player.blockZ}")
        drawTextElement(TopuConfig.direction, "§bFacing: §f${player.horizontalFacing.name.uppercase()}")
        drawTextElement(TopuConfig.clock, "§bClock: §f${SimpleDateFormat("HH:mm:ss").format(Date())}")

        val runtime = Runtime.getRuntime()
        val usedMem = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024
        drawTextElement(TopuConfig.memoryUsage, "§bRAM: §f${usedMem}MB")

        if (TopuConfig.armorHud.enabled) {
            context.matrices.push()
            context.matrices.translate(TopuConfig.armorHud.x.toFloat(), TopuConfig.armorHud.y.toFloat(), 0f)
            context.matrices.scale(TopuConfig.armorHud.scale, TopuConfig.armorHud.scale, 1.0f)
            var offset = 0
            var triggerSiren = false

            for (i in 3 downTo 0) {
                val stack = player.inventory.getArmorStack(i)
                if (!stack.isEmpty) {
                    val maxDura = stack.maxDamage
                    val currentDura = maxDura - stack.damage
                    val pct = ((currentDura.toFloat() / maxDura.toFloat()) * 100).toInt()
                    
                    if (pct <= 50) triggerSiren = true

                    val color = if (pct <= 25) "§c" else if (pct <= 50) "§e" else "§a"
                    context.drawText(client.textRenderer, "${stack.item.name.string}: $color$currentDura/$maxDura ($pct%)", 0, offset, 0xFFFFFF, true)
                    offset += 12
                }
            }
            context.matrices.pop()

            if (triggerSiren && TopuConfig.armorDuraWarning.enabled) {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastWarningSoundTime > 800) {
                    client.soundManager.play(PositionedSoundInstance.master(SoundEvents.BLOCK_NOTE_BLOCK_BASS, 1.0f, 1.0f))
                    lastWarningSoundTime = currentTime
                }
            }
        }
    }
}

class TopuConfigScreen : Screen(Text.literal("TopuClient Settings")) {
    override fun init() {
        var y = 30
        fun addToggle(name: String, element: TopuConfig.HudElement) {
            this.addSelectableChild(ButtonWidget.builder(Text.literal("$name: ${if (element.enabled) "§aON" else "§cOFF"}"), ButtonWidget.PressAction { btn ->
                element.enabled = !element.enabled
                btn.message = Text.literal("$name: ${if (element.enabled) "§aON" else "§cOFF"}")
                TopuConfig.save()
            }).dimensions(this.width / 2 - 60, y, 120, 18).build())
            y += 22
        }
        addToggle("Smart Attack", TopuConfig.smartAttack)
        addToggle("Armor HUD", TopuConfig.armorHud)
        addToggle("Dura Siren", TopuConfig.armorDuraWarning)
        addToggle("CPS Counter", TopuConfig.cps)
        addToggle("FPS Counter", TopuConfig.fps)
        addToggle("Ping Counter", TopuConfig.ping)
        addToggle("Server TPS", TopuConfig.tps)
        addToggle("Coordinates", TopuConfig.coords)
        addToggle("Directional", TopuConfig.direction)
        addToggle("OS Clock", TopuConfig.clock)
        addToggle("RAM Tracker", TopuConfig.memoryUsage)
    }
    override fun render(c: DrawContext, mX: Int, mY: Int, d: Float) { this.renderBackground(c); c.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 10, 0x00FFFF); super.render(c, mX, mY, d) }
    override fun shouldPauseGame() = false
}

class TopuHudLayoutScreen : Screen(Text.literal("HUD Editor")) {
    private var active: TopuConfig.HudElement? = null
    private var offX = 0; private var offY = 0
    
    private val elements = mapOf(
        TopuConfig.armorHud to "Armor HUD",
        TopuConfig.cps to "CPS Counter",
        TopuConfig.fps to "FPS Counter",
        TopuConfig.ping to "Ping Metric",
        TopuConfig.tps to "Server TPS",
        TopuConfig.coords to "Coords Box",
        TopuConfig.direction to "Compass Direction",
        TopuConfig.clock to "OS System Clock",
        TopuConfig.memoryUsage to "RAM Monitor"
    )

    override fun render(c: DrawContext, mx: Int, my: Int, d: Float) {
        this.renderBackground(c)
        c.drawCenteredTextWithShadow(this.textRenderer, "Drag Elements / Scroll Wheel to Scale", this.width / 2, 10, 0xFFFFFF)

        for ((el, name) in elements) {
            if (!el.enabled) continue
            val w = (this.textRenderer.getWidth(name) * el.scale).toInt()
            val h = (12 * el.scale).toInt()
            val boxColor = if (el == active) 0xAA00FFFF.toInt() else 0x55FFFFFF
            c.fill(el.x - 2, el.y - 2, el.x + w + 2, el.y + h + 2, boxColor)
            c.drawText(this.textRenderer, name, el.x, el.y, 0xFFFFFF, false)
        }
    }

    override fun mouseClicked(mx: Double, my: Double, b: Int): Boolean {
        if (b == 0) {
            for ((el, name) in elements) {
                if (!el.enabled) continue
                val w = this.textRenderer.getWidth(name) * el.scale
                val h = 12 * el.scale
                if (mx >= el.x && mx <= el.x + w && my >= el.y && my <= el.y + h) { 
                    active = el
                    offX = (mx - el.x).toInt()
                    offY = (my - el.y).toInt()
                    return true 
                }
            }
        }
        return super.mouseClicked(mx, my, b)
    }

    override fun mouseDragged(mx: Double, my: Double, b: Int, dx: Double, dy: Double): Boolean { 
        active?.let { it.x = (mx - offX).toInt(); it.y = (my - offY).toInt(); return true }
        return super.mouseDragged(mx, my, b, dx, dy) 
    }
    
    override fun mouseScrolled(mx: Double, my: Double, h: Double, v: Double): Boolean {
        for ((el, name) in elements) {
            if (!el.enabled) continue
            val w = this.textRenderer.getWidth(name) * el.scale
            val h = 12 * el.scale
            if (mx >= el.x && mx <= el.x + w && my >= el.y && my <= el.y + h) { 
                el.scale = if (v > 0) (el.scale + 0.1f).coerceAtMost(2.0f) else (el.scale - 0.1f).coerceAtLeast(0.4f)
                return true 
            }
        }
        return super.mouseScrolled(mx, my, h, v)
    }
    
    override fun mouseReleased(mx: Double, my: Double, b: Int): Boolean { active = null; TopuConfig.save(); return super.mouseReleased(mx, my, b) }
    override fun keyPressed(k: Int, s: Int, m: Int): Boolean { if (k == GLFW.GLFW_KEY_ESCAPE || k == GLFW.GLFW_KEY_0) { this.close(); return true }; return super.keyPressed(k, s, m) }
    override fun shouldPauseGame() = false
}
