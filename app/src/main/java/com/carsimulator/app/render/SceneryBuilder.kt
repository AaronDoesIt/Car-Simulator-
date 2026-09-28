package com.carsimulator.app.render

import com.carsimulator.app.sim.Track
import com.carsimulator.app.sim.Vec3
import kotlin.math.floor
import kotlin.math.sin

/**
 * Trees and boulders scattered over the hills beside the strip. Positions are
 * hashed from the track coordinates so the forest is the same every run.
 */
object SceneryBuilder {

    private const val SPACING_X = 7.0

    fun build(track: Track): List<MeshData> {
        val meshes = ArrayList<MeshData>()
        var m = MeshData()
        var x = -80.0
        var i = 0
        while (x < track.lengthX + 80.0) {
            for (side in intArrayOf(-1, 1)) {
                for (band in 0 until 3) {
                    val h1 = hash(i, band, side + 2)
                    val h2 = hash(i, band, side + 5)
                    val h3 = hash(i, band, side + 9)
                    if (h1 < 0.35) continue // gaps in the tree line
                    val z = side * (track.roadHalfWidth + 8.0 + band * 22.0 + h2 * 20.0)
                    val px = x + (h3 - 0.5) * SPACING_X
                    val y = track.heightAt(px, z)
                    val altitude = track.terrainHeightAt(px, z)
                    if (altitude > 95.0) continue // above the tree line
                    val steep = 1.0 - track.normalAt(px, z, eps = 1.0).y
                    if (m.vertexCount > 58000) { meshes.add(m); m = MeshData() }
                    if (steep > 0.35 || h1 > 0.9) {
                        boulder(m, Vec3(px, y, z), 0.8 + h2 * 1.8, h3)
                    } else {
                        tree(m, Vec3(px, y - 0.2, z), 4.5 + h2 * 4.0, 1.6 + h3 * 1.4, shade = h1)
                    }
                }
            }
            x += SPACING_X
            i++
        }
        if (m.vertexCount > 0) meshes.add(m)
        return meshes
    }

    private fun tree(m: MeshData, base: Vec3, height: Double, radius: Double, shade: Double) {
        val trunkH = height * 0.30
        m.addBox(base + Vec3(0.0, trunkH / 2, 0.0), Vec3(0.34, trunkH, 0.34), 0x4E3418, Material.BARK)
        val g = (0.30 + shade * 0.22).toFloat()
        val green = MeshData.pack(0.09f + shade.toFloat() * 0.04f, g, 0.11f)
        val lower = MeshData.shade(green, 0.85f)
        // Three stacked cones, the lowest slightly darker, read as a conifer.
        m.addCone(base + Vec3(0.0, trunkH * 0.75, 0.0), radius, height * 0.42, 8, lower, Material.FOLIAGE)
        m.addCone(base + Vec3(0.0, trunkH * 0.75 + height * 0.22, 0.0), radius * 0.80, height * 0.40, 8, green, Material.FOLIAGE)
        m.addCone(base + Vec3(0.0, trunkH * 0.75 + height * 0.42, 0.0), radius * 0.55, height * 0.34, 8, green, Material.FOLIAGE)
    }

    private fun boulder(m: MeshData, base: Vec3, size: Double, seed: Double) {
        // Two overlapping hulls with hashed corner jitter so no two rocks are the same box.
        fun jitter(k: Int) = (hash(k, (seed * 1000).toInt(), 3) - 0.5) * size * 0.25
        val hx = size * 0.7; val hz = size * 0.5; val h = size * 0.8
        val c = base + Vec3(0.0, size * 0.05, 0.0)
        m.addHull(
            arrayOf(
                c + Vec3(-hx + jitter(0), 0.0, -hz + jitter(1)), c + Vec3(hx + jitter(2), 0.0, -hz + jitter(3)),
                c + Vec3(hx + jitter(4), 0.0, hz + jitter(5)), c + Vec3(-hx + jitter(6), 0.0, hz + jitter(7)),
                c + Vec3(-hx * 0.6 + jitter(8), h, -hz * 0.6 + jitter(9)), c + Vec3(hx * 0.6 + jitter(10), h * 0.9, -hz * 0.6 + jitter(11)),
                c + Vec3(hx * 0.6 + jitter(12), h * 0.95, hz * 0.6 + jitter(13)), c + Vec3(-hx * 0.6 + jitter(14), h * 0.85, hz * 0.6 + jitter(15)),
            ),
            IntArray(6) { 0x6E6A64 }, Material.BARK,
        )
        m.addBox(base + Vec3(size * 0.2, size * 0.55, size * 0.15), Vec3(size * 0.7, size * 0.6, size * 0.6), 0x7C7872, Material.BARK)
    }

    private fun hash(i: Int, band: Int, salt: Int): Double {
        val v = sin(i * 12.9898 + band * 78.233 + salt * 37.719) * 43758.5453
        return v - floor(v)
    }
}
