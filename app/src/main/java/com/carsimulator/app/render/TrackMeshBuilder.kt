package com.carsimulator.app.render

import com.carsimulator.app.sim.Terrain
import com.carsimulator.app.sim.Track
import com.carsimulator.app.sim.Vec3
import kotlin.math.abs
import kotlin.math.floor

/**
 * Turns the track heightfield into coloured grid meshes: asphalt with edge
 * lines and centre dashes, a glowing boost strip, yellow-and-black obstacle
 * stripes, dark craters, and a landscape that goes grass, rock, snow with
 * altitude. Sampling is dense on the road and coarse out on the mountains.
 */
object TrackMeshBuilder {

    private const val COARSE_STEP = 4.0
    private const val FINE_STEP = 0.25

    /** Each returned mesh stays under the 16-bit index limit. */
    fun build(track: Track): List<MeshData> {
        val xs = ArrayList<Double>()
        var x = -80.0
        val fineStart = track.obstacleStartX - 4.0
        val fineEnd = (track.obstacles.maxOfOrNull { it.endX } ?: track.obstacleStartX) + 4.0
        while (x <= track.lengthX + 80.0) {
            xs.add(x)
            val inFine = x >= fineStart - COARSE_STEP && x <= fineEnd
            val inBoost = x >= track.boost.startX - COARSE_STEP && x <= track.boost.endX
            x += when {
                inFine -> FINE_STEP
                inBoost -> 1.0
                else -> COARSE_STEP
            }
        }
        val zs = lateralSamples()

        val rowsPerChunk = (60000 / zs.size) - 1
        val meshes = ArrayList<MeshData>()
        var row = 0
        while (row < xs.size - 1) {
            val endRow = minOf(xs.size - 1, row + rowsPerChunk)
            val m = MeshData()
            val ids = Array(endRow - row + 1) { IntArray(zs.size) }
            for (r in row..endRow) {
                val xr = xs[r]
                for ((c, zc) in zs.withIndex()) {
                    val h = track.heightAt(xr, zc)
                    val n = track.normalAt(xr, zc, eps = if (abs(zc) > track.roadHalfWidth + 20) 1.0 else 0.05)
                    val (cr, cg, cb) = colorAt(track, xr, zc, n)
                    ids[r - row][c] = m.addVertex(Vec3(xr, h, zc), n, cr, cg, cb)
                }
            }
            for (r in 0 until endRow - row) {
                for (c in 0 until zs.size - 1) {
                    val a = ids[r][c]; val b = ids[r][c + 1]; val cc = ids[r + 1][c + 1]; val d = ids[r + 1][c]
                    m.addQuad(a, b, cc, d)
                }
            }
            meshes.add(m)
            row = endRow
        }
        return meshes
    }

    /** Dense across the road, then progressively coarser out to the mountains. */
    fun lateralSamples(): List<Double> {
        val half = ArrayList<Double>()
        var z = 0.0
        while (z <= 14.0) { half.add(z); z += 0.5 }
        z = 16.0
        while (z <= 44.0) { half.add(z); z += 2.0 }
        z = 50.0
        while (z <= 140.0) { half.add(z); z += 6.0 }
        z = 155.0
        while (z <= 320.0) { half.add(z); z += 15.0 }
        val out = ArrayList<Double>()
        for (i in half.indices.reversed()) if (half[i] != 0.0) out.add(-half[i])
        out.addAll(half)
        return out
    }

    private fun colorAt(track: Track, x: Double, z: Double, normal: Vec3): Triple<Float, Float, Float> {
        val onRoad = abs(z) <= track.roadHalfWidth
        if (!onRoad) return landColor(track, x, z, normal)
        val obstacle = track.obstacleHeightAt(x, z)
        if (obstacle < -0.01) return Triple(0.16f, 0.13f, 0.11f)
        if (obstacle > 0.01) {
            val stripe = floor(x / 0.45).toInt() % 2 == 0
            return if (stripe) Triple(0.95f, 0.75f, 0.10f) else Triple(0.15f, 0.15f, 0.15f)
        }
        if (track.boost.contains(x)) {
            val pulse = floor(x / 1.0).toInt() % 2 == 0
            return if (pulse) Triple(0.15f, 0.65f, 1.0f) else Triple(0.05f, 0.35f, 0.75f)
        }
        val edge = abs(abs(z) - track.roadHalfWidth) < 0.5
        if (edge) return Triple(0.92f, 0.92f, 0.92f)
        val centreDash = abs(z) < 0.2 && floor(x / 4.0).toInt() % 2 == 0
        if (centreDash) return Triple(0.95f, 0.85f, 0.35f)
        val grain = ((floor(x / 2.0) * 7 + floor(z / 2.0) * 13).toInt() and 3) * 0.012f
        return Triple(0.35f + grain, 0.35f + grain, 0.37f + grain)
    }

    private fun landColor(track: Track, x: Double, z: Double, normal: Vec3): Triple<Float, Float, Float> {
        val altitude = track.terrainHeightAt(x, z)
        val slope = 1.0 - normal.y // 0 flat, larger on steep faces
        val grassTint = ((floor(x / 9.0) + floor(z / 9.0)).toInt() and 1) * 0.035f
        val grass = floatArrayOf(0.29f + grassTint, 0.54f + grassTint, 0.21f)
        val rock = floatArrayOf(0.46f, 0.42f, 0.37f)
        val snow = floatArrayOf(0.93f, 0.95f, 0.98f)
        val rockBlend = (Terrain.smoothstep((altitude - 35.0) / 40.0) + slope * 1.6).coerceIn(0.0, 1.0).toFloat()
        val snowBlend = (Terrain.smoothstep((altitude - 110.0) / 50.0) * (1.0 - slope * 1.2)).coerceIn(0.0, 1.0).toFloat()
        val out = FloatArray(3) { i ->
            val gr = grass[i] + (rock[i] - grass[i]) * rockBlend
            gr + (snow[i] - gr) * snowBlend
        }
        return Triple(out[0], out[1], out[2])
    }
}
