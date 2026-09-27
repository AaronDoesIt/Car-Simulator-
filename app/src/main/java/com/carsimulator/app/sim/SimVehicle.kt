package com.carsimulator.app.sim

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

/** One corner's wheel and suspension. Positions in the body frame unless stated. */
class Wheel(
    val index: Int,
    /** Top of the suspension ray in the body frame. */
    val attachLocal: Vec3,
    val steerable: Boolean,
    val driven: Boolean,
    val radius: Double,
) {
    var compression = 0.0
    var previousCompression = 0.0
    var inContact = false
    var contactPoint = Vec3.ZERO
    var contactNormal = Vec3.Y
    var loadN = 0.0
    var spinAngle = 0.0
    var steerAngle = 0.0

    var detached = false
    /** World-space state once the wheel has been torn off. */
    var freePosition = Vec3.ZERO
    var freeVelocity = Vec3.ZERO
    var freeSpin = 0.0

    /** World-space wheel centre for rendering. */
    var centerWorld = Vec3.ZERO
}

/** What the vehicle reports to the game after each frame. */
data class VehicleEvents(
    val chassisImpacts: Int = 0,
    val strongestImpactSpeed: Double = 0.0,
    val wheelsDetachedThisFrame: Int = 0,
    val boostedThisFrame: Boolean = false,
)

/**
 * The car: a rigid chassis on four raycast wheels driving over a [Track].
 *
 * The body frame has X forward, Y up, Z right with the centre of gravity at
 * the origin. All tuning comes from the [spec]; nothing is hard-coded per car.
 */
class SimVehicle(
    val spec: VehicleSpec,
    val track: Track,
    startX: Double = 0.0,
    startZ: Double = 0.0,
) {
    val body: RigidBody
    val wheels: List<Wheel>
    val damage = DamageModel(spec)

    /** Finger steering, -1 (full left) .. 1 (full right). */
    var steerInput = 0.0
    var throttle = 0.0
    var brake = 0.0

    var boosted = false
        private set
    var airTime = 0.0
        private set
    var longestAirTime = 0.0
        private set
    var maxSpeed = 0.0
        private set
    var rolledOver = false
        private set
    var simulatedTime = 0.0
        private set

    /** Chassis contact points in the body frame. */
    private val contactPointsLocal: List<Vec3>
    private val contactWasTouching: BooleanArray
    private val chassisStiffness: Double
    private val chassisDamping: Double
    private val wheelDetachLoadN = spec.wheelDetachLoadG * spec.massKg * VehicleSpec.GRAVITY

    private val halfWidth = spec.widthM / 2
    private val bodyBottomY = -(spec.cgHeightM - spec.groundClearanceM)
    private val bodyTopY = spec.heightM - spec.cgHeightM

    init {
        val inertia = RigidBody.boxInertia(spec.massKg, spec.lengthM, spec.heightM * 0.8, spec.widthM)
        body = RigidBody(
            mass = spec.massKg,
            inertia = inertia,
            position = Vec3(startX, spec.cgHeightM + track.heightAt(startX, startZ), startZ),
        )

        val attachY = -spec.cgHeightM + spec.wheelRadiusM + spec.suspensionRestM - spec.staticSagM
        val halfTrack = spec.trackWidthM / 2
        wheels = listOf(
            Wheel(0, Vec3(spec.frontAxleX, attachY, -halfTrack), steerable = true, driven = false, radius = spec.wheelRadiusM),
            Wheel(1, Vec3(spec.frontAxleX, attachY, halfTrack), steerable = true, driven = false, radius = spec.wheelRadiusM),
            Wheel(2, Vec3(spec.rearAxleX, attachY, -halfTrack), steerable = false, driven = true, radius = spec.wheelRadiusM),
            Wheel(3, Vec3(spec.rearAxleX, attachY, halfTrack), steerable = false, driven = true, radius = spec.wheelRadiusM),
        )
        wheels.forEach { it.compression = spec.staticSagM; it.previousCompression = spec.staticSagM }

        val xF = spec.frontAxleX + spec.frontOverhang
        val xR = spec.rearAxleX - spec.rearOverhang
        val xMid = (xF + xR) / 2
        contactPointsLocal = listOf(
            // Bottom corners and bottom edge midpoints.
            Vec3(xF, bodyBottomY, -halfWidth), Vec3(xF, bodyBottomY, halfWidth),
            Vec3(xR, bodyBottomY, -halfWidth), Vec3(xR, bodyBottomY, halfWidth),
            Vec3(xMid, bodyBottomY, -halfWidth), Vec3(xMid, bodyBottomY, halfWidth),
            Vec3(xF, bodyBottomY, 0.0), Vec3(xR, bodyBottomY, 0.0),
            // Roof corners.
            Vec3(xF * 0.6, bodyTopY, -halfWidth * 0.9), Vec3(xF * 0.6, bodyTopY, halfWidth * 0.9),
            Vec3(xR * 0.6, bodyTopY, -halfWidth * 0.9), Vec3(xR * 0.6, bodyTopY, halfWidth * 0.9),
            // Bumper faces at mid height.
            Vec3(xF, (bodyBottomY + bodyTopY) * 0.5, 0.0), Vec3(xR, (bodyBottomY + bodyTopY) * 0.5, 0.0),
        )
        contactWasTouching = BooleanArray(contactPointsLocal.size)
        chassisStiffness = spec.massKg * 350.0
        chassisDamping = 2.0 * sqrt(chassisStiffness * spec.massKg / 6.0) * 0.6
    }

    val position: Vec3 get() = body.position
    val velocity: Vec3 get() = body.velocity
    val orientation: Quat get() = body.orientation
    val speed: Double get() = body.speed
    val forwardSpeed: Double get() = body.velocity dot body.orientation.forward
    val wheelsOnGround: Int get() = wheels.count { !it.detached && it.inContact }
    val wheelsLost: Int get() = wheels.count { it.detached }
    val isAirborne: Boolean get() = wheelsOnGround == 0 && !anyChassisContact
    private var anyChassisContact = false

    /**
     * Advance by one rendered frame. Sub-steps so that at very high speed the
     * wheels never skip more than ~8 cm between samples.
     */
    fun advance(frameDt: Double): VehicleEvents {
        val dtClamped = frameDt.coerceIn(0.0, 0.1)
        if (dtClamped == 0.0) return VehicleEvents()
        val travel = dtClamped * max(speed, 5.0)
        val substeps = ceil(travel / 0.08).toInt().coerceIn(4, 200)
        val dt = dtClamped / substeps
        var impacts = 0
        var strongest = 0.0
        var detachedNow = 0
        var boostedNow = false
        repeat(substeps) {
            val r = step(dt)
            impacts += r.chassisImpacts
            strongest = max(strongest, r.strongestImpactSpeed)
            detachedNow += r.wheelsDetachedThisFrame
            boostedNow = boostedNow || r.boostedThisFrame
        }
        return VehicleEvents(impacts, strongest, detachedNow, boostedNow)
    }

    private fun step(dt: Double): VehicleEvents {
        simulatedTime += dt
        val gravity = Vec3(0.0, -VehicleSpec.GRAVITY, 0.0)
        val q = body.orientation
        val up = q.up

        val steerAngle = steerInput.coerceIn(-1.0, 1.0) * MAX_STEER_RAD / (1.0 + abs(forwardSpeed) / 22.0)
        var detachedNow = 0

        // --- Wheels ----------------------------------------------------------------
        for (w in wheels) {
            if (w.detached) {
                stepFreeWheel(w, dt, gravity)
                continue
            }
            w.steerAngle = if (w.steerable) steerAngle else 0.0
            val attachW = body.toWorld(w.attachLocal)
            val down = -up
            val rayLength = spec.suspensionRestM + w.radius

            // Two-iteration march down the ray to the heightfield.
            w.inContact = false
            if (down.y < -0.2) {
                var t = (attachW.y - track.heightAt(attachW.x, attachW.z)) / -down.y
                var p = attachW + down * t
                t += (p.y - track.heightAt(p.x, p.z)) / -down.y
                p = attachW + down * t
                if (t <= rayLength) {
                    w.inContact = true
                    w.contactPoint = p
                    w.contactNormal = track.normalAt(p.x, p.z)
                    w.previousCompression = w.compression
                    w.compression = (rayLength - t).coerceIn(0.0, spec.suspensionRestM)
                    val bottomOut = max(0.0, w.radius - t)

                    val compVel = (w.compression - w.previousCompression) / dt
                    var force = spec.springRateNPerM * w.compression + spec.damperNsPerM * compVel
                    if (bottomOut > 0) force += spec.springRateNPerM * 25.0 * bottomOut
                    force = max(0.0, force)
                    w.loadN = force
                    body.applyForce(up * force, w.contactPoint)

                    if (bottomOut > 0.02) {
                        val local = body.toLocal(w.contactPoint)
                        val vn = body.velocityAt(w.contactPoint) dot w.contactNormal
                        damage.addImpact(local, 0.5 * spec.massKg / 4 * vn * vn * (bottomOut / w.radius))
                    }
                    if (force > wheelDetachLoadN) {
                        detachWheel(w)
                        detachedNow++
                        continue
                    }
                    applyTyreForces(w, dt)
                } else {
                    w.compression = 0.0
                    w.previousCompression = 0.0
                    w.loadN = 0.0
                }
            } else {
                w.loadN = 0.0
            }
            w.centerWorld = if (w.inContact) w.contactPoint + up * w.radius
            else attachW + down * spec.suspensionRestM
            if (w.inContact) w.spinAngle += (body.velocityAt(w.centerWorld) dot q.forward) / w.radius * dt
        }

        // --- Chassis against the ground ---------------------------------------------
        var impacts = 0
        var strongest = 0.0
        anyChassisContact = false
        for ((i, local) in contactPointsLocal.withIndex()) {
            val p = body.toWorld(local)
            val h = track.heightAt(p.x, p.z)
            val pen = h - p.y
            if (pen <= 0) { contactWasTouching[i] = false; continue }
            anyChassisContact = true
            val n = track.normalAt(p.x, p.z)
            val v = body.velocityAt(p)
            val vn = v dot n
            var fn = chassisStiffness * pen
            if (vn < 0) fn -= chassisDamping * vn
            val vt = v - n * vn
            val vtLen = vt.length
            val ft = if (vtLen > 1e-3) vt * (-CHASSIS_FRICTION * fn / vtLen) else Vec3.ZERO
            body.applyForce(n * fn + ft, p)

            if (!contactWasTouching[i]) {
                contactWasTouching[i] = true
                if (vn < -1.0) {
                    impacts++
                    strongest = max(strongest, -vn)
                    damage.addImpact(local, 0.5 * spec.massKg / 4 * vn * vn)
                }
            } else if (vtLen > 0.5) {
                damage.addScrape(local, fn, vtLen, dt)
            }
        }

        // --- Aerodynamic drag ---------------------------------------------------------
        val v = body.velocity
        val vLen = v.length
        if (vLen > 0.1) {
            val dragN = 0.5 * AIR_DENSITY * spec.dragCoefficient * spec.frontalAreaM2 * vLen * vLen * DRAG_SCALE
            body.applyCentralForce(v * (-dragN / vLen))
        }

        // --- Boost strip --------------------------------------------------------------
        var boostedNow = false
        if (!boosted && track.boost.contains(body.position.x) && wheelsOnGround >= 2) {
            val fwd = q.forward.horizontal().normalized()
            body.velocity = fwd * track.boost.targetSpeedMps + Vec3(0.0, body.velocity.y, 0.0)
            boosted = true
            boostedNow = true
        }

        body.integrate(dt, gravity)

        // --- Bookkeeping --------------------------------------------------------------
        maxSpeed = max(maxSpeed, body.speed)
        if (isAirborne) {
            airTime += dt
            longestAirTime = max(longestAirTime, airTime)
        } else {
            airTime = 0.0
        }
        if (body.orientation.up.y < 0.15) rolledOver = true

        return VehicleEvents(impacts, strongest, detachedNow, boostedNow)
    }

    private fun applyTyreForces(w: Wheel, dt: Double) {
        val q = body.orientation
        val n = w.contactNormal
        val wheelFwdLocal = Vec3(cos(w.steerAngle), 0.0, sin(w.steerAngle))
        val wf = q.rotate(wheelFwdLocal)
        val fwd = (wf - n * (wf dot n)).normalized()
        if (fwd.lengthSquared < 1e-6) return
        val lat = (n cross fwd).normalized()

        val vc = body.velocityAt(w.contactPoint)
        val vf = vc dot fwd
        val vl = vc dot lat
        val maxFriction = spec.tyreGrip * w.loadN

        // Lateral: linear cornering stiffness saturating smoothly at the grip limit.
        val slipAngle = atan2(vl, abs(vf) + 0.8)
        val cornering = w.loadN * CORNERING_STIFFNESS_PER_N
        val fy = if (maxFriction > 1e-6) -maxFriction * tanh(cornering * slipAngle / maxFriction) else 0.0

        // Longitudinal: drive, brake and rolling resistance.
        var fx = 0.0
        if (w.driven) fx += throttle * spec.engineForceN / 2.0
        fx -= sign(vf) * brake * maxFriction
        fx -= sign(vf) * ROLLING_RESISTANCE * w.loadN
        // Kill creep when nearly stopped with the brakes on.
        if (abs(vf) < 0.3 && throttle == 0.0) fx = -vf * w.loadN * 0.5

        var fxC = fx
        var fyC = fy
        val total = sqrt(fx * fx + fy * fy)
        if (total > maxFriction && total > 1e-9) {
            fxC = fx * maxFriction / total
            fyC = fy * maxFriction / total
        }
        body.applyForce(fwd * fxC + lat * fyC, w.contactPoint)
    }

    private fun detachWheel(w: Wheel) {
        w.detached = true
        w.inContact = false
        w.loadN = 0.0
        val centre = w.centerWorld
        w.freePosition = centre
        val kick = body.orientation.up * 6.0 + body.orientation.right * (if (w.attachLocal.z < 0) -4.0 else 4.0)
        w.freeVelocity = body.velocityAt(centre) * 0.6 + kick
        w.freeSpin = 0.0
        damage.addImpact(w.attachLocal, 0.5 * spec.massKg / 4 * 20.0 * 20.0)
    }

    private fun stepFreeWheel(w: Wheel, dt: Double, gravity: Vec3) {
        w.freeVelocity += gravity * dt
        w.freePosition += w.freeVelocity * dt
        val h = track.heightAt(w.freePosition.x, w.freePosition.z)
        if (w.freePosition.y - w.radius < h) {
            w.freePosition = w.freePosition.withY(h + w.radius)
            val n = track.normalAt(w.freePosition.x, w.freePosition.z)
            val vn = w.freeVelocity dot n
            if (vn < 0) {
                val vt = w.freeVelocity - n * vn
                w.freeVelocity = vt * 0.92 - n * vn * 0.35
            }
        }
        w.freeSpin += w.freeVelocity.horizontal().length / w.radius * dt
        w.centerWorld = w.freePosition
        w.spinAngle = w.freeSpin
    }

    /** Whether the vehicle has come to rest (or fallen off the world). */
    fun isSettled(): Boolean =
        (speed < 0.6 && body.angularVelocity.length < 0.3) || body.position.y < -20.0

    companion object {
        const val MAX_STEER_RAD = 0.55
        const val AIR_DENSITY = 1.225
        /** Arcade tweak: real drag would scrub off much of the boost before the obstacles. */
        const val DRAG_SCALE = 0.35
        const val CHASSIS_FRICTION = 0.6
        const val ROLLING_RESISTANCE = 0.015
        /** Cornering stiffness per newton of load, rad⁻¹. */
        const val CORNERING_STIFFNESS_PER_N = 12.0
    }
}
