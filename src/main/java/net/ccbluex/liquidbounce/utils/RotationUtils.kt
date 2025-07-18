/* GoldBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/bzym2/GoldBounce/
 */
package net.ccbluex.liquidbounce.utils

import net.ccbluex.liquidbounce.bzym.OpenSimplex2S
import net.ccbluex.liquidbounce.event.*
import net.ccbluex.liquidbounce.features.module.modules.combat.FastBow
import net.ccbluex.liquidbounce.features.module.modules.render.Rotations
import net.ccbluex.liquidbounce.features.module.modules.settings.Debugger
import net.ccbluex.liquidbounce.utils.RaycastUtils.raycastEntity
import net.ccbluex.liquidbounce.utils.RotationUtils.MAX_CAPTURE_TICKS
import net.ccbluex.liquidbounce.utils.RotationUtils.currentRotation
import net.ccbluex.liquidbounce.utils.extensions.*
import net.ccbluex.liquidbounce.utils.inventory.InventoryUtils
import net.ccbluex.liquidbounce.utils.misc.RandomUtils.nextDouble
import net.ccbluex.liquidbounce.utils.misc.RandomUtils.nextFloat
import net.ccbluex.liquidbounce.utils.skid.lbnew.MathUtil.clamp
import net.ccbluex.liquidbounce.utils.timing.WaitTickUtils
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityLivingBase
import net.minecraft.network.play.client.C03PacketPlayer
import net.minecraft.realms.RealmsMth.clamp
import net.minecraft.realms.RealmsMth.wrapDegrees
import net.minecraft.tileentity.TileEntity
import net.minecraft.util.*
import java.util.*
import javax.vecmath.Vector2f
import kotlin.math.*


object RotationUtils : MinecraftInstance(), Listenable {
    @Suppress("NOTHING_TO_INLINE")
    inline fun Vec3.scale(factor: Double) = Vec3(
        xCoord * factor,
        yCoord * factor,
        zCoord * factor
    )

    /**
     * Our final rotation point, which [currentRotation] follows.
     */
    var targetRotation: Rotation? = null

    /**
     * The current rotation that is responsible for aiming at objects, synchronizing movement, etc.
     */
    var currentRotation: Rotation? = null

    /**
     * The last rotation that the server has received.
     */
    var serverRotation: Rotation
        get() = lastRotations[0]
        set(value) {
            lastRotations = lastRotations.toMutableList().apply { set(0, value) }
        }

    private const val MAX_CAPTURE_TICKS = 3

    /**
     * A list that stores the last rotations captured from 0 up to [MAX_CAPTURE_TICKS] previous ticks.
     */
    var lastRotations = MutableList(MAX_CAPTURE_TICKS) { Rotation.ZERO }
        set(value) {
            val updatedList = MutableList(lastRotations.size) { Rotation.ZERO }

            for (tick in 0 until MAX_CAPTURE_TICKS) {
                updatedList[tick] = if (tick == 0) value[0] else field[tick - 1]
            }

            field = updatedList
        }

    /**
     * The currently in-use rotation settings, which are used to determine how the rotations will move.
     */
    var activeSettings: RotationSettings? = null

    var resetTicks = 0

    /**
     * Face block
     *
     * @param blockPos target block
     */
    fun faceBlock(blockPos: BlockPos?, throughWalls: Boolean = true): VecRotation? {
        val world = mc.theWorld ?: return null
        val player = mc.thePlayer ?: return null

        if (blockPos == null)
            return null

        val eyesPos = player.eyes
        val startPos = Vec3(blockPos)

        var visibleVec: VecRotation? = null
        var invisibleVec: VecRotation? = null

        for (x in 0.0..1.0) {
            for (y in 0.0..1.0) {
                for (z in 0.0..1.0) {
                    val block = blockPos.getBlock() ?: return null

                    val posVec = startPos.add(block.lerpWith(x, y, z))

                    val dist = eyesPos.distanceTo(posVec)

                    val (diffX, diffY, diffZ) = posVec - eyesPos
                    val diffXZ = sqrt(diffX * diffX + diffZ * diffZ)

                    val rotation = Rotation(
                        MathHelper.wrapAngleTo180_float(atan2(diffZ, diffX).toDegreesF() - 90f),
                        MathHelper.wrapAngleTo180_float(-atan2(diffY, diffXZ).toDegreesF())
                    ).fixedSensitivity()

                    val rotationVector = getVectorForRotation(rotation)
                    val vector = eyesPos + (rotationVector * dist)

                    val currentVec = VecRotation(posVec, rotation)
                    val raycast = world.rayTraceBlocks(eyesPos, vector, false, true, false)

                    val currentRotation = currentRotation ?: player.rotation

                    if (raycast != null && raycast.blockPos == blockPos) {
                        if (visibleVec == null || rotationDifference(
                                currentVec.rotation,
                                currentRotation
                            ) < rotationDifference(visibleVec.rotation, currentRotation)
                        ) {
                            visibleVec = currentVec
                        }
                    } else if (throughWalls) {
                        val invisibleRaycast = performRaytrace(blockPos, rotation) ?: continue

                        if (invisibleRaycast.blockPos != blockPos) {
                            continue
                        }

                        if (invisibleVec == null || rotationDifference(
                                currentVec.rotation,
                                currentRotation
                            ) < rotationDifference(invisibleVec.rotation, currentRotation)
                        ) {
                            invisibleVec = currentVec
                        }
                    }
                }
            }
        }

        return visibleVec ?: invisibleVec
    }

    /**
     * Algorithm模式的核心搜索函数
     */
    private fun algorithmSearch(targetBB: AxisAlignedBB): Rotation? {
        val eyesPos = mc.thePlayer.eyes
        var bestRotation: Rotation? = null
        var minDistance = Double.MAX_VALUE

        // 使用多层扫描策略
        for (yRatio in listOf(0.4, 0.5, 0.6)) { // 身体不同高度
            for (xRatio in listOf(0.2, 0.5, 0.8)) { // 水平扫描
                for (zRatio in listOf(0.2, 0.5, 0.8)) {
                    val scanPos = targetBB.lerpWith(xRatio, yRatio, zRatio)
                    val rotation = toRotation(scanPos, true).fixedSensitivity()

                    // 计算实际命中点
                    val hitVec = calculateHitVec(rotation, eyesPos) ?: continue

                    // 评估路径有效性
                    val distance = hitVec.distanceTo(eyesPos)
                    if (distance < minDistance) {
                        minDistance = distance
                        bestRotation = rotation
                    }
                }
            }
        }
        return bestRotation
    }

    /**
     * 默认搜索逻辑（原searchCenter的核心部分）
     */
    private fun defaultSearch(targetBB: AxisAlignedBB): Rotation {
        val eyesPos = mc.thePlayer.eyes
        var closestRot = Rotation.ZERO
        var closestDist = Double.MAX_VALUE

        for (y in 0.0..1.0 step 0.1) {
            val pos = targetBB.lerpWith(0.5, y, 0.5)
            val rot = toRotation(pos, true).fixedSensitivity()
            val dist = pos.distanceTo(eyesPos)

            if (dist < closestDist) {
                closestDist = dist
                closestRot = rot
            }
        }
        return closestRot
    }

    /**
     * 扩展函数：计算旋转到目标点的距离
     */
    fun Rotation.distanceTo(target: Vec3): Double {
        val directionVec = getVectorForRotation(this)
        val eyesPos = mc.thePlayer.eyes
        val projectedPoint =
            eyesPos.addVector(directionVec.xCoord * 1000, directionVec.yCoord * 1000, directionVec.zCoord * 1000)

        return target.distanceTo(projectedPoint)
    }

    /**
     * 计算命中向量（带碰撞检测）
     */
    private fun calculateHitVec(rotation: Rotation, startPos: Vec3 = mc.thePlayer.eyes): Vec3? {
        val endPos = startPos.add(getVectorForRotation(rotation).scale(128.0))

        return mc.theWorld.rayTraceBlocks(
            startPos,
            endPos,
            false,
            true,
            false
        )?.hitVec
    }

    // 在Rotation类添加以下扩展方法
    fun Rotation.withGCD(sensitivity: Float = mc.gameSettings.mouseSensitivity): Rotation {
        val f = sensitivity * 0.6F + 0.2F
        val gcd = f * f * f * 8.0F
        return Rotation(
            (this.yaw - (this.yaw % gcd)),
            (this.pitch - (this.pitch % gcd))
        )
    }

    private fun algorithmRotate(target: Vec3, settings: RotationSettings): Rotation {
        val queue = PriorityQueue<Pair<Rotation, Double>>(compareBy { it.second })
        val visited = mutableSetOf<Pair<Float, Float>>()

        val startRotation = currentRotation ?: mc.thePlayer.rotation
        queue.add(startRotation to startRotation.distanceTo(target))

        repeat(settings.algorithmSearchSteps.get()) {
            val (current, _) = queue.poll() ?: return@repeat

            for (yawStep in -1..1) {
                for (pitchStep in -1..1) {
                    val newYaw = current.yaw + yawStep * settings.algorithmPrecision.get()
                    val newPitch = current.pitch + pitchStep * settings.algorithmPrecision.get()
                    val newRot = Rotation(newYaw, newPitch)

                    if (visited.add(newYaw.toFloat() to newPitch.toFloat())) {
                        val hitVec = calculateHitVec(newRot)
                        val distance = hitVec?.distanceTo(target) ?: Double.MAX_VALUE
                        queue.add(newRot to distance)
                    }
                }
            }
        }

        return queue.minByOrNull { it.second }?.first ?: startRotation
    }

    private fun tryRunPerlinNoise(settings: RotationSettings, addMilleseconds: Long): Float {
        val time = System.currentTimeMillis() / 1000.0 * settings.noiseSpeed.get()

        if (settings.improve.get() == "XY") {
            return OpenSimplex2S.noise3_ImproveXY(
                mc.thePlayer.uniqueID.mostSignificantBits,
                time + addMilleseconds,
                time + addMilleseconds,
                time + addMilleseconds
            ) * settings.noiseScale.get()
        } else if (settings.improve.get() == "XZ") {
            return OpenSimplex2S.noise3_ImproveXZ(
                mc.thePlayer.uniqueID.mostSignificantBits,
                time + addMilleseconds,
                time + addMilleseconds,
                time + addMilleseconds
            ) * settings.noiseScale.get()
        } else {
            return 0F
        }
    }

    private fun noiseRotate(baseRotation: Rotation, settings: RotationSettings): Rotation {
        val time = System.currentTimeMillis() / 1000.0 * settings.noiseSpeed.get()
        val maxStep = settings.maxTurnSpeed.get() * 0.05f
        val yawNoise = tryRunPerlinNoise(settings, settings.noiseAdditionYaw.get().toLong())
        val pitchNoise = tryRunPerlinNoise(settings, settings.noiseAdditionPitch.get().toLong())
        return baseRotation + Rotation(
            yawNoise.coerceIn(-maxStep, maxStep),
            pitchNoise.coerceIn(-maxStep, maxStep)
        )
    }

    fun getRotationBlock(pos: BlockPos, predict: Float): Rotation {
        val from =
            net.ccbluex.liquidbounce.utils.client.MinecraftInstance.Companion.mc.thePlayer.getPositionEyes(predict)
        val to = Vec3d(pos.x + 0.5, pos.y + 0.5, pos.z + 0.5)
        val diff = to.subtract(Vec3d(from))

        val yaw = MathHelper.wrapAngleTo180_float(
            Math.toDegrees(atan2(diff.z, diff.x)).toFloat() - 90f
        )
        val pitch = MathHelper.wrapAngleTo180_float(
            (-Math.toDegrees(atan2(diff.y, sqrt(diff.x * diff.x + diff.z * diff.z)))).toFloat()
        )
        return Rotation(yaw, pitch)
    }

    /**
     * Face trajectory of arrow by default, can be used for calculating other trajectories (eggs, snowballs)
     * by specifying `gravity` and `velocity` parameters
     *
     * @param target      your enemy
     * @param predict     predict new enemy position
     * @param predictSize predict size of predict
     * @param gravity     how much gravity does the projectile have, arrow by default
     * @param velocity    with what velocity will the projectile be released, velocity for arrow is calculated when null
     */
    fun faceTrajectory(
        target: Entity,
        predict: Boolean,
        predictSize: Float,
        gravity: Float = 0.05f,
        velocity: Float? = null,
    ): Rotation {
        val player = mc.thePlayer

        val posX =
            target.posX + (if (predict) (target.posX - target.prevPosX) * predictSize else .0) - (player.posX + if (predict) player.posX - player.prevPosX else .0)
        val posY =
            target.entityBoundingBox.minY + (if (predict) (target.entityBoundingBox.minY - target.prevPosY) * predictSize else .0) + target.eyeHeight - 0.15 - (player.entityBoundingBox.minY + (if (predict) player.posY - player.prevPosY else .0)) - player.getEyeHeight()
        val posZ =
            target.posZ + (if (predict) (target.posZ - target.prevPosZ) * predictSize else .0) - (player.posZ + if (predict) player.posZ - player.prevPosZ else .0)
        val posSqrt = sqrt(posX * posX + posZ * posZ)

        var finalVelocity = velocity

        if (finalVelocity == null) {
            finalVelocity = if (FastBow.handleEvents()) 1f else player.itemInUseDuration / 20f
            finalVelocity = ((finalVelocity * finalVelocity + finalVelocity * 2) / 3).coerceAtMost(1f)
        }

        val gravityModifier = 0.12f * gravity

        return Rotation(
            atan2(posZ, posX).toDegreesF() - 90f,
            -atan(
                (finalVelocity * finalVelocity - sqrt(
                    finalVelocity * finalVelocity * finalVelocity * finalVelocity - gravityModifier * (gravityModifier * posSqrt * posSqrt + 2 * posY * finalVelocity * finalVelocity)
                )) / (gravityModifier * posSqrt)
            ).toDegreesF()
        )
    }

    /**
     * Translate vec to rotation
     *
     * @param vec     target vec
     * @param predict predict new location of your body
     * @return rotation
     */
    fun toRotation(vec: Vec3, predict: Boolean = false, fromEntity: Entity = mc.thePlayer): Rotation {
        val eyesPos = fromEntity.eyes
        if (predict) eyesPos.addVector(fromEntity.motionX, fromEntity.motionY, fromEntity.motionZ)

        val (diffX, diffY, diffZ) = vec - eyesPos
        return Rotation(
            MathHelper.wrapAngleTo180_float(
                atan2(diffZ, diffX).toDegreesF() - 90f
            ), MathHelper.wrapAngleTo180_float(
                -atan2(diffY, sqrt(diffX * diffX + diffZ * diffZ)).toDegreesF()
            )
        )
    }

    /**
     * Search good center
     *
     * @param bb                entity box to search rotation for
     * @param outborder         outborder option
     * @param random            random option
     * @param predict           predict, offsets rotation by player's motion
     * @param lookRange         look range
     * @param attackRange       attack range, rotations in attack range will be prioritized
     * @param throughWallsRange through walls range,
     * @return center
     */
    fun searchCenter(
        bb: AxisAlignedBB,
        outborder: Boolean,
        randomization: RandomizationSettings? = null,
        predict: Boolean,
        lookRange: Float,
        attackRange: Float,
        throughWallsRange: Float = 0f,
        bodyPoints: List<String> = listOf("Head", "Feet"),
        horizontalSearch: ClosedFloatingPointRange<Float> = 0f..1f,
        settings: RotationSettings
    ): Rotation? {
        val scanRange = lookRange.coerceAtLeast(attackRange)

        val max = BodyPoint.fromString(bodyPoints[0]).range.endInclusive
        val min = BodyPoint.fromString(bodyPoints[1]).range.start

        if (outborder) {
            val vec3 = bb.lerpWith(nextDouble(0.5, 1.3), nextDouble(0.9, 1.3), nextDouble(0.5, 1.3))

            return toRotation(vec3, predict).fixedSensitivity()
        }

        val eyes = mc.thePlayer.eyes

        var currRotation = Rotation.ZERO.plus(currentRotation ?: mc.thePlayer.rotation)

        var attackRotation: Pair<Rotation, Float>? = null
        var lookRotation: Pair<Rotation, Float>? = null

        randomization?.takeIf { it.randomize }?.run {
            val yawMovement = angleDifference(currRotation.yaw, serverRotation.yaw).sign.takeIf { it != 0f }
                ?: arrayOf(-1f, 1f).random()
            val pitchMovement = angleDifference(currRotation.pitch, serverRotation.pitch).sign.takeIf { it != 0f }
                ?: arrayOf(-1f, 1f).random()

            currRotation.yaw += if (Math.random() > yawRandomizationChance.random()) {
                yawRandomizationRange.random() * yawMovement
            } else 0f
            currRotation.pitch += if (Math.random() > pitchRandomizationChance.random()) {
                pitchRandomizationRange.random() * pitchMovement
            } else 0f

            currRotation.fixedSensitivity()
        }

        val (hMin, hMax) = horizontalSearch.start.toDouble() to horizontalSearch.endInclusive.toDouble()

        for (x in hMin..hMax) {
            for (y in min..max) {
                for (z in hMin..hMax) {
                    val vec = bb.lerpWith(x, y, z)

                    val rotation = toRotation(vec, predict).fixedSensitivity()

                    // Calculate actual hit vec after applying fixed sensitivity to rotation
                    val gcdVec = bb.calculateIntercept(
                        eyes,
                        eyes + getVectorForRotation(rotation) * scanRange.toDouble()
                    )?.hitVec ?: continue

                    val distance = eyes.distanceTo(gcdVec)

                    // Check if vec is in range
                    // Skip if a rotation that is in attack range was already found and the vec is out of attack range
                    if (distance > scanRange || (attackRotation != null && distance > attackRange))
                        continue

                    // Check if vec is reachable through walls
                    if (!isVisible(gcdVec) && distance > throughWallsRange)
                        continue

                    val rotationWithDiff = rotation to rotationDifference(rotation, currRotation)

                    if (distance <= attackRange) {
                        if (attackRotation == null || rotationWithDiff.second < attackRotation.second)
                            attackRotation = rotationWithDiff
                    } else {
                        if (lookRotation == null || rotationWithDiff.second < lookRotation.second)
                            lookRotation = rotationWithDiff
                    }
                }
            }
        }
        return when {
            settings.rotationMode == "Algorithm" -> {
                algorithmSearch(bb)?.withGCD()
            }

            settings.rotationMode == "Noise" -> {
                val base = defaultSearch(bb)?.withGCD()
                base?.let { noiseRotate(it, settings) }?.fixedSensitivity()
            }

            else -> {
                attackRotation?.first ?: lookRotation?.first ?: run {
                    val vec = getNearestPointBB(eyes, bb)
                    val dist = eyes.distanceTo(vec)

                    if (dist <= scanRange && (dist <= throughWallsRange || isVisible(vec)))
                        toRotation(vec, predict)
                    else null
                }
            }
        }
    }

    fun searchCenter(
        bb: AxisAlignedBB, outborder: Boolean, randomization: RandomizationSettings? = null, predict: Boolean,
        lookRange: Float, attackRange: Float, throughWallsRange: Float = 0f,
        bodyPoints: List<String> = listOf("Head", "Feet"), horizontalSearch: ClosedFloatingPointRange<Float> = 0f..1f
    ): Rotation? {
        val scanRange = lookRange.coerceAtLeast(attackRange)

        val max = BodyPoint.fromString(bodyPoints[0]).range.endInclusive
        val min = BodyPoint.fromString(bodyPoints[1]).range.start

        if (outborder) {
            val vec3 = bb.lerpWith(nextDouble(0.5, 1.3), nextDouble(0.9, 1.3), nextDouble(0.5, 1.3))

            return toRotation(vec3, predict).fixedSensitivity()
        }

        val eyes = mc.thePlayer.eyes

        var currRotation = Rotation.ZERO.plus(currentRotation ?: mc.thePlayer.rotation)

        var attackRotation: Pair<Rotation, Float>? = null
        var lookRotation: Pair<Rotation, Float>? = null

        randomization?.takeIf { it.randomize }?.run {
            val yawMovement = angleDifference(currRotation.yaw, serverRotation.yaw).sign.takeIf { it != 0f }
                ?: arrayOf(-1f, 1f).random()
            val pitchMovement = angleDifference(currRotation.pitch, serverRotation.pitch).sign.takeIf { it != 0f }
                ?: arrayOf(-1f, 1f).random()

            currRotation.yaw += if (Math.random() > yawRandomizationChance.random()) {
                yawRandomizationRange.random() * yawMovement
            } else 0f
            currRotation.pitch += if (Math.random() > pitchRandomizationChance.random()) {
                pitchRandomizationRange.random() * pitchMovement
            } else 0f

            currRotation.fixedSensitivity()
        }

        val (hMin, hMax) = horizontalSearch.start.toDouble() to horizontalSearch.endInclusive.toDouble()

        for (x in hMin..hMax) {
            for (y in min..max) {
                for (z in hMin..hMax) {
                    val vec = bb.lerpWith(x, y, z)

                    val rotation = toRotation(vec, predict).fixedSensitivity()

                    // Calculate actual hit vec after applying fixed sensitivity to rotation
                    val gcdVec = bb.calculateIntercept(
                        eyes,
                        eyes + getVectorForRotation(rotation) * scanRange.toDouble()
                    )?.hitVec ?: continue

                    val distance = eyes.distanceTo(gcdVec)

                    // Check if vec is in range
                    // Skip if a rotation that is in attack range was already found and the vec is out of attack range
                    if (distance > scanRange || (attackRotation != null && distance > attackRange))
                        continue

                    // Check if vec is reachable through walls
                    if (!isVisible(gcdVec) && distance > throughWallsRange)
                        continue

                    val rotationWithDiff = rotation to rotationDifference(rotation, currRotation)

                    if (distance <= attackRange) {
                        if (attackRotation == null || rotationWithDiff.second < attackRotation.second)
                            attackRotation = rotationWithDiff
                    } else {
                        if (lookRotation == null || rotationWithDiff.second < lookRotation.second)
                            lookRotation = rotationWithDiff
                    }
                }
            }
        }
        return attackRotation?.first ?: lookRotation?.first ?: run {
            val vec = getNearestPointBB(eyes, bb)
            val dist = eyes.distanceTo(vec)

            if (dist <= scanRange && (dist <= throughWallsRange || isVisible(vec)))
                toRotation(vec, predict)
            else null
        }

    }

    /**
     * Calculate difference between the client rotation and your entity
     *
     * @param entity your entity
     * @return difference between rotation
     */
    fun rotationDifference(entity: Entity) =
        rotationDifference(toRotation(entity.hitBox.center, true), mc.thePlayer.rotation)

    /**
     * Calculate difference between two rotations
     *
     * @param a rotation
     * @param b rotation
     * @return difference between rotation
     */
    fun rotationDifference(a: Rotation, b: Rotation = serverRotation) =
        hypot(angleDifference(a.yaw, b.yaw), a.pitch - b.pitch)

    private fun limitAngleChange(
        currentRotation: Rotation,
        targetRotation: Rotation,
        settings: RotationSettings
    ): Rotation {
        val (hSpeed, vSpeed) = if (settings.instant) {
            180f to 180f
        } else settings.horizontalSpeed.random() to settings.verticalSpeed.random()

        return performAngleChange(
            currentRotation,
            targetRotation,
            hSpeed,
            vSpeed,
            !settings.instant && settings.legitimize,
            settings.minRotationDifference,
        )
    }

    fun performAngleChange(
        currentRotation: Rotation,
        targetRotation: Rotation,
        hSpeed: Float,
        vSpeed: Float = hSpeed,
        legitimize: Boolean,
        minRotationDiff: Float,
    ): Rotation {
        var (yawDiff, pitchDiff) = angleDifferences(targetRotation, currentRotation)

        val rotationDifference = hypot(yawDiff, pitchDiff)

        if (rotationDifference <= getFixedAngleDelta())
            return currentRotation.plusDiff(targetRotation)

        val isShortStopActive = WaitTickUtils.hasScheduled(this)

        if (isShortStopActive || activeSettings?.shouldPerformShortStop() == true) {
            // Use the tick scheduling to our advantage as we can check if short stop is still active.
            if (!isShortStopActive) {
                WaitTickUtils.schedule(activeSettings?.shortStopDuration?.random()?.plus(1) ?: 0, this)
            }

            activeSettings?.resetSimulateShortStopData()

            yawDiff = 0f
            pitchDiff = 0f
        }

        var (straightLineYaw, straightLinePitch) =
            abs(yawDiff safeDiv rotationDifference) * hSpeed to abs(pitchDiff safeDiv rotationDifference) * vSpeed

        straightLineYaw = yawDiff.coerceIn(-straightLineYaw, straightLineYaw)
        straightLinePitch = pitchDiff.coerceIn(-straightLinePitch, straightLinePitch)

        val rotationWithGCD = Rotation(straightLineYaw, straightLinePitch).fixedSensitivity()

        if (abs(rotationWithGCD.yaw) <= nextFloat(min(minRotationDiff, getFixedAngleDelta()), minRotationDiff)) {
            straightLineYaw = 0f
        }

        if (abs(rotationWithGCD.pitch) < nextFloat(min(minRotationDiff, getFixedAngleDelta()), minRotationDiff)) {
            straightLinePitch = 0f
        }

        if (legitimize) {
            applySlowDown(straightLineYaw, true) {
                straightLineYaw = it
            }

            applySlowDown(straightLinePitch, false) {
                straightLinePitch = it
            }
        }

        return currentRotation.plus(Rotation(straightLineYaw, straightLinePitch))
    }

    private fun applySlowDown(diff: Float, yaw: Boolean, action: (Float) -> Unit) {
        if (diff == 0f) {
            action(diff)
            return
        }

        val lastTick1 = angleDifferences(serverRotation, lastRotations[1]).let { diffs ->
            if (yaw) diffs.x else diffs.y
        }

        val diffAbs = abs(diff)

        val range = when {
            diffAbs <= 3f -> 0.4f..0.8f + (0.2f * (1 - diffAbs / 3f)).coerceIn(0f, 1f)
            diffAbs > 50f -> 0.2f..0.55f
            // This modifies how fast the rotations will slow down to switch direction.
            // The less/higher the progression, the slower/faster the slow-down.
            // This when applied with pitch automatically performs a curve, but we are not looking for too much slow-down.
            // Have a curve applied while still trying to focus on target. (0.4f - 0.5f) seems to work fine.
            // Could be an option if needed.
            diff.sign != lastTick1.sign && lastTick1.sign != 0f && diff.sign != 0f -> 0.4f..0.5f
            else -> 0.1f..0.4f
        }

        action((lastTick1..diff).lerpWith(range.random()))
    }

    /**
     * Calculate difference between two angle points
     *
     * @param a angle point
     * @param b angle point
     * @return difference between angle points
     */
    fun angleDifference(a: Float, b: Float) = MathHelper.wrapAngleTo180_float(a - b)

    /**
     * Returns a 2-parameter vector with the calculated angle differences between [target] and [current] rotations
     */
    fun angleDifferences(target: Rotation, current: Rotation) =
        Vector2f(angleDifference(target.yaw, current.yaw), target.pitch - current.pitch)

    /**
     * Calculate rotation to vector
     *
     * @param [yaw] [pitch] your rotation
     * @return target vector
     */
    fun getVectorForRotation(yaw: Float, pitch: Float): Vec3 {
        val yawRad = yaw.toRadians()
        val pitchRad = pitch.toRadians()

        val f = MathHelper.cos(-yawRad - PI.toFloat())
        val f1 = MathHelper.sin(-yawRad - PI.toFloat())
        val f2 = -MathHelper.cos(-pitchRad)
        val f3 = MathHelper.sin(-pitchRad)

        return Vec3((f1 * f2).toDouble(), f3.toDouble(), (f * f2).toDouble())
    }

    fun getVectorForRotation(rotation: Rotation) = getVectorForRotation(rotation.yaw, rotation.pitch)

    /**
     * Returns the inverted yaw angle.
     *
     * @param yaw The original yaw angle in degrees.
     * @return The yaw angle inverted by 180 degrees.
     */
    fun invertYaw(yaw: Float): Float {
        return (yaw + 180) % 360
    }

    /**
     * Allows you to check if your crosshair is over your target entity
     *
     * @param targetEntity       your target entity
     * @param blockReachDistance your reach
     * @return if crosshair is over target
     */
    fun isFaced(targetEntity: Entity, blockReachDistance: Double) =
        raycastEntity(blockReachDistance) { entity: Entity -> targetEntity == entity } != null

    /**
     * Allows you to check if your crosshair is over your target entity
     *
     * @param targetEntity       your target entity
     * @param blockReachDistance your reach
     * @return if crosshair is over target
     */
    fun isRotationFaced(targetEntity: Entity, blockReachDistance: Double, rotation: Rotation) = raycastEntity(
        blockReachDistance,
        rotation.yaw,
        rotation.pitch
    ) { entity: Entity -> targetEntity == entity } != null

    /**
     * Allows you to check if your enemy is behind a wall
     */
    fun isVisible(vec3: Vec3) = mc.theWorld.rayTraceBlocks(mc.thePlayer.eyes, vec3) == null

    fun isEntityHeightVisible(entity: Entity) = arrayOf(
        entity.hitBox.center.withY(entity.hitBox.maxY),
        entity.hitBox.center.withY(entity.hitBox.minY)
    ).any { isVisible(it) }

    fun isEntityHeightVisible(entity: TileEntity) = arrayOf(
        entity.renderBoundingBox.center.withY(entity.renderBoundingBox.maxY),
        entity.renderBoundingBox.center.withY(entity.renderBoundingBox.minY)
    ).any { isVisible(it) }

    /**
     * Set your target rotation
     *
     * @param rotation your target rotation
     */
    fun generateSinusoidalRotation(currentRotation: Rotation, amplitude: Float, frequency: Float): Rotation {
        val deltaTime = System.currentTimeMillis() / 1000.0
        val sinusoidalYaw = currentRotation.yaw + amplitude * sin(deltaTime * frequency).toFloat()
        val sinusoidalPitch = currentRotation.pitch + amplitude * cos(deltaTime * frequency).toFloat()

        return Rotation(sinusoidalYaw, sinusoidalPitch).fixedSensitivity()
    }

    fun faceMultipleTrajectories(
        targets: List<Entity>,
        predict: Boolean,
        predictSize: Float,
        gravity: Float = 0.05f,
        velocity: Float? = null,
    ): Rotation? {
        if (targets.isEmpty()) return null
        val randomTarget = targets.random() // 随机选择一个目标
        return faceTrajectory(randomTarget, predict, predictSize, gravity, velocity)
    }

    fun generateCurvedRotationSequence(
        startRotation: Rotation,
        endRotation: Rotation,
        steps: Int
    ): List<Rotation> {
        return List(steps) { step ->
            val t = step.toFloat() / steps
            val curveFactor = sin(t * Math.PI).toFloat() // 使用正弦曲线生成非线性效果
            Rotation(
                startRotation.yaw + (endRotation.yaw - startRotation.yaw) * curveFactor,
                startRotation.pitch + (endRotation.pitch - startRotation.pitch) * curveFactor
            ).fixedSensitivity()
        }
    }

    fun getRotations(posX: Double, posY: Double, posZ: Double): Rotation {
        val player = mc.thePlayer
        val x = posX - player.posX
        val y = posY - (player.posY + player.getEyeHeight())
        val z = posZ - player.posZ
        val dist = MathHelper.sqrt_double(x * x + z * z)
        val yaw = (atan2(z, x) * 180.0 / Math.PI - 90).toFloat()
        val pitch = (-(atan2(y, dist.toDouble()) * 180.0 / Math.PI)).toFloat()
        return Rotation(yaw, pitch)
    }

    fun simulateMouseSensitiveRotation(
        currentRotation: Rotation,
        targetRotation: Rotation,
        sensitivity: Float,
        maxStep: Float = 10f
    ): Rotation {
        val yawDiff = RotationUtils.angleDifference(targetRotation.yaw, currentRotation.yaw)
        val pitchDiff = targetRotation.pitch - currentRotation.pitch

        val yawStep = (yawDiff * sensitivity).coerceIn(-maxStep, maxStep)
        val pitchStep = (pitchDiff * sensitivity).coerceIn(-maxStep, maxStep)

        return currentRotation.plus(Rotation(yawStep, pitchStep)).fixedSensitivity()
    }

    fun getRotationsEntity(entity: EntityLivingBase): Rotation {
        return RotationUtils.getRotations(entity.posX, entity.posY + entity.getEyeHeight() - 0.4, entity.posZ);
    }

    fun simulateInertialRotation(
        currentRotation: Rotation,
        targetRotation: Rotation,
        inertiaFactor: Float = 0.9f
    ): Rotation {
        val yawDiff = RotationUtils.angleDifference(targetRotation.yaw, currentRotation.yaw)
        val pitchDiff = targetRotation.pitch - currentRotation.pitch

        val yawStep = yawDiff * (1 - inertiaFactor)
        val pitchStep = pitchDiff * (1 - inertiaFactor)

        return currentRotation.plus(Rotation(yawStep, pitchStep)).fixedSensitivity()
    }

    fun simulateDynamicPath(
        currentRotation: Rotation,
        targetRotation: Rotation,
        pathPoints: Int = 5
    ): List<Rotation> {
        return List(pathPoints) { step ->
            val t = step.toFloat() / pathPoints
            Rotation(
                currentRotation.yaw + (targetRotation.yaw - currentRotation.yaw) * t,
                currentRotation.pitch + (targetRotation.pitch - currentRotation.pitch) * t
            ).fixedSensitivity()
        }
    }

    fun simulateRandomJitter(
        currentRotation: Rotation,
        jitterAmount: Float = 1f
    ): Rotation {
        val randomYawJitter = (-jitterAmount..jitterAmount).random()
        val randomPitchJitter = (-jitterAmount..jitterAmount).random()

        return currentRotation.plus(Rotation(randomYawJitter, randomPitchJitter)).fixedSensitivity()
    }

    fun simulateSmoothTracking(
        currentRotation: Rotation,
        targetRotation: Rotation,
        stepSize: Float = 5f
    ): Rotation {
        val yawDiff = RotationUtils.angleDifference(targetRotation.yaw, currentRotation.yaw)
        val pitchDiff = targetRotation.pitch - currentRotation.pitch

        val yawStep = yawDiff.coerceIn(-stepSize, stepSize)
        val pitchStep = pitchDiff.coerceIn(-stepSize, stepSize)

        return currentRotation.plus(Rotation(yawStep, pitchStep)).fixedSensitivity()
    }

    fun simulateRealisticRotation(
        currentRotation: Rotation,
        targetRotation: Rotation,
        sensitivity: Float = 0.8f,
        inertiaFactor: Float = 0.9f,
        jitterAmount: Float = 1f,
        stepSize: Float = 5f
    ): Rotation {
        val sensitiveRotation = simulateMouseSensitiveRotation(currentRotation, targetRotation, sensitivity)
        val inertialRotation = simulateInertialRotation(sensitiveRotation, targetRotation, inertiaFactor)
        val jitteredRotation = simulateRandomJitter(inertialRotation, jitterAmount)
        return simulateSmoothTracking(jitteredRotation, targetRotation, stepSize)
    }

    fun simulateMicroAdjustments(
        baseRotation: Rotation,
        targetRotation: Rotation,
        adjustmentRange: Float = 2f
    ): Rotation {
        val yawDiff = angleDifference(targetRotation.yaw, baseRotation.yaw)
        val pitchDiff = targetRotation.pitch - baseRotation.pitch
        val yawAdjust = yawDiff.coerceIn(-adjustmentRange, adjustmentRange)
        val pitchAdjust = pitchDiff.coerceIn(-adjustmentRange, adjustmentRange)

        return baseRotation.plus(Rotation(yawAdjust, pitchAdjust))
    }
    // From Phantom Injection
    fun shortestYaw(from: Float, to: Float): Float {
        return from + limitYaw180(to - from)
    }

    fun limitYaw180(yaw: Float): Float {
        return wrapDegrees(yaw)
    }

    fun limitPitch90(pitch: Float): Float {
        return clamp(pitch, -90F, 90F)
    }

    fun aimToPoint(from: Vec3?, to: Vec3): Vec2f {
        val diff = to.subtract(from)
        val distance = hypot(diff.xCoord, diff.zCoord)
        val yaw = Math.toDegrees(atan2(diff.zCoord, diff.xCoord)).toFloat() - 90.0f
        val pitch = -Math.toDegrees(atan2(diff.yCoord, distance)).toFloat()
        return Vec2f(yaw, pitch)
    }
    // End of Phantom Injection
    fun setTargetRotation(rotation: Rotation, options: RotationSettings, ticks: Int = options.resetTicks) {
        if (rotation.yaw.isNaN() || rotation.pitch.isNaN() || rotation.pitch > 90 || rotation.pitch < -90) return
        if (!options.prioritizeRequest && activeSettings?.prioritizeRequest == true) return

        if (!options.applyServerSide) {
            currentRotation?.let {
                mc.thePlayer.rotationYaw = it.yaw
                mc.thePlayer.rotationPitch = it.pitch
            }
            resetRotation()
        }

        // 获取 rotateMode
        val rotateMode =
            if (options is RotationSettingsWithRotationModes) options.rotationMode else options.getMode().get()
        if (Debugger.RotationDebug) {
            chat("RotationMode $rotateMode")
        }
        val current = currentRotation ?: rotation

        targetRotation = when (rotateMode) {
            "MouseSensitive" -> simulateMouseSensitiveRotation(current, rotation, sensitivity = 0.8f)
            "Inertial" -> simulateInertialRotation(current, rotation, inertiaFactor = 0.9f)
            "MicroAdjustment" -> {
                val baseRotation = rotation.fixedSensitivity()
                simulateMicroAdjustments(baseRotation, rotation, adjustmentRange = 1.5f)
            }

            "SmoothTracking" -> simulateSmoothTracking(current, rotation, stepSize = 5f)
            "RandomJitter" -> simulateRandomJitter(current, jitterAmount = 0.8f)
            "Realistic" -> simulateRealisticRotation(current, rotation)
            "Sinusoidal" -> generateSinusoidalRotation(current, amplitude = 3f, frequency = 2f)
            "Default" -> rotation
            else -> rotation
        } as Rotation?

        if (Debugger.RotationDebug) {
            chat("Target Rotation: ${targetRotation?.yaw}, ${targetRotation?.pitch}")
        }

        resetTicks = if (!options.applyServerSide || !options.resetTicksValue.isSupported()) 1 else ticks
        activeSettings = options

        if (options.immediate) {
            update()
        }
    }


    fun resetRotation() {
        resetTicks = 0
        currentRotation?.let { (yaw, _) ->
            mc.thePlayer?.let {
                it.rotationYaw = yaw + angleDifference(it.rotationYaw, yaw)
                syncRotations()
            }
        }
        targetRotation = null
        currentRotation = null
        activeSettings = null
    }

    /**
     * Returns the smallest angle difference possible with a specific sensitivity ("gcd")
     */
    fun getFixedAngleDelta(sensitivity: Float = mc.gameSettings.mouseSensitivity) =
        (sensitivity * 0.6f + 0.2f).pow(3) * 1.2f

    /**
     * Returns angle that is legitimately accomplishable with player's current sensitivity
     */
    fun getFixedSensitivityAngle(targetAngle: Float, startAngle: Float = 0f, gcd: Float = getFixedAngleDelta()) =
        startAngle + ((targetAngle - startAngle) / gcd).roundToInt() * gcd

    /**
     * Creates a raytrace even when the target [blockPos] is not visible
     */
    fun performRaytrace(
        blockPos: BlockPos,
        rotation: Rotation,
        reach: Float = mc.playerController.blockReachDistance,
    ): MovingObjectPosition? {
        val world = mc.theWorld ?: return null
        val player = mc.thePlayer ?: return null

        val eyes = player.eyes

        return blockPos.getBlock()?.collisionRayTrace(
            world,
            blockPos,
            eyes,
            eyes + (getVectorForRotation(rotation) * reach.toDouble())
        )
    }

    fun performRayTrace(blockPos: BlockPos, vec: Vec3, eyes: Vec3 = mc.thePlayer.eyes) =
        mc.theWorld?.let { blockPos.getBlock()?.collisionRayTrace(it, blockPos, eyes, vec) }

    fun syncRotations() {
        val player = mc.thePlayer ?: return

        player.prevRotationYaw = player.rotationYaw
        player.prevRotationPitch = player.rotationPitch
        player.renderArmYaw = player.rotationYaw
        player.renderArmPitch = player.rotationPitch
        player.prevRenderArmYaw = player.rotationYaw
        player.prevRotationPitch = player.rotationPitch
    }

    private fun update() {
        val settings = activeSettings ?: return
        val player = mc.thePlayer ?: return

        val playerRotation = player.rotation

        val shouldUpdate = !InventoryUtils.serverOpenContainer && !InventoryUtils.serverOpenInventory

        if (!shouldUpdate) {
            return
        }

        if (resetTicks == 0) {
            val distanceToPlayerRotation =
                rotationDifference(currentRotation ?: serverRotation, playerRotation).withGCD()

            if (distanceToPlayerRotation <= settings.angleResetDifference || !settings.applyServerSide) {
                resetRotation()
                return
            }

            currentRotation = limitAngleChange(
                currentRotation ?: serverRotation,
                playerRotation,
                settings
            ).fixedSensitivity()
            return
        }

        targetRotation?.let {
            limitAngleChange(currentRotation ?: serverRotation, it, settings).let { rotation ->
                if (!settings.applyServerSide) {
                    rotation.toPlayer(player)
                } else {
                    currentRotation = rotation.fixedSensitivity()
                }
            }
        }

        if (resetTicks > 0) {
            resetTicks--
        }
    }

    /**
     * Any module that modifies the server packets without using the [currentRotation] should use on module disable.
     */
    fun syncSpecialModuleRotations() {
        serverRotation.let { (yaw, _) ->
            mc.thePlayer?.let {
                it.rotationYaw = yaw + angleDifference(it.rotationYaw, yaw)
                syncRotations()
            }
        }
    }

    /**
     * Checks if the rotation difference is not the same as the smallest GCD angle possible.
     */
    fun canUpdateRotation(current: Rotation, target: Rotation, multiplier: Int = 1): Boolean {
        if (current == target)
            return true

        val smallestAnglePossible = getFixedAngleDelta()

        return rotationDifference(target, current).withGCD() > smallestAnglePossible * multiplier
    }

    /**
     * Handle rotation update
     */
    @EventTarget(priority = -1)
    fun onRotationUpdate(event: RotationUpdateEvent) {
        activeSettings?.let {
            // Was the rotation update immediate? Allow updates the next tick.
            if (it.immediate) {
                it.immediate = false
                return
            }
        }

        update()
    }

    /**
     * Handle strafing
     */
    @EventTarget
    fun onStrafe(event: StrafeEvent) {
        val data = activeSettings ?: return

        if (!data.strafe) {
            return
        }

        currentRotation?.let {
            it.applyStrafeToPlayer(event, data.strict)
            event.cancelEvent()
        }
    }

    /**
     * Handle rotation-packet modification
     */
    @EventTarget
    fun onPacket(event: PacketEvent) {
        val packet = event.packet

        if (packet !is C03PacketPlayer) {
            return
        }

        if (!packet.rotating) {
            activeSettings?.resetSimulateShortStopData()
            return
        }

        currentRotation?.let { packet.rotation = it }

        val diffs = angleDifferences(packet.rotation, serverRotation)

        if (Rotations.debugRotations && currentRotation != null) {
            chat("PREV YAW: ${diffs.x}, PREV PITCH: ${diffs.y}")
        }

        activeSettings?.updateSimulateShortStopData(diffs.x)
    }

    enum class BodyPoint(val rank: Int, val range: ClosedFloatingPointRange<Double>) {
        HEAD(1, 0.75..0.9),
        BODY(0, 0.5..0.75),
        FEET(-1, 0.1..0.4),
        UNKNOWN(-2, 0.0..0.0);

        companion object {
            fun fromString(point: String): BodyPoint {
                return values().find { it.name.equals(point, ignoreCase = true) } ?: UNKNOWN
            }
        }
    }

    fun coerceBodyPoint(point: BodyPoint, minPoint: BodyPoint, maxPoint: BodyPoint): BodyPoint {
        return when {
            point.rank < minPoint.rank -> minPoint
            point.rank > maxPoint.rank -> maxPoint
            else -> point
        }
    }
}