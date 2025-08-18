/*
 * GoldBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/bzym2/GoldBounce/
 */
package net.ccbluex.liquidbounce.features.module.modules.render

import net.ccbluex.liquidbounce.event.EventTarget
import net.ccbluex.liquidbounce.event.Render2DEvent
import net.ccbluex.liquidbounce.event.Render3DEvent
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.features.module.modules.misc.AntiBot.isBot
import net.ccbluex.liquidbounce.ui.font.GameFontRenderer.Companion.getColorIndex
import net.ccbluex.liquidbounce.utils.ClientUtils.LOGGER
import net.ccbluex.liquidbounce.utils.RotationUtils.isEntityHeightVisible
import net.ccbluex.liquidbounce.utils.attack.EntityUtils.isLookingOnEntities
import net.ccbluex.liquidbounce.utils.attack.EntityUtils.isSelected
import net.ccbluex.liquidbounce.utils.extensions.*
import net.ccbluex.liquidbounce.utils.render.ColorSettingsInteger
import net.ccbluex.liquidbounce.utils.render.ColorUtils
import net.ccbluex.liquidbounce.utils.render.ColorUtils.rainbow
import net.ccbluex.liquidbounce.utils.render.RenderUtils.draw2D
import net.ccbluex.liquidbounce.utils.render.RenderUtils.drawEntityBox
import net.ccbluex.liquidbounce.utils.render.WorldToScreen
import net.ccbluex.liquidbounce.utils.render.shader.shaders.GlowShader
import net.ccbluex.liquidbounce.value.*
import net.minecraft.client.renderer.GlStateManager.enableTexture2D
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.player.EntityPlayer
import org.lwjgl.opengl.GL11.*
import org.lwjgl.util.vector.Vector3f
import java.awt.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

object ESP : Module("ESP", Category.RENDER, hideModule = false) {

    val mode by choices(
        "Mode",
        arrayOf("Box", "OtherBox", "WireFrame", "2D", "Real2D", "Outline", "Glow"), "Box"
    )

    val outlineWidth by floatValue("Outline-Width", 3f, 0.5f..5f) { mode == "Outline" }

    val wireframeWidth by floatValue("WireFrame-Width", 2f, 0.5f..5f) { mode == "WireFrame" }

    private val glowRenderScale by floatValue("Glow-Renderscale", 1f, 0.5f..2f) { mode == "Glow" }
    private val glowRadius by intValue("Glow-Radius", 4, 1..5) { mode == "Glow" }
    private val glowFade by intValue("Glow-Fade", 10, 0..30) { mode == "Glow" }
    private val glowTargetAlpha by floatValue("Glow-Target-Alpha", 0f, 0f..1f) { mode == "Glow" }

    private val espColorMode by choices("ESP-Color", arrayOf("Custom", "Rainbow"), "Custom")
    private val espColor = ColorSettingsInteger(this, "ESP", withAlpha = false)
    { espColorMode == "Custom" }.with(255, 255, 255)

    private val maxRenderDistance by object : IntegerValue("MaxRenderDistance", 100, 1..200) {
        override fun onUpdate(value: Int) {
            maxRenderDistanceSq = value.toDouble().pow(2.0)
        }
    }

    private val onLook by _boolean("OnLook", false)
    private val maxAngleDifference by floatValue("MaxAngleDifference", 90f, 5.0f..90f) { onLook }

    private val thruBlocks by _boolean("ThruBlocks", true)

    private var maxRenderDistanceSq = 0.0
        set(value) {
            field = if (value <= 0.0) maxRenderDistance.toDouble().pow(2.0) else value
        }

    private val colorTeam by _boolean("Team", false)
    private val bot by _boolean("Bots", true)

    var renderNameTags = true

    @EventTarget
    fun onRender3D(event: Render3DEvent) {
        val mvMatrix = WorldToScreen.getMatrix(GL_MODELVIEW_MATRIX)
        val projectionMatrix = WorldToScreen.getMatrix(GL_PROJECTION_MATRIX)
        val real2d = mode == "Real2D"

        if (real2d) {
            glPushAttrib(GL_ENABLE_BIT)
            glEnable(GL_BLEND)
            glDisable(GL_TEXTURE_2D)
            glDisable(GL_DEPTH_TEST)
            glMatrixMode(GL_PROJECTION)
            glPushMatrix()
            glLoadIdentity()
            glOrtho(0.0, mc.displayWidth.toDouble(), mc.displayHeight.toDouble(), 0.0, -1.0, 1.0)
            glMatrixMode(GL_MODELVIEW)
            glPushMatrix()
            glLoadIdentity()
            glDisable(GL_DEPTH_TEST)
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
            enableTexture2D()
            glDepthMask(true)
            glLineWidth(1f)
        }

        for (entity in mc.theWorld.loadedEntityList) {
            if (entity !is EntityLivingBase || !bot && isBot(entity)) continue
            if (isSelected(entity, false)) {

                val distanceSquared = mc.thePlayer.getDistanceSqToEntity(entity)

                if (onLook && !isLookingOnEntities(entity, maxAngleDifference.toDouble()))
                    continue

                if (!thruBlocks && !isEntityHeightVisible(entity)) continue

                if (distanceSquared <= maxRenderDistanceSq) {
                    val color = getColor(entity)

                    when (mode) {
                        "Box", "OtherBox" -> drawEntityBox(entity, color, mode != "OtherBox")
                        "2D" -> {
                            val (posX, posY, posZ) = entity.lastTickPos.lerpWith(
                                entity.currPos,
                                mc.timer.renderPartialTicks
                            ) - mc.renderManager.renderPos

                            draw2D(entity, posX, posY, posZ, color.rgb, Color.BLACK.rgb)
                        }

                        "Real2D" -> {
                            val (posX, posY, posZ) = entity.lastTickPos.lerpWith(
                                entity.currPos,
                                mc.timer.renderPartialTicks
                            ) - mc.renderManager.renderPos

                            val bb =
                                entity.hitBox.offset(-entity.posX, -entity.posY, -entity.posZ).offset(posX, posY, posZ)
                            val boxVertices = arrayOf(
                                doubleArrayOf(bb.minX, bb.minY, bb.minZ),
                                doubleArrayOf(bb.minX, bb.maxY, bb.minZ),
                                doubleArrayOf(bb.maxX, bb.maxY, bb.minZ),
                                doubleArrayOf(bb.maxX, bb.minY, bb.minZ),
                                doubleArrayOf(bb.minX, bb.minY, bb.maxZ),
                                doubleArrayOf(bb.minX, bb.maxY, bb.maxZ),
                                doubleArrayOf(bb.maxX, bb.maxY, bb.maxZ),
                                doubleArrayOf(bb.maxX, bb.minY, bb.maxZ)
                            )
                            var minX = Float.MAX_VALUE
                            var minY = Float.MAX_VALUE
                            var maxX = -1f
                            var maxY = -1f
                            for (boxVertex in boxVertices) {
                                val screenPos = WorldToScreen.worldToScreen(
                                    Vector3f(
                                        boxVertex[0].toFloat(),
                                        boxVertex[1].toFloat(),
                                        boxVertex[2].toFloat()
                                    ), mvMatrix, projectionMatrix, mc.displayWidth, mc.displayHeight
                                )
                                    ?: continue
                                minX = min(screenPos.x, minX)
                                minY = min(screenPos.y, minY)
                                maxX = max(screenPos.x, maxX)
                                maxY = max(screenPos.y, maxY)
                            }
                            if (minX > 0 || minY > 0 || maxX <= mc.displayWidth || maxY <= mc.displayWidth) {
                                glColor4f(color.red / 255f, color.green / 255f, color.blue / 255f, 1f)
                                glBegin(GL_LINE_LOOP)
                                glVertex2f(minX, minY)
                                glVertex2f(minX, maxY)
                                glVertex2f(maxX, maxY)
                                glVertex2f(maxX, minY)
                                glEnd()
                            }
                        }
                    }
                }
            }
        }

        if (real2d) {
            glColor4f(1f, 1f, 1f, 1f)
            glEnable(GL_DEPTH_TEST)
            glMatrixMode(GL_PROJECTION)
            glPopMatrix()
            glMatrixMode(GL_MODELVIEW)
            glPopMatrix()
            glPopAttrib()
        }
    }

    @EventTarget
    fun onRender2D(event: Render2DEvent) {
        if (mc.theWorld == null || mode != "Glow")
            return

        renderNameTags = false

        try {
            val entitiesGrouped = getEntitiesByColor(maxRenderDistanceSq)

            entitiesGrouped.forEach { (color, entities) ->
                GlowShader.startDraw(event.partialTicks, glowRenderScale)

                for (entity in entities) {
                    mc.renderManager.renderEntitySimple(entity, event.partialTicks)
                }

                GlowShader.stopDraw(color, glowRadius, glowFade, glowTargetAlpha)
            }
        } catch (ex: Exception) {
            LOGGER.error("An error occurred while rendering all entities for shader esp", ex)
        }

        renderNameTags = true
    }

    override val tag
        get() = mode

    private fun getEntitiesByColor(maxDistanceSquared: Double): Map<Color, List<EntityLivingBase>> {
        return getEntitiesInRange(maxDistanceSquared)
            .groupBy { getColor(it) }
    }

    private fun getEntitiesInRange(maxDistanceSquared: Double): List<EntityLivingBase> {
        val player = mc.thePlayer

        return mc.theWorld.loadedEntityList.asSequence()
            .filterIsInstance<EntityLivingBase>()
            .filterNot { isBot(it) && !bot }
            .filter { isSelected(it, false) }
            .filter { player.getDistanceSqToEntity(it) <= maxDistanceSquared }
            .filter { thruBlocks || isEntityHeightVisible(it) }
            .toList()
    }

    fun getColor(entity: Entity? = null): Color {
        run {
            if (entity != null && entity is EntityLivingBase) {
                if (entity.hurtTime > 0)
                    return Color.RED

                if (entity is EntityPlayer && entity.isClientFriend())
                    return Color.YELLOW

                if (colorTeam) {
                    val chars = (entity.displayName ?: return@run).formattedText.toCharArray()
                    var color = Int.MAX_VALUE

                    for (i in chars.indices) {
                        if (chars[i] != '§' || i + 1 >= chars.size) continue

                        val index = getColorIndex(chars[i + 1])
                        if (index < 0 || index > 15) continue

                        color = ColorUtils.hexColors[index]
                        break
                    }

                    return Color(color)
                }
            }
        }

        return if (espColorMode == "Rainbow") rainbow() else Color(espColor.color().rgb)
    }

    fun shouldRender(entity: EntityLivingBase): Boolean {
        return (bot || !isBot(entity))
    }

}