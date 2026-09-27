package com.carsimulator.app.game

import com.carsimulator.app.sim.SimVehicle
import com.carsimulator.app.sim.VehicleSpec
import kotlin.math.max
import kotlin.math.roundToInt

enum class Phase {
    /** Countdown, then throttle to the strip while the player steers. */
    LAUNCH,
    /** Car has reached the obstacles: side camera, slow motion, carnage. */
    IMPACT,
    /** Everything has stopped moving. */
    RESULTS,
}

data class RunResult(
    val level: Int,
    val vehicleName: String,
    val score: Int,
    val damagePercent: Int,
    val wheelsLost: Int,
    val longestAirSeconds: Double,
    val maxSpeedMph: Double,
    val rolledOver: Boolean,
    val strongestImpactMph: Double,
)

/**
 * One attempt at one level with one vehicle. Owns the simulated car, drives
 * the phase machine and the camera, and produces a [RunResult] at the end.
 */
class GameSession(val spec: VehicleSpec, val level: Level) {

    val vehicle = SimVehicle(spec, level.track, startX = -8.0)
    val camera = CameraDirector()

    var phase: Phase = Phase.LAUNCH
        private set

    /** 1.0 real time; drops during the crash for the slow-motion cut. */
    var timeScale = 1.0
        private set

    var elapsed = 0.0
        private set

    private var countdown = COUNTDOWN_SECONDS
    private var impactElapsed = 0.0
    private var settledFor = 0.0
    private var slowMotionLeft = 0.0
    private var strongestImpact = 0.0
    private var impactCount = 0

    val countdownRemaining: Double get() = max(0.0, countdown)

    /** Finger steering input, -1..1. */
    fun setSteer(value: Double) {
        vehicle.steerInput = value.coerceIn(-1.0, 1.0)
    }

    fun update(realDt: Double) {
        val dt = realDt.coerceIn(0.0, 0.1)
        elapsed += dt

        when (phase) {
            Phase.LAUNCH -> {
                if (countdown > 0) {
                    countdown -= dt
                    vehicle.throttle = 0.0
                    vehicle.brake = 1.0
                } else {
                    vehicle.brake = 0.0
                    vehicle.throttle = if (vehicle.boosted) 0.0 else 1.0
                }
                timeScale = 1.0
            }
            Phase.IMPACT -> {
                vehicle.throttle = 0.0
                // Once the carnage is behind it, stand on the brakes so clean runs end promptly.
                if (vehicle.position.x > level.track.obstacleEndX + BRAKE_AFTER_OBSTACLES_M) vehicle.brake = 1.0
                impactElapsed += dt
                if (slowMotionLeft > 0) {
                    slowMotionLeft -= dt
                    timeScale = SLOW_MOTION_SCALE
                } else {
                    timeScale += (1.0 - timeScale) * min(1.0, dt * 3.0)
                }
            }
            Phase.RESULTS -> return
        }

        val events = vehicle.advance(dt * timeScale)
        impactCount += events.chassisImpacts
        strongestImpact = max(strongestImpact, events.strongestImpactSpeed)

        if (phase == Phase.LAUNCH) {
            val reachedObstacles = vehicle.position.x >= level.track.obstacleStartX - vehicle.spec.lengthM
            if (reachedObstacles || events.chassisImpacts > 0 || events.wheelsDetachedThisFrame > 0) {
                phase = Phase.IMPACT
                slowMotionLeft = SLOW_MOTION_SECONDS
                camera.switchToSide(vehicle)
            }
        }
        if (phase == Phase.IMPACT) {
            settledFor = if (vehicle.isSettled()) settledFor + dt else 0.0
            val offTheEnd = vehicle.position.x > level.track.lengthX
            if (settledFor > SETTLE_SECONDS || impactElapsed > MAX_IMPACT_SECONDS || offTheEnd) {
                phase = Phase.RESULTS
            }
        }

        camera.update(dt, vehicle)
    }

    fun result(): RunResult {
        val damage = vehicle.damage.total
        val air = vehicle.longestAirTime
        val score = (damage * 1000 +
            vehicle.wheelsLost * 300 +
            air * 200 +
            (if (vehicle.rolledOver) 500 else 0) +
            strongestImpact * 10).roundToInt()
        return RunResult(
            level = level.number,
            vehicleName = spec.displayName,
            score = score,
            damagePercent = (damage * 100).roundToInt(),
            wheelsLost = vehicle.wheelsLost,
            longestAirSeconds = air,
            maxSpeedMph = vehicle.maxSpeed / Level.MPH_TO_MPS,
            rolledOver = vehicle.rolledOver,
            strongestImpactMph = strongestImpact / Level.MPH_TO_MPS,
        )
    }

    private fun min(a: Double, b: Double) = if (a < b) a else b

    companion object {
        const val COUNTDOWN_SECONDS = 1.5
        const val SLOW_MOTION_SECONDS = 1.6
        const val SLOW_MOTION_SCALE = 0.3
        const val SETTLE_SECONDS = 1.5
        const val MAX_IMPACT_SECONDS = 25.0
        const val BRAKE_AFTER_OBSTACLES_M = 40.0
    }
}
