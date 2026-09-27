package com.carsimulator.app.sim

import kotlin.math.abs
import kotlin.math.max

/** Panels of the body that crumple independently. */
enum class Panel { FRONT, REAR, LEFT, RIGHT, ROOF, UNDERBODY }

/**
 * Tracks how crushed each [Panel] is on a 0..1 scale and converts impacts
 * into crush. The renderer reads [level] to deform the mesh; the score
 * reads [total].
 */
class DamageModel(private val spec: VehicleSpec) {

    private val levels = DoubleArray(Panel.entries.size)

    /** Energy that fully crushes one panel: half the mass at [VehicleSpec.crushSpeedMps]. */
    private val crushEnergyJ = 0.5 * spec.massKg * spec.crushSpeedMps * spec.crushSpeedMps

    fun level(panel: Panel): Double = levels[panel.ordinal]

    /** Weighted average crush across the body, 0..1. */
    val total: Double
        get() = (levels[Panel.FRONT.ordinal] * 1.5 +
            levels[Panel.REAR.ordinal] * 1.0 +
            levels[Panel.LEFT.ordinal] * 1.0 +
            levels[Panel.RIGHT.ordinal] * 1.0 +
            levels[Panel.ROOF.ordinal] * 1.2 +
            levels[Panel.UNDERBODY.ordinal] * 0.8) / 6.5

    /**
     * Register an impact on the body at [localPoint] (body frame) carrying
     * [energyJ]. The energy is shared between the panels the point belongs to.
     */
    fun addImpact(localPoint: Vec3, energyJ: Double) {
        if (energyJ <= 0) return
        val halfLen = spec.lengthM / 2
        val halfWid = spec.widthM / 2
        val top = spec.heightM - spec.cgHeightM
        val bottom = -(spec.cgHeightM - spec.groundClearanceM)

        val w = DoubleArray(Panel.entries.size)
        w[Panel.FRONT.ordinal] = max(0.0, localPoint.x / halfLen)
        w[Panel.REAR.ordinal] = max(0.0, -localPoint.x / halfLen)
        w[Panel.RIGHT.ordinal] = max(0.0, localPoint.z / halfWid)
        w[Panel.LEFT.ordinal] = max(0.0, -localPoint.z / halfWid)
        w[Panel.ROOF.ordinal] = if (top > 0) max(0.0, localPoint.y / top) else 0.0
        w[Panel.UNDERBODY.ordinal] = if (bottom < 0) max(0.0, localPoint.y / bottom) else 0.0

        val sum = w.sum()
        if (sum < 1e-9) return
        val frac = energyJ / crushEnergyJ
        for (i in levels.indices) {
            levels[i] = (levels[i] + frac * (w[i] / sum)).coerceIn(0.0, 1.0)
        }
    }

    /** Slow crush from dragging the body along the ground. */
    fun addScrape(localPoint: Vec3, normalForceN: Double, slideSpeed: Double, dt: Double) {
        val energy = abs(normalForceN) * slideSpeed * dt * 0.15
        addImpact(localPoint, energy)
    }

    fun reset() = levels.fill(0.0)
}
