package com.carsimulator.app.render

import com.carsimulator.app.sim.Vec3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Dome that rides along with the camera. The shader colours it by view
 * direction (gradient, sun, clouds), so the mesh only has to cover the sky
 * and dip a little below the horizon to hide the edge of the world.
 */
object SkyBuilder {
    const val RADIUS = 2400.0

    fun dome(rings: Int = 12, segments: Int = 36): MeshData {
        val m = MeshData()
        val ids = Array(rings + 1) { IntArray(segments) }
        for (r in 0..rings) {
            // Elevation from 12 degrees below the horizon up to the zenith.
            val elev = -12.0 + (90.0 + 12.0) * r / rings
            val e = Math.toRadians(elev)
            for (s in 0 until segments) {
                val a = 2 * PI * s / segments
                val p = Vec3(cos(e) * cos(a), sin(e), cos(e) * sin(a)) * RADIUS
                ids[r][s] = m.addVertex(p, -p.normalized(), 0.75f, 0.83f, 0.93f, Material.MATTE)
            }
        }
        for (r in 0 until rings) for (s in 0 until segments) {
            val s1 = (s + 1) % segments
            m.addQuad(ids[r][s], ids[r][s1], ids[r + 1][s1], ids[r + 1][s])
        }
        return m
    }
}
