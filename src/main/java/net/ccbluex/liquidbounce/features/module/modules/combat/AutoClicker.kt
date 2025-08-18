/*
 * GoldBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/bzym2/GoldBounce/
 */
package net.ccbluex.liquidbounce.features.module.modules.combat

import net.ccbluex.liquidbounce.event.AttackEvent
import net.ccbluex.liquidbounce.event.EventTarget
import net.ccbluex.liquidbounce.event.Render3DEvent
import net.ccbluex.liquidbounce.event.UpdateEvent
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.utils.attack.EntityUtils
import net.ccbluex.liquidbounce.utils.extensions.fixedSensitivityPitch
import net.ccbluex.liquidbounce.utils.extensions.fixedSensitivityYaw
import net.ccbluex.liquidbounce.utils.extensions.getDistanceToEntityBox
import net.ccbluex.liquidbounce.utils.extensions.isBlock
import net.ccbluex.liquidbounce.utils.misc.RandomUtils
import net.ccbluex.liquidbounce.utils.misc.RandomUtils.nextFloat
import net.ccbluex.liquidbounce.utils.timing.TimeUtils.randomClickDelay
import net.ccbluex.liquidbounce.value.IntegerValue
import net.ccbluex.liquidbounce.value._boolean
import net.ccbluex.liquidbounce.value.floatValue
import net.ccbluex.liquidbounce.value.intValue
import net.minecraft.client.settings.KeyBinding
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityLivingBase
import net.minecraft.item.EnumAction
import net.minecraft.item.ItemBlock
import kotlin.random.Random.Default.nextBoolean

object AutoClicker : Module("AutoClicker", Category.COMBAT, hideModule = false) {

    private val simulateDoubleClicking by _boolean("SimulateDoubleClicking", false)

    private val maxCPSValue: IntegerValue = object : IntegerValue("MaxCPS", 8, 1..20) {
        override fun onChange(oldValue: Int, newValue: Int) = newValue.coerceAtLeast(minCPS)
    }
    private val maxCPS by maxCPSValue

    private val minCPS by object : IntegerValue("MinCPS", 5, 1..20) {
        override fun onChange(oldValue: Int, newValue: Int) = newValue.coerceAtMost(maxCPS)

        override fun isSupported() = !maxCPSValue.isMinimal()
    }

    private val hurtTime by intValue("HurtTime", 10, 0..10) { left }

    private val right by _boolean("Right", true)
    private val left by _boolean("Left", true)
    private val jitter by _boolean("Jitter", false)
    private val block by _boolean("AutoBlock", false) { left }
    private val blockDelay by intValue("BlockDelay", 50, 0..100) { block }

    private val requiresNoInput by _boolean("RequiresNoInput", false) { left }
    private val maxAngleDifference by floatValue("MaxAngleDifference", 30f, 10f..180f) { left && requiresNoInput }
    private val range by floatValue("Range", 3f, 0.1f..5f) { left && requiresNoInput }

    private val onlyBlocks by _boolean("OnlyBlocks", true) { right }

    private var rightDelay = randomClickDelay(minCPS, maxCPS)
    private var rightLastSwing = 0L
    private var leftDelay = randomClickDelay(minCPS, maxCPS)
    private var leftLastSwing = 0L

    private var lastBlocking = 0L

    private val shouldAutoClick
        get() = mc.thePlayer.capabilities.isCreativeMode || !mc.objectMouseOver.typeOfHit.isBlock

    private var shouldJitter = false

    private var target: EntityLivingBase? = null

    override fun onDisable() {
        rightLastSwing = 0L
        leftLastSwing = 0L
        lastBlocking = 0L
        target = null
    }

    @EventTarget
    fun onAttack(event: AttackEvent) {
        if (!left) return
        val targetEntity = event.targetEntity as EntityLivingBase

        target = targetEntity
    }

    @EventTarget
    fun onRender3D(event: Render3DEvent) {
        mc.thePlayer?.let { thePlayer ->
            val time = System.currentTimeMillis()
            val doubleClick = if (simulateDoubleClicking) RandomUtils.nextInt(-1, 1) else 0

            if (block && thePlayer.swingProgress > 0 && !mc.gameSettings.keyBindUseItem.isKeyDown) {
                mc.gameSettings.keyBindUseItem.pressTime = 0
            }

            if (right && mc.gameSettings.keyBindUseItem.isKeyDown && time - rightLastSwing >= rightDelay) {
                if (!onlyBlocks || thePlayer.heldItem.item is ItemBlock) {
                    handleRightClick(time, doubleClick)
                }
            }

            if (requiresNoInput) {
                val nearbyEntity = getNearestEntityInRange() ?: return
                if (!EntityUtils.isLookingOnEntities(nearbyEntity, maxAngleDifference.toDouble())) return

                if (left && shouldAutoClick && time - leftLastSwing >= leftDelay) {
                    handleLeftClick(time, doubleClick)
                } else if (block && !mc.gameSettings.keyBindUseItem.isKeyDown && shouldAutoClick && shouldAutoRightClick() && mc.gameSettings.keyBindAttack.pressTime != 0) {
                    handleBlock(time)
                }
            } else {
                if (left && mc.gameSettings.keyBindAttack.isKeyDown && !mc.gameSettings.keyBindUseItem.isKeyDown && shouldAutoClick && time - leftLastSwing >= leftDelay) {
                    handleLeftClick(time, doubleClick)
                } else if (block && mc.gameSettings.keyBindAttack.isKeyDown && !mc.gameSettings.keyBindUseItem.isKeyDown && shouldAutoClick && shouldAutoRightClick() && mc.gameSettings.keyBindAttack.pressTime != 0) {
                    handleBlock(time)
                }
            }
        }
    }

    @EventTarget
    fun onTick(event: UpdateEvent) {
        mc.thePlayer?.let { thePlayer ->

            shouldJitter = !mc.objectMouseOver.typeOfHit.isBlock &&
                    (thePlayer.isSwingInProgress || mc.gameSettings.keyBindAttack.pressTime != 0)

            if (jitter && ((left && shouldAutoClick && shouldJitter)
                        || (right && !thePlayer.isUsingItem && mc.gameSettings.keyBindUseItem.isKeyDown
                        && ((onlyBlocks && thePlayer.heldItem.item is ItemBlock) || !onlyBlocks)))
            ) {

                if (nextBoolean()) thePlayer.fixedSensitivityYaw += nextFloat(-1F, 1F)
                if (nextBoolean()) thePlayer.fixedSensitivityPitch += nextFloat(-1F, 1F)
            }
        }
    }

    private fun getNearestEntityInRange(): Entity? {
        val player = mc.thePlayer ?: return null

        return mc.theWorld?.loadedEntityList?.asSequence()
            ?.filter { EntityUtils.isSelected(it, true) && player.getDistanceToEntityBox(it) <= range }
            ?.minByOrNull { player.getDistanceToEntityBox(it) }
    }

    private fun shouldAutoRightClick() = mc.thePlayer.heldItem?.itemUseAction in arrayOf(EnumAction.BLOCK)

    private fun handleLeftClick(time: Long, doubleClick: Int) {
        if (target != null && target!!.hurtTime > hurtTime) return

        repeat(1 + doubleClick) {
            KeyBinding.onTick(mc.gameSettings.keyBindAttack.keyCode)

            leftLastSwing = time
            leftDelay = randomClickDelay(minCPS, maxCPS)
        }
    }

    private fun handleRightClick(time: Long, doubleClick: Int) {
        repeat(1 + doubleClick) {
            KeyBinding.onTick(mc.gameSettings.keyBindUseItem.keyCode)

            rightLastSwing = time
            rightDelay = randomClickDelay(minCPS, maxCPS)
        }
    }

    private fun handleBlock(time: Long) {
        if (time - lastBlocking >= blockDelay) {
            KeyBinding.onTick(mc.gameSettings.keyBindUseItem.keyCode)

            lastBlocking = time
        }
    }
}
