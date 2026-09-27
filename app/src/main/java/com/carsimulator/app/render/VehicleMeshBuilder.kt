package com.carsimulator.app.render

import com.carsimulator.app.sim.BodyStyle
import com.carsimulator.app.sim.DamageModel
import com.carsimulator.app.sim.Panel
import com.carsimulator.app.sim.Vec3
import com.carsimulator.app.sim.VehicleSpec
import kotlin.math.max
import kotlin.math.sin

/**
 * Builds a body for a [VehicleSpec] out of tapered hulls in the body frame
 * (centre of gravity at the origin, X forward, Y up, Z right), plus the
 * per-vertex panel weights that let [deform] crumple it as damage grows.
 * Every proportion comes from the spec, so a Suburban is long and boxy and a
 * Corvette is low and pinched without any per-model geometry.
 */
class VehicleMesh private constructor(
    val data: MeshData,
    private val restPositions: FloatArray,
    private val panelWeights: Array<FloatArray>,
    private val bodyCentre: Vec3,
) {
    /** Write crumpled positions into [target] (interleaved GL array) for the current damage. */
    fun deform(damage: DamageModel, target: FloatArray) {
        val levels = FloatArray(Panel.entries.size) { damage.level(Panel.entries[it]).toFloat() }
        val count = restPositions.size / 3
        for (i in 0 until count) {
            var d = 0f
            val w = panelWeights[i]
            for (p in levels.indices) d = max(d, w[p] * levels[p])
            val rx = restPositions[i * 3]; val ry = restPositions[i * 3 + 1]; val rz = restPositions[i * 3 + 2]
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
        private const val GLASS = 0x1B2634
        private const val CHROME = 0xD8DCE0
        private const val LIGHT = 0xFFF3C4
        private const val TAIL = 0xE53935
        private const val RUBBER = 0x151515

        fun build(spec: VehicleSpec): VehicleMesh {
            val m = MeshData()
            val bottom = -(spec.cgHeightM - spec.groundClearanceM)
            val top = spec.heightM - spec.cgHeightM
            val xF = spec.frontAxleX + spec.frontOverhang
            val xR = spec.rearAxleX - spec.rearOverhang
            val len = xF - xR
            val hw = spec.widthM / 2
            val bh = top - bottom
            val body = spec.colorRgb
            val accent = spec.accentRgb
            val wheelY = spec.wheelRadiusM - spec.cgHeightM
            val r = spec.wheelRadiusM

            /** Tapered slab: bottom rectangle, top rectangle that can be inset, raked and sloped. */
            fun slab(
                xBack: Double, xFront: Double, yBottom: Double, yTopBack: Double, yTopFront: Double,
                zBottom: Double, zTop: Double, xTopBack: Double = xBack, xTopFront: Double = xFront,
                colors: IntArray,
            ) {
                m.addHull(
                    arrayOf(
                        Vec3(xBack, yBottom, -zBottom), Vec3(xFront, yBottom, -zBottom),
                        Vec3(xFront, yBottom, zBottom), Vec3(xBack, yBottom, zBottom),
                        Vec3(xTopBack, yTopBack, -zTop), Vec3(xTopFront, yTopFront, -zTop),
                        Vec3(xTopFront, yTopFront, zTop), Vec3(xTopBack, yTopBack, zTop),
                    ),
                    colors,
                )
            }
            fun solid(c: Int) = intArrayOf(c, c, c, c, c, c)
            fun cabin(roof: Int) = intArrayOf(roof, roof, GLASS, GLASS, GLASS, GLASS)

            fun arches() {
                for (ax in listOf(spec.frontAxleX, spec.rearAxleX)) for (side in listOf(-1.0, 1.0)) {
                    m.addBox(Vec3(ax, wheelY + r * 0.75, side * (hw + 0.03)), Vec3(r * 2.5, r * 0.9, 0.14), accent)
                }
            }
            fun mirrors(xPillar: Double, yMirror: Double) {
                for (side in listOf(-1.0, 1.0)) {
                    m.addBox(Vec3(xPillar, yMirror, side * (hw + 0.10)), Vec3(0.16, 0.12, 0.22), body)
                }
            }
            fun frontEnd(yNose: Double) {
                m.addBox(Vec3(xF - 0.02, yNose, 0.0), Vec3(0.06, bh * 0.16, hw * 0.9), accent) // grille
                m.addBox(Vec3(xF - 0.10, bottom + 0.22, 0.0), Vec3(0.28, 0.24, hw * 2.0), CHROME) // bumper
                for (side in listOf(-1.0, 1.0)) {
                    m.addBox(Vec3(xF + 0.02, yNose + 0.02, side * hw * 0.68), Vec3(0.06, 0.16, 0.32), LIGHT)
                }
            }
            fun rearEnd(yLights: Double) {
                m.addBox(Vec3(xR + 0.10, bottom + 0.22, 0.0), Vec3(0.28, 0.24, hw * 2.0), CHROME)
                for (side in listOf(-1.0, 1.0)) {
                    m.addBox(Vec3(xR - 0.02, yLights, side * hw * 0.70), Vec3(0.06, 0.16, 0.30), TAIL)
                }
                m.addBox(Vec3(xR - 0.05, bottom + 0.08, -hw * 0.6), Vec3(0.20, 0.09, 0.09), CHROME) // exhaust
            }

            when (spec.bodyStyle) {
                BodyStyle.PICKUP -> {
                    val belt = bottom + bh * 0.50
                    val cab0 = xR + len * 0.36; val cab1 = xR + len * 0.64
                    slab(xR, xF, bottom + 0.12, belt, belt, hw, hw * 0.98, colors = solid(body))
                    slab(cab1, xF - 0.03, belt, belt + bh * 0.10, belt + bh * 0.03, hw * 0.98, hw * 0.92, colors = solid(body))
                    slab(cab0, cab1, belt, belt + bh * 0.16, belt + bh * 0.16, hw, hw * 0.97, colors = solid(body))
                    slab(cab0, cab1, belt + bh * 0.16, top, top, hw * 0.96, hw * 0.86, xTopBack = cab0 + 0.10, xTopFront = cab1 - 0.55, colors = cabin(body))
                    // Bed rails and tailgate.
                    for (side in listOf(-1.0, 1.0)) {
                        m.addBox(Vec3((xR + cab0) / 2, belt + bh * 0.09, side * (hw - 0.07)), Vec3(cab0 - xR - 0.08, bh * 0.18, 0.14), body)
                    }
                    m.addBox(Vec3(xR + 0.08, belt + bh * 0.09, 0.0), Vec3(0.14, bh * 0.18, hw * 2), body)
                    m.addBox(Vec3((xR + cab0) / 2, belt + 0.02, 0.0), Vec3(cab0 - xR - 0.3, 0.04, hw * 1.7), RUBBER) // bed liner
                    arches(); mirrors(cab1 - 0.45, belt + bh * 0.22); frontEnd(belt - bh * 0.10); rearEnd(belt + bh * 0.05)
                }
                BodyStyle.SUV, BodyStyle.OFFROAD -> {
                    val belt = bottom + bh * 0.48
                    val cab0 = xR + 0.04
                    val cab1 = xR + len * (if (spec.bodyStyle == BodyStyle.SUV) 0.72 else 0.66)
                    slab(xR, xF, bottom + 0.12, belt, belt, hw, hw * 0.98, colors = solid(body))
                    slab(cab1, xF - 0.03, belt, belt + bh * 0.10, belt + bh * 0.04, hw * 0.98, hw * 0.93, colors = solid(body))
                    slab(cab0, cab1, belt, belt + bh * 0.16, belt + bh * 0.16, hw, hw * 0.97, colors = solid(body))
                    val rake = if (spec.bodyStyle == BodyStyle.SUV) 0.55 else 0.30
                    slab(cab0, cab1, belt + bh * 0.16, top, top, hw * 0.96, hw * 0.88, xTopBack = cab0 + 0.12, xTopFront = cab1 - rake, colors = cabin(body))
                    if (spec.bodyStyle == BodyStyle.OFFROAD) {
                        m.addBox(Vec3((cab0 + cab1) / 2, top + 0.08, 0.0), Vec3((cab1 - cab0) * 0.7, 0.08, hw * 1.6), RUBBER) // roof rack
                        m.addBox(Vec3(xR - 0.16, belt + 0.15, hw * 0.3), Vec3(0.28, r * 1.9, r * 1.9), RUBBER) // spare tyre
                    } else {
                        m.addBox(Vec3((cab0 + cab1) / 2, top + 0.05, 0.0), Vec3((cab1 - cab0) * 0.8, 0.05, hw * 1.3), accent) // roof rails
                    }
                    arches(); mirrors(cab1 - rake, belt + bh * 0.24); frontEnd(belt - bh * 0.10); rearEnd(belt + bh * 0.08)
                }
                BodyStyle.SPORTS -> {
                    val belt = bottom + bh * 0.62
                    val cab0 = xR + len * 0.30; val cab1 = xR + len * 0.60
                    slab(xR, xF, bottom + 0.08, belt, belt - bh * 0.12, hw, hw * 0.94, xTopFront = xF - 0.05, colors = solid(body))
                    slab(cab1, xF - 0.1, belt - bh * 0.12, belt, belt - bh * 0.18, hw * 0.94, hw * 0.86, colors = solid(body))
                    slab(cab0, cab1, belt, top, top, hw * 0.90, hw * 0.74, xTopBack = cab0 + 0.30, xTopFront = cab1 - 0.70, colors = cabin(body))
                    slab(xR + 0.05, cab0, belt, belt + bh * 0.06, belt, hw * 0.96, hw * 0.90, colors = solid(body)) // rear deck
                    mirrors(cab1 - 0.65, belt + 0.08); frontEnd(belt - bh * 0.30); rearEnd(belt - bh * 0.02)
                }
                BodyStyle.MUSCLE, BodyStyle.SEDAN -> {
                    val belt = bottom + bh * 0.56
                    val cab0 = xR + len * 0.26; val cab1 = xR + len * 0.62
                    slab(xR, xF, bottom + 0.10, belt, belt - bh * 0.06, hw, hw * 0.97, colors = solid(body))
                    slab(cab1, xF - 0.05, belt - bh * 0.06, belt + bh * 0.02, belt - bh * 0.10, hw * 0.97, hw * 0.90, colors = solid(body))
                    slab(cab0, cab1, belt, top, top, hw * 0.94, hw * 0.80, xTopBack = cab0 + 0.35, xTopFront = cab1 - 0.62, colors = cabin(body))
                    slab(xR + 0.05, cab0, belt, belt + bh * 0.08, belt + bh * 0.02, hw * 0.98, hw * 0.92, colors = solid(body)) // trunk
                    if (spec.bodyStyle == BodyStyle.MUSCLE) {
                        m.addBox(Vec3(xR + 0.18, belt + bh * 0.16, 0.0), Vec3(0.18, 0.05, hw * 1.7), accent) // spoiler
                    }
                    mirrors(cab1 - 0.55, belt + 0.10); frontEnd(belt - bh * 0.22); rearEnd(belt + bh * 0.02)
                }
            }

            val rest = m.positionsCopy()
            val centre = Vec3((xF + xR) / 2, (top + bottom) / 2, 0.0)
            val halfLen = len / 2
            val weights = Array(m.vertexCount) { i ->
                val x = rest[i * 3].toDouble() - centre.x
                val y = rest[i * 3 + 1].toDouble()
                val z = rest[i * 3 + 2].toDouble()
                val arr = FloatArray(Panel.entries.size)
                arr[Panel.FRONT.ordinal] = smooth(x / halfLen)
                arr[Panel.REAR.ordinal] = smooth(-x / halfLen)
                arr[Panel.RIGHT.ordinal] = smooth(z / hw)
                arr[Panel.LEFT.ordinal] = smooth(-z / hw)
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
            m.addCylinderZ(spec.wheelRadiusM, spec.wheelWidthM, 16, RUBBER, 0xB8BCC2)
            return m
        }
    }
}
