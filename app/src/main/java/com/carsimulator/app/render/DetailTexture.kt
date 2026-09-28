package com.carsimulator.app.render

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sin

/**
 * The one texture the game uses: four tileable grey-scale detail patterns
 * packed into RGBA. The shader picks a channel by material and multiplies the
 * vertex colour by it, so asphalt gets grain, grass gets mottling, rock and
 * bark get streaks and foliage gets clumps. Generated at start-up, no files.
 *
 *  R  asphalt: fine grain plus per-texel speckle
 *  G  ground: soft mid-frequency mottle (doubles as the cloud pattern)
 *  B  rock and bark: ridged, streaked along one axis
 *  A  foliage: high-contrast clumps
 */
object DetailTexture {
    const val SIZE = 512

    /** RGBA bytes, row-major, [SIZE] x [SIZE]. Same output every call. */
    fun build(): ByteArray {
        val out = ByteArray(SIZE * SIZE * 4)
        var k = 0
        for (y in 0 until SIZE) {
            val v = y.toDouble() / SIZE
            for (x in 0 until SIZE) {
                val u = x.toDouble() / SIZE
                out[k++] = byte(asphalt(u, v, x, y))
                out[k++] = byte(ground(u, v))
                out[k++] = byte(rock(u, v))
                out[k++] = byte(foliage(u, v))
            }
        }
        return out
    }

    fun asphalt(u: Double, v: Double, x: Int, y: Int): Double {
        val f = fbm(u, v, 32, 32, 5, seed = 1, gain = 0.5)
        val speckle = hash(x, y, 9)
        return 0.5 + (f - 0.5) * 1.5 + (speckle - 0.5) * 0.22
    }

    fun ground(u: Double, v: Double): Double {
        val f = fbm(u, v, 4, 4, 5, seed = 2, gain = 0.55)
        return 0.5 + (f - 0.5) * 1.5
    }

    fun rock(u: Double, v: Double): Double {
        val f = fbm(u, v, 3, 9, 5, seed = 3, gain = 0.5)
        val ridge = 1.0 - abs(2.0 * f - 1.0)
        // fbm sits near 0.5, so the ridge crowds towards 1; stretch it back around mid grey.
        return 0.5 + (ridge - 0.75) * 1.6
    }

    fun foliage(u: Double, v: Double): Double {
        val f = fbm(u, v, 16, 16, 4, seed = 4, gain = 0.6)
        val clumps = smoothstep((f - 0.38) / 0.3)
        return 0.25 + clumps * 0.65
    }

    /**
     * Value noise on a lattice that wraps every [periodX] by [periodY] cells
     * across the unit square, so the result tiles exactly.
     */
    fun noise(u: Double, v: Double, periodX: Int, periodY: Int, seed: Int): Double {
        val x = u * periodX; val y = v * periodY
        val x0 = floor(x).toInt(); val y0 = floor(y).toInt()
        val fx = smoothstep(x - x0); val fy = smoothstep(y - y0)
        fun h(i: Int, j: Int) = hash(Math.floorMod(i, periodX), Math.floorMod(j, periodY), seed)
        val a = h(x0, y0); val b = h(x0 + 1, y0); val c = h(x0, y0 + 1); val d = h(x0 + 1, y0 + 1)
        val top = a + (b - a) * fx
        val bottom = c + (d - c) * fx
        return top + (bottom - top) * fy
    }

    fun fbm(u: Double, v: Double, periodX: Int, periodY: Int, octaves: Int, seed: Int, gain: Double): Double {
        var amp = 1.0; var sum = 0.0; var norm = 0.0
        var px = periodX; var py = periodY
        for (i in 0 until octaves) {
            sum += amp * noise(u, v, px, py, seed * 31 + i)
            norm += amp
            amp *= gain
            px *= 2; py *= 2
        }
        return sum / norm
    }

    private fun hash(x: Int, y: Int, seed: Int): Double {
        val s = sin(x * 127.1 + y * 311.7 + seed * 74.7) * 43758.5453
        return s - floor(s)
    }

    private fun smoothstep(t: Double): Double {
        val c = t.coerceIn(0.0, 1.0)
        return c * c * (3 - 2 * c)
    }

    private fun byte(v: Double): Byte = (v.coerceIn(0.0, 1.0) * 255.0 + 0.5).toInt().toByte()
}
