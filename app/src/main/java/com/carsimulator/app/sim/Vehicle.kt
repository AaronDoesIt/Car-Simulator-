package com.carsimulator.app.sim

import kotlin.math.max
import kotlin.math.min

/**
 * Immutable snapshot of the simulated car.
 *
 * Kept free of Android imports so the physics can be unit tested on the JVM
 * and reused by any UI layer.
 *
 * @property speedMps forward speed in metres per second, never negative.
 * @property gear currently engaged forward gear, 1..[VehicleSpec.gearCount].
 * @property throttle driver throttle input, 0.0 (off) to 1.0 (floored).
 * @property brake driver brake input, 0.0 (off) to 1.0 (full).
 */
data class Vehicle(
    val speedMps: Double = 0.0,
    val gear: Int = 1,
    val throttle: Double = 0.0,
    val brake: Double = 0.0,
) {
    val speedKmh: Double get() = speedMps * 3.6

    /**
     * Fraction of the current gear's speed band that is being used, 0.0..1.0.
     * Drives the tachometer in the UI and gear changes in [step].
     */
    fun rpmFraction(spec: VehicleSpec): Double {
        val band = spec.gearTopSpeedMps(gear)
        val floor = spec.gearTopSpeedMps(gear - 1)
        if (band <= floor) return 0.0
        return ((speedMps - floor) / (band - floor)).coerceIn(0.0, 1.0)
    }
}

/** Tunable constants describing how the car behaves. */
data class VehicleSpec(
    /** Peak acceleration at full throttle in first gear, m/s². */
    val maxAccelerationMps2: Double = 6.0,
    /** Deceleration at full brake, m/s². */
    val maxBrakingMps2: Double = 9.0,
    /** Deceleration from rolling and air resistance when coasting, m/s². */
    val coastDecelerationMps2: Double = 0.8,
    /** Number of forward gears. */
    val gearCount: Int = 6,
    /** Top speed of the highest gear, m/s. Defaults to 216 km/h. */
    val topSpeedMps: Double = 60.0,
) {
    init {
        require(gearCount >= 1) { "gearCount must be at least 1" }
        require(topSpeedMps > 0) { "topSpeedMps must be positive" }
    }

    /**
     * Speed at which [gear] runs out of revs. Gear 0 is a virtual "below first"
     * gear whose top speed is zero, which simplifies band arithmetic.
     */
    fun gearTopSpeedMps(gear: Int): Double {
        if (gear <= 0) return 0.0
        val clamped = min(gear, gearCount)
        return topSpeedMps * clamped / gearCount
    }
}

/**
 * Advances [vehicle] by [dtSeconds] using simple, deterministic kinematics.
 *
 * Throttle and brake are applied as accelerations; higher gears pull less
 * strongly so the car is quick off the line and slower to reach top speed.
 * Gear changes happen automatically at the edges of each gear's speed band.
 */
fun Vehicle.step(spec: VehicleSpec, dtSeconds: Double): Vehicle {
    require(dtSeconds >= 0) { "dtSeconds must not be negative" }
    if (dtSeconds == 0.0) return this

    val gearPull = 1.0 / gear
    val drive = throttle * spec.maxAccelerationMps2 * gearPull
    val braking = brake * spec.maxBrakingMps2
    val coast = if (throttle == 0.0) spec.coastDecelerationMps2 else 0.0

    val acceleration = drive - braking - coast
    val rawSpeed = speedMps + acceleration * dtSeconds
    val newSpeed = rawSpeed.coerceIn(0.0, spec.topSpeedMps)

    var newGear = gear
    while (newGear < spec.gearCount && newSpeed >= spec.gearTopSpeedMps(newGear)) {
        newGear++
    }
    while (newGear > 1 && newSpeed < spec.gearTopSpeedMps(newGear - 1) * 0.9) {
        newGear--
    }

    return copy(speedMps = max(0.0, newSpeed), gear = newGear)
}
