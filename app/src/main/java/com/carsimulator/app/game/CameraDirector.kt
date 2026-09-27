package com.carsimulator.app.game

import com.carsimulator.app.sim.SimVehicle
import com.carsimulator.app.sim.Vec3
import kotlin.math.exp

enum class CameraMode { CHASE, SIDE }

data class CameraState(val eye: Vec3, val target: Vec3, val fovDegrees: Double)

/**
 * Decides where the camera sits. Chase mode hangs above and behind the car
 * while the player steers; side mode cuts to a trackside view and pulls back
 * so the whole crash stays in frame.
 */
class CameraDirector {
    var mode: CameraMode = CameraMode.CHASE
        private set

    var state = CameraState(Vec3(-12.0, 4.0, 0.0), Vec3.ZERO, 60.0)
        private set

    private var heading = Vec3.X
    private var sideDistance = 14.0
    private var sideDistanceTarget = 14.0
    private var sideSign = 1.0
    private var cut = true

    fun reset() {
        mode = CameraMode.CHASE
        heading = Vec3.X
        cut = true
    }

    /** Snap to the trackside view; zoom-out distance scales with speed. */
    fun switchToSide(vehicle: SimVehicle) {
        if (mode == CameraMode.SIDE) return
        mode = CameraMode.SIDE
        // Stand on the side the car is not drifting toward so it comes toward the lens.
        sideSign = if (vehicle.position.z >= 0) -1.0 else 1.0
        sideDistance = 10.0
        sideDistanceTarget = (14.0 + vehicle.speed * 0.12).coerceIn(14.0, 60.0)
        cut = true
    }

    fun update(dt: Double, vehicle: SimVehicle) {
        val pos = vehicle.position
        val desired: CameraState
        val smoothing: Double
        when (mode) {
            CameraMode.CHASE -> {
                val v = vehicle.velocity.horizontal()
                val wanted = if (v.length > 3.0) v.normalized() else vehicle.orientation.forward.horizontal().normalized()
                heading = Vec3.lerp(heading, wanted, 1 - exp(-dt * 4.0)).normalized()
                val back = 8.0 + vehicle.speed * 0.06
                val height = 3.4 + vehicle.speed * 0.02
                desired = CameraState(
                    eye = pos + Vec3(0.0, height, 0.0) - heading * back,
                    target = pos + heading * 7.0 + Vec3(0.0, 0.6, 0.0),
                    fovDegrees = 62.0,
                )
                smoothing = 7.0
            }
            CameraMode.SIDE -> {
                sideDistance += (sideDistanceTarget - sideDistance) * (1 - exp(-dt * 2.5))
                val lead = vehicle.velocity.x * 0.12
                desired = CameraState(
                    eye = Vec3(pos.x + lead, 3.0 + sideDistance * 0.18, sideSign * sideDistance),
                    target = Vec3(pos.x + lead * 0.5, pos.y, pos.z),
                    fovDegrees = 55.0,
                )
                smoothing = 5.0
            }
        }
        state = if (cut) {
            cut = false
            desired
        } else {
            val a = 1 - exp(-dt * smoothing)
            CameraState(
                eye = Vec3.lerp(state.eye, desired.eye, a),
                target = Vec3.lerp(state.target, desired.target, a),
                fovDegrees = state.fovDegrees + (desired.fovDegrees - state.fovDegrees) * a,
            )
        }
    }
}
