/*
 * GoldBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/bzym2/GoldBounce/
 */
package net.ccbluex.liquidbounce.features.module.modules.movement

import net.ccbluex.liquidbounce.LiquidBounce
import net.ccbluex.liquidbounce.event.EventTarget
import net.ccbluex.liquidbounce.event.PacketEvent
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.features.module.modules.combat.KillAura
import net.ccbluex.liquidbounce.features.module.modules.combat.SuperKnockback
import net.ccbluex.liquidbounce.features.module.modules.world.scaffolds.Scaffold
import net.ccbluex.liquidbounce.utils.RotationUtils.activeSettings
import net.ccbluex.liquidbounce.utils.RotationUtils.currentRotation
import net.ccbluex.liquidbounce.utils.extensions.isMoving
import net.ccbluex.liquidbounce.utils.inventory.InventoryUtils.serverOpenInventory
import net.ccbluex.liquidbounce.value._boolean
import net.ccbluex.liquidbounce.value.choices
import net.ccbluex.liquidbounce.value.floatValue
import net.minecraft.network.play.client.C0BPacketEntityAction
import net.minecraft.potion.Potion
import net.minecraft.util.MovementInput
import kotlin.math.abs

object Sprint : Module("Sprint", Category.MOVEMENT, gameDetecting = false, hideModule = false) {
    val mode by choices("Mode", arrayOf("Legit", "Vanilla"), "Vanilla")

    val onlyOnSprintPress by _boolean("OnlyOnSprintPress", false)
    private val alwaysCorrect by _boolean("AlwaysCorrectSprint", false)

    val allDirections by _boolean("AllDirections", true) { mode == "Vanilla" }
    val jumpDirections by _boolean("JumpDirections", false) { mode == "Vanilla" && allDirections }

    private val allDirectionsLimitSpeed by floatValue("AllDirectionsLimitSpeed", 1f, 0.75f..1f)
    { mode == "Vanilla" && allDirections }
    private val allDirectionsLimitSpeedGround by _boolean("AllDirectionsLimitSpeedOnlyGround", true)
    { mode == "Vanilla" && allDirections }

    private val blindness by _boolean("Blindness", true) { mode == "Vanilla" }
    var usingItem = _boolean("UsingItem", false) { mode == "Vanilla" }
    private val inventory by _boolean("Inventory", false) { mode == "Vanilla" }
    private val food by _boolean("Food", true) { mode == "Vanilla" }

    private val checkServerSide by _boolean("CheckServerSide", false) { mode == "Vanilla" }
    private val checkServerSideGround by _boolean("CheckServerSideOnlyGround", false)
    { mode == "Vanilla" && checkServerSide }
    private val noPackets by _boolean("NoPackets", false) { mode == "Vanilla" }

    private var isSprinting = false

    override val tag
        get() = mode

    fun correctSprintState(movementInput: MovementInput, isUsingItem: Boolean) {
        val player = mc.thePlayer ?: return

        if (SuperKnockback.breakSprint()) {
            player.isSprinting = false
            return
        }

        if ((onlyOnSprintPress || !handleEvents()) && !player.isSprinting && !mc.gameSettings.keyBindSprint.isKeyDown && !SuperKnockback.startSprint() && !isSprinting)
            return

        if (handleEvents()) {
            if (!Scaffold.sprint && LiquidBounce.moduleManager.getModule("Scaffold")?.state == true) {
                player.isSprinting = false
                isSprinting = false
                return
            } else if (Scaffold.sprint && Scaffold.eagle == "Normal" && player.isMoving && player.onGround && Scaffold.eagleSneaking && Scaffold.eagleSprint) {
                player.isSprinting = true
                isSprinting = true
                return
            }
        }

        if (handleEvents() || alwaysCorrect) {
            player.isSprinting = !shouldStopSprinting(movementInput, isUsingItem)
            isSprinting = player.isSprinting
            if (player.isSprinting && allDirections && mode != "Legit") {
                if (!allDirectionsLimitSpeedGround || player.onGround) {
                    player.motionX *= allDirectionsLimitSpeed
                    player.motionZ *= allDirectionsLimitSpeed
                }
            }
        }
    }

    private fun shouldStopSprinting(movementInput: MovementInput, isUsingItem: Boolean): Boolean {
        val player = mc.thePlayer ?: return false

        val isLegitModeActive = mode == "Legit"

        val modifiedForward = if (currentRotation != null && activeSettings?.strict == true) {
            player.movementInput.moveForward
        } else {
            movementInput.moveForward
        }
        if (KillAura.target != null && !KillAura.keepSprint) {
            return true
        }
        if (!player.isMoving) {
            return true
        }

        if (player.isCollidedHorizontally) {
            return true
        }

        if ((blindness || isLegitModeActive) && player.isPotionActive(Potion.blindness) && !player.isSprinting) {
            return true
        }

        if ((food || isLegitModeActive) && !(player.foodStats.foodLevel > 6f || player.capabilities.allowFlying)) {
            return true
        }

        if ((usingItem.get() || isLegitModeActive) && !handleEvents() && isUsingItem) {
            return true
        }

        if ((inventory || isLegitModeActive) && serverOpenInventory) {
            return true
        }

        if (isLegitModeActive) {
            return modifiedForward < 0.8
        }

        if (allDirections) {
            return false
        }

        val threshold = if ((!usingItem.get() || handleEvents()) && isUsingItem) 0.2 else 0.8
        val playerForwardInput = player.movementInput.moveForward

        if (!checkServerSide) {
            return if (currentRotation != null) {
                abs(playerForwardInput) < threshold || playerForwardInput < 0 && modifiedForward < threshold
            } else {
                playerForwardInput < threshold
            }
        }

        if (checkServerSideGround && !player.onGround) {
            return currentRotation == null && modifiedForward < threshold
        }

        return modifiedForward < threshold
    }

    @EventTarget
    fun onPacket(event: PacketEvent) {
        if (mode == "Legit") {
            return
        }

        val packet = event.packet
        if (packet !is C0BPacketEntityAction || !noPackets || event.isCancelled) {
            return
        }
        if (packet.action == C0BPacketEntityAction.Action.STOP_SPRINTING || packet.action == C0BPacketEntityAction.Action.START_SPRINTING) {
            event.cancelEvent()
        }
    }
}
