package com.carsimulator.app.sim

/**
 * Six-degree-of-freedom rigid body with a diagonal (box) inertia tensor.
 *
 * Forces and torques accumulate between calls to [integrate]; [clearForces]
 * runs automatically after integration.
 */
class RigidBody(
    val mass: Double,
    /** Local-frame principal inertia (kg·m²) for a box: Ixx, Iyy, Izz. */
    private val inertia: Vec3,
    var position: Vec3 = Vec3.ZERO,
    var orientation: Quat = Quat.IDENTITY,
    var velocity: Vec3 = Vec3.ZERO,
    /** Angular velocity in world frame, rad/s. */
    var angularVelocity: Vec3 = Vec3.ZERO,
) {
    private var force = Vec3.ZERO
    private var torque = Vec3.ZERO

    val invMass = 1.0 / mass
    private val invInertiaLocal = Vec3(1.0 / inertia.x, 1.0 / inertia.y, 1.0 / inertia.z)

    fun toWorld(local: Vec3): Vec3 = position + orientation.rotate(local)
    fun toLocal(world: Vec3): Vec3 = orientation.inverseRotate(world - position)

    /** Velocity of the body material at a world-space point. */
    fun velocityAt(worldPoint: Vec3): Vec3 =
        velocity + (angularVelocity cross (worldPoint - position))

    fun applyForce(worldForce: Vec3, worldPoint: Vec3) {
        force += worldForce
        torque += (worldPoint - position) cross worldForce
    }

    fun applyCentralForce(worldForce: Vec3) {
        force += worldForce
    }

    fun applyImpulse(worldImpulse: Vec3, worldPoint: Vec3) {
        velocity += worldImpulse * invMass
        val angImpulse = (worldPoint - position) cross worldImpulse
        angularVelocity += worldInvInertia(angImpulse)
    }

    /** Multiply a world-frame vector by the world-frame inverse inertia tensor. */
    fun worldInvInertia(v: Vec3): Vec3 {
        val local = orientation.inverseRotate(v)
        return orientation.rotate(local.scale(invInertiaLocal))
    }

    fun integrate(dt: Double, gravity: Vec3, angularDamping: Double = 0.02) {
        velocity += (force * invMass + gravity) * dt
        angularVelocity += worldInvInertia(torque) * dt
        angularVelocity *= (1.0 - angularDamping * dt).coerceAtLeast(0.0)
        position += velocity * dt
        orientation = orientation.integrate(angularVelocity, dt)
        clearForces()
    }

    fun clearForces() {
        force = Vec3.ZERO
        torque = Vec3.ZERO
    }

    val speed: Double get() = velocity.length

    companion object {
        /** Solid-box inertia about its centre. */
        fun boxInertia(mass: Double, sizeX: Double, sizeY: Double, sizeZ: Double): Vec3 = Vec3(
            mass / 12.0 * (sizeY * sizeY + sizeZ * sizeZ),
            mass / 12.0 * (sizeX * sizeX + sizeZ * sizeZ),
            mass / 12.0 * (sizeX * sizeX + sizeY * sizeY),
        )
    }
}
