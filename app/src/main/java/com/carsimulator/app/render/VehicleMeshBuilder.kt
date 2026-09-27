package com.carsimulator.app.render

import com.carsimulator.app.sim.BodyStyle
import com.carsimulator.app.sim.DamageModel
import com.carsimulator.app.sim.Panel
import com.carsimulator.app.sim.Vec3
import com.carsimulator.app.sim.VehicleSpec
import kotlin.math.max
import kotlin.math.sin

/**
 * Builds a low-poly body for a [VehicleSpec] out of boxes in the body frame
 * (centre of gravity at the origin, X forward, Y up, Z right), plus the
 * per-vertex panel weights that let [deform] crumple it as damage grows.
 */
class VehicleMesh private constructor(
    val data: MeshData,
    private val restPositions: FloatArray,
    /** Per vertex, per panel: how strongly that vertex crumples with the panel. */
    private val panelWeights: Array<FloatArray>,
    private val bodyCentre: Vec3,
) {
    /**
     * Write crumpled positions into [target] (the GL vertex array, interleaved)
     * using the current damage levels.
     */
    fun deform(damage: DamageModel, target: FloatArray) {
        val levels = FloatArray(Panel.entries.size) { damage.level(Panel.entries[it]).toFloat() }
        val count = restPositions.size / 3
        for (i in 0 until count) {
            var d = 0f
            val w = panelWeights[i]
            for (p in levels.indices) d = max(d, w[p] * levels[p])
            val rx = restPositions[i * 3]; val ry = restPositions[i * 3 + 1]; val rz = restPositions[i * 3 + 2]
            // Pull toward the body centre and add a hashed ripple so panels look bent, not scaled.
            val pull = 0.30f * d
            val ripple = 0.10f * d
            val hx = sin(i * 12.9898f) * ripple
            val hy = sin(i * 78.233f) * ripple
            val hz = sin(i * 37.719f) * ripple
            target[i * MeshData.STRIDE] = rx - (rx - bodyCentre.x.toFloat()) * pull + hx
            target[i * MeshData.STRIDE + 1] = ry - (ry - bodyCentre.y.toFloat()) * pull * 1.3f + hy
            target[i * MeshData.STRIDE + 2] = rz - (rz - bodyCentre.z.toFloat()) * pull + hz
        }
    }

    companion object {
        fun build(spec: VehicleSpec): VehicleMesh {
            val m = MeshData()
            val bottom = -(spec.cgHeightM - spec.groundClearanceM)
            val top = spec.heightM - spec.cgHeightM
            val xF = spec.frontAxleX + spec.frontOverhang
            val xR = spec.rearAxleX - spec.rearOverhang
            val len = xF - xR
            val w = spec.widthM
            val body = spec.colorRgb
            val accent = spec.accentRgb
            val glass = 0x1E2A3A
            val bodyHeight = top - bottom

            fun box(x0: Double, x1: Double, y0: Double, y1: Double, zHalf: Double, color: Int) {
                m.addBox(Vec3((x0 + x1) / 2, (y0 + y1) / 2, 0.0), Vec3(x1 - x0, y1 - y0, zHalf * 2), color)
            }

            when (spec.bodyStyle) {
                BodyStyle.PICKUP -> {
                    val beltline = bottom + bodyHeight * 0.52
                    // Lower body full length.
                    box(xR, xF, bottom, beltline, w / 2, body)
                    // Hood, slightly lower than the belt.
                    box(xR + len * 0.66, xF - 0.05, beltline, beltline + bodyHeight * 0.08, w / 2 * 0.96, body)
                    // Cab.
                    val cab0 = xR + len * 0.36; val cab1 = xR + len * 0.66
                    box(cab0, cab1, beltline, top, w / 2 * 0.94, body)
                    box(cab0 + 0.05, cab1 - 0.05, beltline + bodyHeight * 0.05, top - 0.05, w / 2 * 0.95, glass)
                    // Bed rails.
                    box(xR + 0.02, cab0, beltline, beltline + bodyHeight * 0.16, w / 2, body)
                    box(xR + 0.1, cab0 - 0.1, beltline, beltline + bodyHeight * 0.13, w / 2 * 0.82, accent)
                }
                BodyStyle.SUV, BodyStyle.OFFROAD -> {
                    val beltline = bottom + bodyHeight * 0.50
                    box(xR, xF, bottom, beltline, w / 2, body)
                    val cab0 = xR + 0.02; val cab1 = xR + len * (if (spec.bodyStyle == BodyStyle.SUV) 0.72 else 0.68)
                    box(cab0, cab1, beltline, top, w / 2 * 0.95, body)
                    box(cab0 + 0.05, cab1 - 0.05, beltline + bodyHeight * 0.06, top - 0.06, w / 2 * 0.96, glass)
                    box(cab1, xF - 0.05, beltline, beltline + bodyHeight * 0.10, w / 2 * 0.96, body)
                }
                BodyStyle.SPORTS -> {
                    val beltline = bottom + bodyHeight * 0.58
                    box(xR, xF, bottom, beltline, w / 2, body)
                    val cab0 = xR + len * 0.30; val cab1 = xR + len * 0.62
                    box(cab0, cab1, beltline, top, w / 2 * 0.80, body)
                    box(cab0 + 0.04, cab1 - 0.04, beltline + 0.02, top - 0.04, w / 2 * 0.82, glass)
                }
                BodyStyle.MUSCLE, BodyStyle.SEDAN -> {
                    val beltline = bottom + bodyHeight * 0.55
                    box(xR, xF, bottom, beltline, w / 2, body)
                    val cab0 = xR + len * 0.24; val cab1 = xR + len * 0.64
                    box(cab0, cab1, beltline, top, w / 2 * 0.86, body)
                    box(cab0 + 0.05, cab1 - 0.05, beltline + 0.03, top - 0.05, w / 2 * 0.88, glass)
                }
            }
            // Bumpers and grille.
            box(xF - 0.12, xF + 0.06, bottom + 0.05, bottom + 0.35, w / 2 * 0.98, accent)
            box(xR - 0.06, xR + 0.12, bottom + 0.05, bottom + 0.35, w / 2 * 0.98, accent)
            // Headlights and tail lights.
            m.addBox(Vec3(xF + 0.03, bottom + bodyHeight * 0.42, -w * 0.32), Vec3(0.06, 0.16, 0.30), 0xFFF3C4)
            m.addBox(Vec3(xF + 0.03, bottom + bodyHeight * 0.42, w * 0.32), Vec3(0.06, 0.16, 0.30), 0xFFF3C4)
            m.addBox(Vec3(xR - 0.03, bottom + bodyHeight * 0.42, -w * 0.32), Vec3(0.06, 0.14, 0.28), 0xE53935)
            m.addBox(Vec3(xR - 0.03, bottom + bodyHeight * 0.42, w * 0.32), Vec3(0.06, 0.14, 0.28), 0xE53935)

            val rest = m.positionsCopy()
            val centre = Vec3((xF + xR) / 2, (top + bottom) / 2, 0.0)
            val halfLen = len / 2
            val halfWid = w / 2
            val weights = Array(m.vertexCount) { i ->
                val x = rest[i * 3].toDouble() - centre.x
                val y = rest[i * 3 + 1].toDouble()
                val z = rest[i * 3 + 2].toDouble()
                val arr = FloatArray(Panel.entries.size)
                arr[Panel.FRONT.ordinal] = smooth(x / halfLen)
                arr[Panel.REAR.ordinal] = smooth(-x / halfLen)
                arr[Panel.RIGHT.ordinal] = smooth(z / halfWid)
                arr[Panel.LEFT.ordinal] = smooth(-z / halfWid)
                arr[Panel.ROOF.ordinal] = if (top > 0) smooth(y / top) else 0f
                arr[Panel.UNDERBODY.ordinal] = if (bottom < 0) smooth(y / bottom) else 0f
                arr
            }
            return VehicleMesh(m, rest, weights, centre)
        }

        /** 0 below 0.2, ramping to 1 at 1.0, so only vertices near a panel move with it. */
        private fun smooth(t: Double): Float {
            val c = ((t - 0.2) / 0.8).coerceIn(0.0, 1.0)
            return (c * c).toFloat()
        }

        fun buildWheel(spec: VehicleSpec): MeshData {
            val m = MeshData()
            m.addCylinderZ(spec.wheelRadiusM, spec.wheelWidthM, 14, 0x1B1B1B, 0xB0B0B0)
            return m
        }
    }
}
