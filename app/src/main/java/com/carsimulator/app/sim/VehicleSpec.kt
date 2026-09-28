package com.carsimulator.app.sim

import kotlin.math.PI
import kotlin.math.sqrt

/** Silhouette family the mesh builder uses to shape the body. */
enum class BodyStyle { PICKUP, SUV, MUSCLE, SPORTS, OFFROAD, SEDAN }

/**
 * Real-world figures for one vehicle plus the handful of tuning values the
 * simulation derives from them. Dimensions in metres, mass in kilograms.
 *
 * Curb weights and dimensions come from published manufacturer spec sheets
 * (see [VehicleCatalog] for the sources per entry). Suspension figures are
 * engineering estimates: ride frequency and damping ratio are typical for the
 * class, and lifted trucks get longer, softer travel and a taller centre of
 * gravity as a lift kit would.
 */
data class VehicleSpec(
    val id: String,
    val displayName: String,
    val year: Int,
    val make: String,
    val bodyStyle: BodyStyle,
    val massKg: Double,
    val lengthM: Double,
    val widthM: Double,
    val heightM: Double,
    val wheelbaseM: Double,
    val trackWidthM: Double,
    /** Share of curb weight carried by the front axle, 0..1. */
    val frontWeightFraction: Double,
    val wheelRadiusM: Double,
    val wheelWidthM: Double,
    /** Distance from road to the lowest body panel at rest. */
    val groundClearanceM: Double,
    /** Centre of gravity height above the road at rest. */
    val cgHeightM: Double,
    /** Lift kit height added on top of stock, 0 for stock. */
    val liftM: Double = 0.0,
    val rideFrequencyHz: Double = 1.4,
    val dampingRatio: Double = 0.35,
    /** Suspension travel available at rest before the bump stops, metres. */
    val suspensionTravelM: Double = 0.18,
    val tyreGrip: Double = 0.95,
    /** Normal-direction impact speed (m/s) that fully crushes a panel. */
    val crushSpeedMps: Double = 26.0,
    /** Wheel load, as a multiple of the whole vehicle's weight, that rips a wheel off. */
    val wheelDetachLoadG: Double = 7.0,
    val engineForceN: Double = 6000.0,
    val dragCoefficient: Double = 0.45,
    val colorRgb: Int = 0xC0392B,
    val accentRgb: Int = 0x222222,
    val sourceNote: String = "",
    /** Asset base name of an imported body mesh (see scripts/import_vehicle_glb.py); null builds one procedurally. */
    val modelAsset: String? = null,
) {
    val frontAxleX: Double get() = wheelbaseM * (1.0 - frontWeightFraction)
    val rearAxleX: Double get() = -wheelbaseM * frontWeightFraction
    val frontOverhang: Double get() = (lengthM - wheelbaseM) * 0.45
    val rearOverhang: Double get() = (lengthM - wheelbaseM) * 0.55

    /** Per-corner spring rate from ride frequency: k = m (2πf)². */
    val springRateNPerM: Double get() = (massKg / 4.0) * (2 * PI * rideFrequencyHz).let { it * it }
    val damperNsPerM: Double get() = 2.0 * dampingRatio * sqrt(springRateNPerM * massKg / 4.0)

    /** Static sag under curb weight. */
    val staticSagM: Double get() = (massKg / 4.0) * GRAVITY / springRateNPerM

    /** Rest length of the suspension ray beyond the wheel radius. */
    val suspensionRestM: Double get() = suspensionTravelM + staticSagM

    /** Frontal area for drag: width × height with a 0.85 fill factor. */
    val frontalAreaM2: Double get() = widthM * heightM * 0.85

    companion object {
        const val GRAVITY = 9.81
    }
}
