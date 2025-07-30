/*
 * GoldBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/bzym2/GoldBounce/
 */
package net.ccbluex.liquidbounce.features.module.modules.render

import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.features.module.modules.render.Animations.animations
import net.ccbluex.liquidbounce.features.module.modules.render.Animations.defaultAnimation
import net.ccbluex.liquidbounce.utils.MinecraftInstance
import net.ccbluex.liquidbounce.utils.timing.MSTimer
import net.ccbluex.liquidbounce.value._boolean
import net.ccbluex.liquidbounce.value.choices
import net.ccbluex.liquidbounce.value.floatValue
import net.ccbluex.liquidbounce.value.intValue
import net.minecraft.client.entity.AbstractClientPlayer
import net.minecraft.client.renderer.GlStateManager.*
import net.minecraft.util.MathHelper
import org.lwjgl.opengl.GL11.glTranslated
import org.lwjgl.opengl.GL11.glTranslatef

/**
 * Animations module
 *
 * This module affects the blocking animation. It allows the user to customize the animation.
 * If you are looking forward to contribute to this module, please name your animation with a reasonable name. Do not name them after clients or yourself.
 * Please credit from where you got the animation from and make sure they are willing to contribute.
 * If they are not willing to contribute, please do not add the animation to this module.
 *
 * If you are looking for the animation classes, please look at the [Animation] class. It allows you to create your own animation.
 * After making your animation class, please add it to the [animations] array. It should automatically be added to the list and show up in the GUI.
 *
 * By default, the module uses the [OneSevenAnimation] animation. If you want to change the default animation, please change the [defaultAnimation] variable.
 * Default animations are even used when the module is disabled.
 *
 * If another variables from the renderItemInFirstPerson method are needed, please let me know or pass them by yourself.
 *
 * @author CCBlueX
 */
object Animations : Module("Animations", Category.RENDER, gameDetecting = false, hideModule = false) {

    // Default animation
    val defaultAnimation = OneSevenAnimation()

    private val animations = arrayOf(
        OneSevenAnimation(),
        OldPushdownAnimation(),
        NewPushdownAnimation(),
        OldAnimation(),
        HeliumAnimation(),
        ArgonAnimation(),
        CesiumAnimation(),
        SulfurAnimation(),
        SpinAnimation(),
        ModelSpinAnimation(),
        AstolfoAnimation(),
        EtherealAnimation(),
        ExhibitionAnimation(),
        InteriaAnimation(),
        LeakedAnimation(),
        MoonAnimation(),
        OldExhibitionAnimation(),
        OneDotSevenAnimation(),
        PunchAnimation(),
        PushAnimation(),
        SigmaAnimation(),
        SlideAnimation(),
        SmallAnimation(),
        SmoothAnimation(),
        SpinningAnimation(),
        StellaAnimation(),
        StylesAnimation(),
        SwaingAnimation(),
        SwankAnimation(),
        SwangAnimation(),
        SwingAnimation(),
        SwongAnimation()
    )

    private val animationMode by choices("Mode", animations.map { it.name }.sorted().toTypedArray(), "Pushdown")
    val oddSwing by _boolean("OddSwing", false)
    val swingSpeed by intValue("SwingSpeed", 15, 0..20)
    val cancelEquip by _boolean("CancelEquip", false) {animationMode == "Spin" }
    val scale by floatValue("Scale", 0f, -5f..5f) {animationMode == "Spin" }
    val spinSpeed by intValue("SpinSpeed", 72, 1..360) { animationMode == "ModelSpin" }
    val autoCenter by _boolean("AutoCenter", true) { animationMode == "ModelSpin" }
    val modelCenterX by floatValue("CenterX", 0f, -2f..2f) {animationMode == "ModelSpin" }
    val modelCenterY by floatValue("CenterY", -0.4f, -2f..2f) {animationMode == "ModelSpin" }
    val modelCenterZ by floatValue("CenterZ", 0f, -2f..2f) {animationMode == "ModelSpin" }
    val handItemScale by floatValue("ItemScale", 0f, -5f..5f)
    val handX by floatValue("X", 0f, -5f..5f)
    val handY by floatValue("Y", 0f, -5f..5f)
    val handPosX by floatValue("PositionRotationX", 0f, -50f..50f)
    val handPosY by floatValue("PositionRotationY", 0f, -50f..50f)
    val handPosZ by floatValue("PositionRotationZ", 0f, -50f..50f)

    fun getAnimation() = animations.firstOrNull { it.name == animationMode }
}

/**
 * Sword Animation
 *
 * This class allows you to create your own animation.
 * It transforms the item in the hand and the known functions from Mojang are directly accessible as well.
 *
 * @author CCBlueX
 */
abstract class Animation(val name: String) : MinecraftInstance() {
    abstract fun transform(f1: Float, f: Float, clientPlayer: AbstractClientPlayer)

    /**
     * Transforms the block in the hand
     *
     * @author Mojang
     */
    protected fun doBlockTransformations() {
        translate(-0.5f, 0.2f, 0f)
        rotate(30f, 0f, 1f, 0f)
        rotate(-80f, 1f, 0f, 0f)
        rotate(60f, 0f, 1f, 0f)
    }

    /**
     * Transforms the item in the hand
     *
     * @author Mojang
     */
    protected fun transformFirstPersonItem(equipProgress: Float, swingProgress: Float) {
        translate(0.56f, -0.52f, -0.71999997f)
        translate(0f, equipProgress * -0.6f, 0f)
        rotate(45f, 0f, 1f, 0f)
        val f = MathHelper.sin(swingProgress * swingProgress * 3.1415927f)
        val f1 = MathHelper.sin(MathHelper.sqrt_float(swingProgress) * 3.1415927f)
        rotate(f * -20f, 0f, 1f, 0f)
        rotate(f1 * -20f, 0f, 0f, 1f)
        rotate(f1 * -80f, 1f, 0f, 0f)
        scale(0.4f, 0.4f, 0.4f)
    }

}

/**
 * OneSeven animation (default). Similar to the 1.7 blocking animation.
 *
 * @author CCBlueX
 */
class OneSevenAnimation : Animation("OneSeven") {
    override fun transform(f1: Float, f: Float, clientPlayer: AbstractClientPlayer) {
        transformFirstPersonItem(f, f1)
        doBlockTransformations()
        translate(-0.5f, 0.2f, 0f)
    }

}

class OldAnimation : Animation("Old") {
    override fun transform(f1: Float, f: Float, clientPlayer: AbstractClientPlayer) {
        transformFirstPersonItem(f, f1)
        doBlockTransformations()
    }
}
class ModelSpinAnimation : Animation("ModelSpin") {
    private var rotationAngle = 0f
    private val rotationTimer = MSTimer()

    override fun transform(f1: Float, f: Float, clientPlayer: AbstractClientPlayer) {
        val (centerX, centerY, centerZ) = if (Animations.autoCenter) {
            Triple(0.0, -0.4, 0.0)
        } else {
            Triple(
                Animations.modelCenterX.toDouble(),
                Animations.modelCenterY.toDouble(),
                Animations.modelCenterZ.toDouble()
            )
        }

        // 动态计算角度增量（每秒Animations.spinSpeed度）
        val anglePerTick = Animations.spinSpeed * 0.05f // 50ms间隔

        // 中心点变换流程
        glTranslated(centerX, centerY, centerZ)
        rotate(rotationAngle, 0f, 1f, 0f)
        glTranslated(-centerX, -centerY, -centerZ)

        // 基础动画
        transformFirstPersonItem(f, f1)
        doBlockTransformations()

        // 角度更新逻辑
        if (rotationTimer.hasTimePassed(50L)) {
            rotationAngle += anglePerTick
            rotationAngle %= 360f
            rotationTimer.reset()
        }
    }
}


/**
 * Spin animation
 */
class SpinAnimation : Animation("Spin") {
    private var delay = 0f
    private val rotateTimer = MSTimer()
    private var lastUpdateTime = System.currentTimeMillis()

    override fun transform(f1: Float, f: Float, clientPlayer: AbstractClientPlayer) {
        // 应用位置变换
        glTranslated(
            Animations.handPosX.toDouble(),
            Animations.handPosY.toDouble(),
            Animations.handPosZ.toDouble()
        )

        // 旋转动画逻辑
        rotate(delay, 0f, 0f, -0.1f)

        // 装备动画控制
        if (Animations.cancelEquip) {
            transformFirstPersonItem(0f, 0f)
        } else {
            transformFirstPersonItem(f / 1.5f, 0f)
        }

        // 计时器更新
        val currentTime = System.currentTimeMillis()
        val elapsedTime = currentTime - lastUpdateTime
        if (rotateTimer.hasTimePassed(1L)) {
            delay += (elapsedTime * 360.0 / 850.0).toFloat()
            rotateTimer.reset()
        }
        lastUpdateTime = currentTime

        // 延迟值循环
        if (delay > 360f) delay = 0f

        // 执行方块变形
        doBlockTransformations()

        // 应用缩放
        scale(Animations.scale + 1, Animations.scale + 1, Animations.scale + 1)
    }
}

/**
 * Old Pushdown animation.
 */
class OldPushdownAnimation : Animation("OldPushdown") {

    /**
     * @author CzechHek. Taken from Animations script.
     */
    override fun transform(f1: Float, f: Float, clientPlayer: AbstractClientPlayer) {
        translate(0.56, -0.52, -0.5)
        translate(0.0, -f.toDouble() * 0.3, 0.0)
        rotate(45.5f, 0f, 1f, 0f)
        val var3 = MathHelper.sin(0f)
        val var4 = MathHelper.sin(0f)
        rotate((var3 * -20f), 0f, 1f, 0f)
        rotate((var4 * -20f), 0f, 0f, 1f)
        rotate((var4 * -80f), 1f, 0f, 0f)
        scale(0.32, 0.32, 0.32)
        val var15 = MathHelper.sin((MathHelper.sqrt_float(f1) * 3.1415927f))
        rotate((-var15 * 125 / 1.75f), 3.95f, 0.35f, 8f)
        rotate(-var15 * 35, 0f, (var15 / 100f), -10f)
        translate(-1.0, 0.6, -0.0)
        rotate(30f, 0f, 1f, 0f)
        rotate(-80f, 1f, 0f, 0f)
        rotate(60f, 0f, 1f, 0f)
        glTranslated(1.05, 0.35, 0.4)
        glTranslatef(-1f, 0f, 0f)
    }

}

/**
 * New Pushdown animation.
 * @author EclipsesDev
 *
 * Taken from NightX Moon Animation (I made it smoother here xd)
 */
class NewPushdownAnimation : Animation("NewPushdown") {

    override fun transform(f1: Float, f: Float, clientPlayer: AbstractClientPlayer) {
        val x = Animations.handPosX - 0.08
        val y = Animations.handPosY + 0.12
        val z = Animations.handPosZ.toDouble()
        translate(x, y, z)

        val var9 = MathHelper.sin(MathHelper.sqrt_float(f1) * 3.1415927f)
        translate(0.0, 0.0, 0.0)

        transformFirstPersonItem(f / 1.4f, 0.0f)

        rotate(-var9 * 65.0f / 2.0f, var9 / 2.0f, 1.0f, 4.0f)
        rotate(-var9 * 60.0f, 1.0f, var9 / 3.0f, -0.0f)
        doBlockTransformations()

        scale(1.0, 1.0, 1.0)
    }

}

/**
 * Helium animation.
 * @author 182exe
 */
class HeliumAnimation : Animation("Helium") {
    override fun transform(f1: Float, f: Float, clientPlayer: AbstractClientPlayer) {
        transformFirstPersonItem(f, 0.0f)
        val c0 = MathHelper.sin(f1 * f * 3.1415927f)
        val c1 = MathHelper.sin(MathHelper.sqrt_float(f1) * 3.1415927f)
        rotate(-c1 * 55.0f, 30.0f, c0 / 5.0f, 0.0f)
        doBlockTransformations()
    }
}

/**
 * Argon animation.
 * @author 182exe
 */
class ArgonAnimation : Animation("Argon") {
    override fun transform(f1: Float, f: Float, clientPlayer: AbstractClientPlayer) {
        transformFirstPersonItem(f / 2.5f, f1)
        val c2 = MathHelper.sin(MathHelper.sqrt_float(f1) * 3.1415927f)
        val c3 = MathHelper.cos(MathHelper.sqrt_float(f) * 3.1415927f)
        rotate(c3 * 50.0f / 10.0f, -c2, -0.0f, 100.0f)
        rotate(c2 * 50.0f, 200.0f, -c2 / 2.0f, -0.0f)
        translate(0.0, 0.3, 0.0)
        doBlockTransformations()
    }
}

/**
 * Cesium animation.
 * @author 182exe
 */
class CesiumAnimation : Animation("Cesium") {
    override fun transform(f1: Float, f: Float, clientPlayer: AbstractClientPlayer) {
        val c4 = MathHelper.sin(MathHelper.sqrt_float(f1) * 3.1415927f)
        transformFirstPersonItem(f, 0.0f)
        rotate(-c4 * 10.0f / 20.0f, c4 / 2.0f, 0.0f, 4.0f)
        rotate(-c4 * 30.0f, 0.0f, c4 / 3.0f, 0.0f)
        rotate(-c4 * 10.0f, 1.0f, c4 / 10.0f, 0.0f)
        translate(0.0, 0.2, 0.0)
        doBlockTransformations()
    }
}

/**
 * Sulfur animation.
 * @author 182exe
 */
class SulfurAnimation : Animation("Sulfur") {
    override fun transform(f1: Float, f: Float, clientPlayer: AbstractClientPlayer) {
        val c5 = MathHelper.sin(MathHelper.sqrt_float(f1) * 3.1415927f)
        val c6 = MathHelper.cos(MathHelper.sqrt_float(f1) * 3.1415927f)
        transformFirstPersonItem(f, 0.0f)
        rotate(-c5 * 30.0f, c5 / 10.0f, c6 / 10.0f, 0.0f)
        translate(c5 / 1.5, 0.2, 0.0)
        doBlockTransformations()
    }
}

// Below are the newly added animations

class PushAnimation : Animation("Push") {
    override fun transform(f1: Float, f: Float, clientPlayer: AbstractClientPlayer) {
        val swingProgress = f1
        val var9 = MathHelper.sin(swingProgress * swingProgress * 3.1415927f)
        val var151 = MathHelper.sin(MathHelper.sqrt_float(swingProgress) * 3.1415927f)
        translate(0.56f, -0.52f, -0.71999997f)
        translate(0.0f, swingProgress * -0.6f, 0.0f)
        rotate(45.0f, 0.0f, 1.0f, 0.0f)
        rotate(var9 * -20.0f, 0.0f, 1.0f, 0.0f)
        rotate(var151 * -20.0f, 0.0f, 0.0f, 1.0f)
        scale(0.4f, 0.4f, 0.4f)
        doBlockTransformations()
    }
}

class PunchAnimation : Animation("Punch") {
    override fun transform(f1: Float, f: Float, clientPlayer: AbstractClientPlayer) {
        val var9 = MathHelper.sin(f1 * f1 * 3.1415927f)
        transformFirstPersonItem(f, 0.0f)
        doBlockTransformations()
        translate(0.1f, 0.2f, 0.3f)
        rotate(-var9 * 30.0f, -5.0f, 0.0f, 9.0f)
        rotate(-var9 * 10.0f, 1.0f, -0.4f, -0.5f)
    }
}

class StellaAnimation : Animation("Stella") {
    override fun transform(f1: Float, f: Float, clientPlayer: AbstractClientPlayer) {
        transformFirstPersonItem(-0.1f, f1)
        translate(-0.5f, 0.4f, -0.2f)
        rotate(30.0f, 0.0f, 1.0f, 0.0f)
        rotate(-70.0f, 1.0f, 0.0f, 0.0f)
        rotate(40.0f, 0.0f, 1.0f, 0.0f)
    }
}

class MoonAnimation : Animation("Moon") {
    override fun transform(f1: Float, f: Float, clientPlayer: AbstractClientPlayer) {
        val var8 = MathHelper.sin(MathHelper.sqrt_float(f1) * 3.1415927f)
        transformFirstPersonItem(0.0f, 0.0f)
        translate(-0.08f, 0.12f, 0.0f)
        rotate(-var8 * 65.0f / 2.0f, var8 / 2.0f, 1.0f, 4.0f)
        rotate(-var8 * 60.0f, 1.0f, var8 / 3.0f, -0.0f)
        doBlockTransformations()
    }
}

class LeakedAnimation : Animation("Leaked") {
    override fun transform(f1: Float, f: Float, clientPlayer: AbstractClientPlayer) {
        val `var` = MathHelper.sin(MathHelper.sqrt_float(f1) * 3.1415927f)
        transformFirstPersonItem(0.0f, 0.0f)
        translate(0.08f, 0.02f, 0.0f)
        doBlockTransformations()
        rotate(-`var` * 41f, 1.1f, 0.8f, -0.3f)
    }
}

class AstolfoAnimation : Animation("Astolfo") {
    override fun transform(f1: Float, f: Float, clientPlayer: AbstractClientPlayer) {
        val var7 = MathHelper.sin(MathHelper.sqrt_float(f1) * 3.1415927f)
        transformFirstPersonItem(0.0f, 0.0f)
        translate(-0.08f, 0.12f, 0.0f)
        rotate(-var7 * 58.0f / 2.0f, var7 / 2.0f, 1.0f, 0.5f)
        rotate(-var7 * 43.0f, 1.0f, var7 / 3.0f, -0.0f)
        doBlockTransformations()
    }
}

class SmallAnimation : Animation("Small") {
    override fun transform(f1: Float, f: Float, clientPlayer: AbstractClientPlayer) {
        translate(-0.01f, 0.03f, -0.24f)
        transformFirstPersonItem(0.0f, f1)
        doBlockTransformations()
    }
}

class OneDotSevenAnimation : Animation("1.7") {
    override fun transform(f1: Float, f: Float, clientPlayer: AbstractClientPlayer) {
        transformFirstPersonItem(f, f1)
        doBlockTransformations()
    }
}

class StylesAnimation : Animation("Styles") {
    override fun transform(f1: Float, f: Float, clientPlayer: AbstractClientPlayer) {
        val var11 = MathHelper.sin(MathHelper.sqrt_float(f1) * 3.1415927f)
        transformFirstPersonItem(f, 0.0f)
        doBlockTransformations()
        translate(-0.05f, 0.2f, 0.0f)
        rotate(-var11 * 70.0f / 2.0f, -8.0f, -0.0f, 9.0f)
        rotate(-var11 * 70.0f, 1.0f, -0.4f, -0.0f)
    }
}

class SwankAnimation : Animation("Swank") {
    override fun transform(f1: Float, f: Float, clientPlayer: AbstractClientPlayer) {
        val var151 = MathHelper.sin(MathHelper.sqrt_float(f1) * 3.1415927f)
        transformFirstPersonItem(f / 2.0f, f1)
        rotate(var151 * 30.0f, -var151, -0.0f, 9.0f)
        rotate(var151 * 40.0f, 1.0f, -var151, -0.0f)
        translate(-0.5f, 0.4f, 0.0f)
        rotate(30.0f, 0.0f, 1.0f, 0.0f)
        rotate(-80.0f, 1.0f, 0.0f, 0.0f)
        rotate(60.0f, 0.0f, 1.0f, 0.0f)
    }
}

class SwangAnimation : Animation("Swang") {
    override fun transform(f1: Float, f: Float, clientPlayer: AbstractClientPlayer) {
        val var152 = MathHelper.sin(MathHelper.sqrt_float(f1) * 3.1415927f)
        transformFirstPersonItem(f / 2.0f, f1)
        rotate(var152 * 30.0f / 2.0f, -var152, -0.0f, 9.0f)
        rotate(var152 * 40.0f, 1.0f, -var152 / 2.0f, -0.0f)
        translate(-0.5f, 0.4f, 0.0f)
        rotate(30.0f, 0.0f, 1.0f, 0.0f)
        rotate(-80.0f, 1.0f, 0.0f, 0.0f)
        rotate(60.0f, 0.0f, 1.0f, 0.0f)
    }
}

class SwongAnimation : Animation("Swong") {
    override fun transform(f1: Float, f: Float, clientPlayer: AbstractClientPlayer) {
        val var153 = MathHelper.sin(f1 * f1 * 3.1415927f)
        transformFirstPersonItem(f / 2.0f, 0.0f)
        rotate(-var153 * 40.0f / 2.0f, var153 / 2.0f, -0.0f, 9.0f)
        rotate(-var153 * 30.0f, 1.0f, var153 / 2.0f, -0.0f)
        translate(-0.5f, 0.4f, 0.0f)
        rotate(30.0f, 0.0f, 1.0f, 0.0f)
        rotate(-80.0f, 1.0f, 0.0f, 0.0f)
        rotate(60.0f, 0.0f, 1.0f, 0.0f)
    }
}

class SwaingAnimation : Animation("Swaing") {
    override fun transform(f1: Float, f: Float, clientPlayer: AbstractClientPlayer) {
        val var154 = MathHelper.sin(f1 * f1 * 3.1415927f)
        transformFirstPersonItem(f / 2.0f, -0.2f)
        rotate(-var154 / 19.0f, var154 / 20.0f, -0.0f, 9.0f)
        rotate(-var154 * 30.0f, 10.0f, var154 / 50.0f, 0.0f)
        translate(-0.5f, 0.4f, 0.0f)
        rotate(30.0f, 0.0f, 1.0f, 0.0f)
        rotate(-80.0f, 1.0f, 0.0f, 0.0f)
        rotate(60.0f, 0.0f, 1.0f, 0.0f)
    }
}

class SwingAnimation : Animation("Swing") {
    override fun transform(f1: Float, f: Float, clientPlayer: AbstractClientPlayer) {
        transformFirstPersonItem(f / 2.0f, f1)
        translate(-0.5f, 0.4f, 0.0f)
        rotate(30.0f, 0.0f, 1.0f, 0.0f)
        rotate(-80.0f, 1.0f, 0.0f, 0.0f)
        rotate(60.0f, 0.0f, 1.0f, 0.0f)
    }
}

class SmoothAnimation : Animation("Smooth") {
    override fun transform(f1: Float, f: Float, clientPlayer: AbstractClientPlayer) {
        val var9 = MathHelper.sin(f1 * f1 * 3.1415927f)
        transformFirstPersonItem(f / 1.5f, 0.0f)
        doBlockTransformations()
        translate(-0.05f, 0.3f, 0.3f)
        rotate(-var9 * 140.0f, 8.0f, 0.0f, 8.0f)
        rotate(var9 * 90.0f, 8.0f, 0.0f, 8.0f)
    }
}

class SigmaAnimation : Animation("Sigma") {
    override fun transform(f1: Float, f: Float, clientPlayer: AbstractClientPlayer) {
        val var9 = MathHelper.sin(f1 * f1 * 3.1415927f)
        transformFirstPersonItem(f * 0.5f, 0f)
        rotate(-var9 * 55 / 2.0f, -8.0f, -0.0f, 9.0f)
        rotate(-var9 * 45f, 1.0f, var9 / 2, -0.0f)
        doBlockTransformations()
        glTranslated(1.2, 0.3, 0.5)
        glTranslatef(-1f, if (mc.thePlayer.isSneaking) -0.1f else -0.2f, 0.2f)
    }
}

class SlideAnimation : Animation("Slide") {
    override fun transform(f1: Float, f: Float, clientPlayer: AbstractClientPlayer) {
        val var9 = MathHelper.sin(f1 * f1 * 3.1415927f)
        transformFirstPersonItem(f, 0.0f)
        doBlockTransformations()
        translate(-0.4f, 0.3f, 0.0f)
        rotate(-var9 * 35.0f, -8.0f, -0.0f, 9.0f)
        rotate(-var9 * 70.0f, 1.0f, -0.4f, -0.0f)
        glTranslatef(-0.05f, if (mc.thePlayer.isSneaking) -0.2f else 0.0f, 0.1f)
    }
}

class InteriaAnimation : Animation("Interia") {
    override fun transform(f1: Float, f: Float, clientPlayer: AbstractClientPlayer) {
        transformFirstPersonItem(0.05f, f1)
        translate(-0.5f, 0.5f, 0.0f)
        rotate(30.0f, 0.0f, 1.0f, 0.0f)
        rotate(-80.0f, 1.0f, 0.0f, 0.0f)
        rotate(60.0f, 0.0f, 1.0f, 0.0f)
    }
}

class EtherealAnimation : Animation("Ethereal") {
    override fun transform(f1: Float, f: Float, clientPlayer: AbstractClientPlayer) {
        val var9 = MathHelper.sin(f1 * f1 * 3.1415927f)
        transformFirstPersonItem(f, 0.0f)
        doBlockTransformations()
        translate(-0.05f, 0.2f, 0.2f)
        rotate(-var9 * 70.0f / 2.0f, -8.0f, -0.0f, 9.0f)
        rotate(-var9 * 70.0f, 1.0f, -0.4f, -0.0f)
    }
}

class OldExhibitionAnimation : Animation("OldExhibition") {
    override fun transform(f1: Float, f: Float, clientPlayer: AbstractClientPlayer) {
        val var9 = MathHelper.sin(f1 * f1 * 3.1415927f)
        glTranslated(-0.04, 0.13, 0.0)
        transformFirstPersonItem(f / 2.5f, 0.0f)
        rotate(-var9 * 40.0f / 2.0f, var9 / 2.0f, 1.0f, 4.0f)
        rotate(-var9 * 30.0f, 1.0f, var9 / 3.0f, -0.0f)
        doBlockTransformations()
    }
}

class ExhibitionAnimation : Animation("Exhibition") {
    override fun transform(f1: Float, f: Float, clientPlayer: AbstractClientPlayer) {
        val var151 = MathHelper.sin(MathHelper.sqrt_float(f1) * 3.1415927f)
        glTranslated(-0.03, (var151 * 0.062f).toDouble(), 0.0)
        glTranslated(0.025, 0.09615, 0.0)
        transformFirstPersonItem(f / 3f, 0.0f)
        rotate(-var151 * 9f, -var151 / 20f, -var151 / 20f, 1f)
        rotate(-var151 * 55f, 1.2f, var151 / 4f, 0.36f)
        if (mc.thePlayer.isSneaking) {
            translate(-0.05, -0.05, 0.0)
        }
        doBlockTransformations()
    }
}

class SpinningAnimation : Animation("Spinning") {
    private var spin = 0f
    private var lastTime: Long = 0

    override fun transform(f1: Float, f: Float, clientPlayer: AbstractClientPlayer) {
        if (lastTime == 0L) {
            lastTime = System.currentTimeMillis()
        }
        val delta = System.currentTimeMillis() - lastTime
        lastTime = System.currentTimeMillis()
        spin += delta * 0.3f // Control spin speed
        if (spin > 360f) {
            spin -= 360f
        }
        glTranslated(-0.04, 0.1, 0.0)
        transformFirstPersonItem(f / 2.5f, 0.0f)
        rotate(-90f, 1f, 0f, 0.2f)
        rotate(spin, 0f, -1f, 0f)
    }
}