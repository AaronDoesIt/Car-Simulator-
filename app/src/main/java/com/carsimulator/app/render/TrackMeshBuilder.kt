package com.carsimulator.app.render

import com.carsimulator.app.sim.Track
import com.carsimulator.app.sim.Vec3
import kotlin.math.abs
import kotlin.math.floor

/**
 * Turns the track heightfield into a coloured grid mesh: asphalt with edge
 * lines, a glowing boost strip, yellow-and-black obstacle stripes, dark
 * craters, and grass shoulders. Fine sampling only where the road is bumpy.
 */
object TrackMeshBuilder {

    private const val LATERAL_EXTENT = 45.0
    private const val LATERAL_STEP = 0.5
    private const val COARSE_STEP = 6.0
    private const val FINE_STEP = 0.25

    /** Each returned mesh stays under the 16-bit index limit. */
    fun build(track: Track): List<MeshData> {
        val xs = ArrayList<Double>()
        var x = -60.0
        val fineStart = track.obstacleStartX - 4.0
        val fineEnd = track.obstacleEndX + 4.0
        while (x <= track.lengthX + 60.0) {
            xs.add(x)
            val inFine = x >= fineStart - COARSE_STEP && x <= fineEnd
            val inBoost = x >= track.boost.startX - COARSE_STEP && x <= track.boost.endX
            x += when {
                inFine -> FINE_STEP
                inBoost -> 1.0
                else -> COARSE_STEP
            }
        }
        val zs = ArrayList<Double>()
        var z = -LATERAL_EXTENT
        while (z <= LATERAL_EXTENT + 1e-9) { zs.add(z); z += LATERAL_STEP }

        // Split rows into chunks so each chunk's vertex count fits in a short.
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
                    val n = track.normalAt(xr, zc)
                    val (cr, cg, cb) = colorAt(track, xr, zc, h)
                    ids[r - row][c] = m.addVertex(Vec3(xr, h, zc), n, cr, cg, cb)
                }
            }
            for (r in 0 until endRow - row) {
                for (c in 0 until zs.size - 1) {
                    val a = ids[r][c]; val b = ids[r][c + 1]; val cc = ids[r + 1][c + 1]; val d = ids[r + 1][c]
                    // Counter-clockwise seen from above (+Y).
                    m.addQuad(a, b, cc, d)
                }
            }
            meshes.add(m)
            row = endRow
        }
        return meshes
    }

    private fun colorAt(track: Track, x: Double, z: Double, h: Double): Triple<Float, Float, Float> {
        val onRoad = abs(z) <= track.roadHalfWidth
        if (!onRoad) {
            val tint = if ((floor(x / 12.0) + floor(z / 12.0)).toInt() % 2 == 0) 0.0f else 0.04f
            return Triple(0.30f + tint, 0.55f + tint, 0.22f)
        }
        if (h < -0.01) return Triple(0.16f, 0.13f, 0.11f)
        if (h > 0.01) {
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
        return Triple(0.36f, 0.36f, 0.38f)
    }
}
