package com.carsimulator.app.sim

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max

/**
 * Something bolted to the road that changes its height. Obstacles only add
 * height; the track combines them with [max] so overlaps never double up.
 */
sealed interface Obstacle {
    /** Longitudinal extent along the track, metres. */
    val startX: Double
    val endX: Double

    /** Height above the road at [x], [z], or 0 when outside the obstacle. */
    fun heightAt(x: Double, z: Double): Double

    /** Single rounded ridge, cos² profile, across the whole road width. */
    data class Bump(
        override val startX: Double,
        val length: Double,
        val height: Double,
        val zStart: Double = -Double.MAX_VALUE,
        val zEnd: Double = Double.MAX_VALUE,
    ) : Obstacle {
        override val endX: Double get() = startX + length
        override fun heightAt(x: Double, z: Double): Double {
            if (x < startX || x > endX || z < zStart || z > zEnd) return 0.0
            val t = (x - startX) / length // 0..1
            val c = cos((t - 0.5) * PI)
            return height * c * c
        }
    }

    /**
     * Repeated ridges like the speed-bump grid in the reference video. Rows are
     * [spacing] apart; when [staggered], every other row is shifted sideways by
     * one bump width so the tyres cannot thread a clean line through.
     */
    data class BumpField(
        override val startX: Double,
        val rows: Int,
        val spacing: Double,
        val bumpLength: Double,
        val height: Double,
        val roadHalfWidth: Double,
        val staggered: Boolean = true,
        val segmentWidth: Double = 2.5,
    ) : Obstacle {
        override val endX: Double get() = startX + (rows - 1) * spacing + bumpLength
        override fun heightAt(x: Double, z: Double): Double {
            if (x < startX || x > endX || abs(z) > roadHalfWidth) return 0.0
            val rel = x - startX
            val row = floor(rel / spacing).toInt()
            if (row < 0 || row >= rows) return 0.0
            val inRow = rel - row * spacing
            if (inRow > bumpLength) return 0.0
            if (staggered) {
                // Alternate rows cover alternating segments of the width.
                val seg = floor((z + roadHalfWidth) / segmentWidth).toInt()
                val covered = (seg + row) % 2 == 0
                if (!covered) return 0.0
            }
            val t = inRow / bumpLength
            val c = cos((t - 0.5) * PI)
            return height * c * c
        }
    }

    /** Wedge rising linearly to [height] over [length], then a sheer drop. */
    data class Ramp(
        override val startX: Double,
        val length: Double,
        val height: Double,
    ) : Obstacle {
        override val endX: Double get() = startX + length
        override fun heightAt(x: Double, z: Double): Double {
            if (x < startX || x > endX) return 0.0
            return height * (x - startX) / length
        }
    }

    /** Flat-topped step (a curb or a loading-dock edge). */
    data class Curb(
        override val startX: Double,
        val length: Double,
        val height: Double,
    ) : Obstacle {
        override val endX: Double get() = startX + length
        override fun heightAt(x: Double, z: Double): Double =
            if (x < startX || x > endX) 0.0 else height
    }

    /** Ascending staircase of [steps] equal steps. */
    data class Stairs(
        override val startX: Double,
        val steps: Int,
        val stepLength: Double,
        val stepHeight: Double,
    ) : Obstacle {
        override val endX: Double get() = startX + steps * stepLength
        override fun heightAt(x: Double, z: Double): Double {
            if (x < startX || x > endX) return 0.0
            val step = floor((x - startX) / stepLength).toInt().coerceIn(0, steps - 1)
            return (step + 1) * stepHeight
        }
    }

    /**
     * Field of flat-bottomed craters with sharp lips. Pits are negative height;
     * the track adds them after taking the max of the positive obstacles.
     */
    data class Potholes(
        override val startX: Double,
        val rows: Int,
        val spacing: Double,
        val diameter: Double,
        val depth: Double,
        val roadHalfWidth: Double,
    ) : Obstacle {
        override val endX: Double get() = startX + rows * spacing
        override fun heightAt(x: Double, z: Double): Double {
            if (x < startX || x > endX || abs(z) > roadHalfWidth) return 0.0
            val rel = x - startX
            val row = floor(rel / spacing).toInt()
            val cx = startX + row * spacing + spacing / 2
            val lateralPitch = diameter * 1.6
            val lane = floor((z + roadHalfWidth) / lateralPitch).toInt()
            val shift = if (row % 2 == 0) 0.0 else lateralPitch / 2
            val cz = -roadHalfWidth + lane * lateralPitch + lateralPitch / 2 + shift
            val dx = x - cx
            val dz = z - cz
            val r = diameter / 2
            val d2 = dx * dx + dz * dz
            if (d2 > r * r) return 0.0
            // Flat floor with a steep lip: the far wall is what breaks things at speed.
            val t = kotlin.math.sqrt(d2) / r
            return if (t < LIP_START) -depth else -depth * (1.0 - (t - LIP_START) / (1.0 - LIP_START))
        }

        private companion object {
            const val LIP_START = 0.85
        }
    }
}

/**
 * The strip of road that flings the car up to the round's target speed.
 * Entering it while grounded sets the forward speed to [targetSpeedMps].
 */
data class BoostStrip(val startX: Double, val endX: Double, val targetSpeedMps: Double) {
    fun contains(x: Double) = x in startX..endX
}

/**
 * A straight drag strip along +X with obstacles laid on it. [heightAt] is the
 * road surface; [normalAt] is its finite-difference normal.
 */
class Track(
    val roadHalfWidth: Double,
    val lengthX: Double,
    val obstacles: List<Obstacle>,
    val boost: BoostStrip,
    /** Where the obstacle section begins: entering it flips the camera. */
    val obstacleStartX: Double,
) {
    val obstacleEndX: Double = obstacles.maxOfOrNull { it.endX } ?: obstacleStartX

    fun heightAt(x: Double, z: Double): Double {
        var h = 0.0
        var pit = 0.0
        for (o in obstacles) {
            if (x < o.startX || x > o.endX) continue
            val v = o.heightAt(x, z)
            if (v >= 0) h = max(h, v) else pit = kotlin.math.min(pit, v)
        }
        // Off the road the shoulder drops away gently into a grass ditch.
        val shoulder = if (abs(z) > roadHalfWidth) -(abs(z) - roadHalfWidth) * 0.15 else 0.0
        return h + pit + shoulder
    }

    fun normalAt(x: Double, z: Double, eps: Double = 0.05): Vec3 {
        val hx = heightAt(x + eps, z) - heightAt(x - eps, z)
        val hz = heightAt(x, z + eps) - heightAt(x, z - eps)
        return Vec3(-hx / (2 * eps), 1.0, -hz / (2 * eps)).normalized()
    }

    fun isInObstacleZone(x: Double) = x >= obstacleStartX && x <= obstacleEndX + 10.0
}
