/*
 * GoldBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/bzym2/GoldBounce/
 */
package net.ccbluex.liquidbounce.features.module.modules.render

import net.ccbluex.liquidbounce.event.EventTarget
import net.ccbluex.liquidbounce.event.Render3DEvent
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.features.module.modules.misc.AntiBot.isBot
import net.ccbluex.liquidbounce.features.module.modules.misc.Teams
import net.ccbluex.liquidbounce.utils.attack.EntityUtils
import net.ccbluex.liquidbounce.utils.RotationUtils.isEntityHeightVisible
import net.ccbluex.liquidbounce.utils.extensions.*
import net.ccbluex.liquidbounce.utils.render.ColorUtils
import net.ccbluex.liquidbounce.utils.render.RenderUtils.glColor
import net.ccbluex.liquidbounce.value.*
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.util.Vec3
import org.lwjgl.opengl.GL11.*
import java.awt.Color
import kotlin.math.pow

object Tracers : Module("Tracers", Category.RENDER, hideModule = false) {

    private val colorMode by choices("Color", arrayOf("Custom", "DistanceColor", "Rainbow"), "Custom")
    private val colorRed by intValue("R", 0, 0..255) { colorMode == "Custom" }
    private val colorGreen by intValue("G", 160, 0..255) { colorMode == "Custom" }
    private val colorBlue by intValue("B", 255, 0..255) { colorMode == "Custom" }

    private val thickness by floatValue("Thickness", 2F, 1F..5F)

    private val maxRenderDistance by object : IntegerValue("MaxRenderDistance", 100, 1..200) {
        override fun onUpdate(value: Int) {
            maxRenderDistanceSq = value.toDouble().pow(2.0)
        }
    }

    private var maxRenderDistanceSq = 0.0
        set(value) {
            field = if (value <= 0.0) maxRenderDistance.toDouble().pow(2.0) else value
        }

    private val bot by _boolean("Bots", true)
    private val teams by _boolean("Teams", false)

    private val onLook by _boolean("OnLook", false)
    private val maxAngleDifference by floatValue("MaxAngleDifference", 90f, 5.0f..90f) { onLook }

    private val thruBlocks by _boolean("ThruBlocks", true)

    @EventTarget
    fun onRender3D(event: Render3DEvent) {
        val thePlayer = mc.thePlayer ?: return

        val originalViewBobbing = mc.gameSettings.viewBobbing

        // Temporarily disable view bobbing and re-apply camera transformation
        mc.gameSettings.viewBobbing = false
        mc.entityRenderer.setupCameraTransform(mc.timer.renderPartialTicks, 0)

        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
        glEnable(GL_BLEND)
        glEnable(GL_LINE_SMOOTH)
        glLineWidth(thickness)
        glDisable(GL_TEXTURE_2D)
        glDisable(GL_DEPTH_TEST)
        glDepthMask(false)

        glBegin(GL_LINES)

        for (entity in mc.theWorld.loadedEntityList) {
            val distanceSquared = thePlayer.getDistanceSqToEntity(entity)

            if (distanceSquared <= maxRenderDistanceSq) {
                if (onLook && !EntityUtils.isLookingOnEntities(entity, maxAngleDifference.toDouble())) continue
                if (entity !is EntityLivingBase || !bot && isBot(entity)) continue
                if (!thruBlocks && !isEntityHeightVisible(entity)) continue

                if (entity != thePlayer && EntityUtils.isSelected(entity, false)) {
                    val dist = (thePlayer.getDistanceToEntity(entity) * 2).toInt().coerceAtMost(255)

                    val colorMode = colorMode.lowercase()
                    val color = when {
                        entity is EntityPlayer && entity.isClientFriend() -> Color(250, 192, 61, 150)
                        teams && state && Teams.isInYourTeam(entity) -> Color(0, 162, 232)
                        colorMode == "custom" -> Color(colorRed, colorGreen, colorBlue, 150)
                        colorMode == "distancecolor" -> Color(255 - dist, dist, 0, 150)
                        colorMode == "rainbow" -> ColorUtils.rainbow()
                        else -> Color(255, 255, 255, 150)
                    }

                    drawTraces(entity, color)
                }
            }
        }

        glEnd()

        mc.gameSettings.viewBobbing = originalViewBobbing

        glEnable(GL_TEXTURE_2D)
        glDisable(GL_LINE_SMOOTH)
        glEnable(GL_DEPTH_TEST)
        glDepthMask(true)
        glDisable(GL_BLEND)
        glColor4f(1f, 1f, 1f, 1f)
    }

    private fun drawTraces(entity: Entity, color: Color) {
        val player = mc.thePlayer ?: return

        val (x, y, z) = entity.interpolatedPosition(entity.lastTickPos) - mc.renderManager.renderPos

        val yaw = (player.prevRotationYaw..player.rotationYaw).lerpWith(mc.timer.renderPartialTicks)
        val pitch = (player.prevRotationPitch..player.rotationPitch).lerpWith(mc.timer.renderPartialTicks)

        val eyeVector = Vec3(0.0, 0.0, 1.0).rotatePitch(-pitch.toRadians()).rotateYaw(-yaw.toRadians())

        glColor(color)

        glVertex3d(eyeVector.xCoord, player.getEyeHeight() + eyeVector.yCoord, eyeVector.zCoord)
        glVertex3d(x, y, z)
        glVertex3d(x, y, z)
        glVertex3d(x, y + entity.height, z)
    }
}
