package com.carsimulator.app.sim

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sin

/**
 * Off-road landscape: rolling hills close to the strip that grow into
 * mountains further out. Deterministic value noise from a hashed lattice,
 * so the same seed always builds the same world.
 */
class Terrain(
    private val seed: Int,
    /** Height of the hills that begin just past the shoulder, metres. */
    val hillAmplitude: Double = 35.0,
    /** Height of the mountain band further out, metres. */
    val mountainAmplitude: Double = 140.0,
) {
    /** Height above the road profile at ([x], [z]) for a road of [roadHalfWidth]. */
    fun heightAt(x: Double, z: Double, roadHalfWidth: Double): Double {
        val edge = abs(z) - roadHalfWidth
        if (edge <= SHOULDER_M) return 0.0
        val hillBlend = smoothstep((edge - SHOULDER_M) / HILL_RAMP_M)
        val mountainBlend = smoothstep((edge - MOUNTAIN_START_M) / MOUNTAIN_RAMP_M)
        val hills = noise(x / 55.0, z / 55.0, 0) * 0.65 + noise(x / 22.0, z / 22.0, 1) * 0.35
        val mountains = noise(x / 170.0, z / 170.0, 2) * 0.7 + noise(x / 70.0, z / 70.0, 3) * 0.3
        return hillBlend * hills * hillAmplitude + mountainBlend * mountains * mountainAmplitude
    }

    /** Smoothly interpolated lattice noise in 0..1. */
    fun noise(x: Double, y: Double, octave: Int): Double {
        val x0 = floor(x); val y0 = floor(y)
        val fx = smoothstep(x - x0); val fy = smoothstep(y - y0)
        val ix = x0.toInt(); val iy = y0.toInt()
        val a = hash(ix, iy, octave); val b = hash(ix + 1, iy, octave)
        val c = hash(ix, iy + 1, octave); val d = hash(ix + 1, iy + 1, octave)
        val top = a + (b - a) * fx
        val bottom = c + (d - c) * fx
        return top + (bottom - top) * fy
    }

    private fun hash(x: Int, y: Int, octave: Int): Double {
        val v = sin((x * 127.1 + y * 311.7 + (octave + seed * 17) * 74.7)) * 43758.5453
        return v - floor(v)
    }

    companion object {
        const val SHOULDER_M = 6.0
        const val HILL_RAMP_M = 30.0
        const val MOUNTAIN_START_M = 110.0
        const val MOUNTAIN_RAMP_M = 120.0

        fun smoothstep(t: Double): Double {
            val c = t.coerceIn(0.0, 1.0)
            return c * c * (3 - 2 * c)
        }
    }
}

/** Height of the road centreline along the strip: the hills the car drives over. */
sealed interface RoadProfile {
    fun heightAt(x: Double): Double

    data object Flat : RoadProfile {
        override fun heightAt(x: Double) = 0.0
    }

    /** Sine hills between [startX] and [endX], eased in and out so the strip stays level. */
    data class RollerCoaster(
        val startX: Double,
        val endX: Double,
        val amplitude: Double,
        val wavelength: Double,
    ) : RoadProfile {
        override fun heightAt(x: Double): Double {
            if (x <= startX || x >= endX) return 0.0
            val fade = Terrain.smoothstep((x - startX) / 40.0) * Terrain.smoothstep((endX - x) / 40.0)
            val phase = (x - startX) / wavelength * 2 * Math.PI
            return amplitude * (0.5 - 0.5 * kotlin.math.cos(phase)) * fade
        }
    }

    /**
     * A long climb to a crest that ends in a steep drop, so a car with any
     * speed leaves the ground and falls to the lower road beyond.
     */
    data class CrestJump(
        val startX: Double,
        val climbLength: Double,
        val height: Double,
        /** Horizontal length of the drop face; short means near-vertical. */
        val dropLength: Double = 6.0,
    ) : RoadProfile {
        val crestX: Double get() = startX + climbLength
        override fun heightAt(x: Double): Double = when {
            x <= startX -> 0.0
            x < crestX -> height * Terrain.smoothstep((x - startX) / climbLength)
            x < crestX + dropLength -> height * (1.0 - (x - crestX) / dropLength)
            else -> 0.0
        }
    }

    /** The road falls away into a canyon and climbs back out on the far side. */
    data class CanyonDrop(
        val startX: Double,
        val depth: Double,
        val dropLength: Double = 8.0,
        val floorLength: Double = 40.0,
        val climbLength: Double = 60.0,
    ) : RoadProfile {
        override fun heightAt(x: Double): Double {
            val rel = x - startX
            return when {
                rel <= 0 -> 0.0
                rel < dropLength -> -depth * rel / dropLength
                rel < dropLength + floorLength -> -depth
                rel < dropLength + floorLength + climbLength ->
                    -depth * (1.0 - Terrain.smoothstep((rel - dropLength - floorLength) / climbLength))
                else -> 0.0
            }
        }
    }
}
