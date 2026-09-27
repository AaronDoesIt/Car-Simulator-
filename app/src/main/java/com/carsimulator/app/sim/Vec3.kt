package com.carsimulator.app.sim

import kotlin.math.sqrt

/** Minimal immutable 3-vector. World axes: X forward along the track, Y up, Z right. */
data class Vec3(val x: Double, val y: Double, val z: Double) {
    operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    operator fun times(s: Double) = Vec3(x * s, y * s, z * s)
    operator fun div(s: Double) = Vec3(x / s, y / s, z / s)
    operator fun unaryMinus() = Vec3(-x, -y, -z)

    infix fun dot(o: Vec3) = x * o.x + y * o.y + z * o.z
    infix fun cross(o: Vec3) = Vec3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)

    val length: Double get() = sqrt(x * x + y * y + z * z)
    val lengthSquared: Double get() = x * x + y * y + z * z

    fun normalized(): Vec3 {
        val l = length
        return if (l < 1e-12) ZERO else this / l
    }

    /** Component-wise multiply. */
    fun scale(o: Vec3) = Vec3(x * o.x, y * o.y, z * o.z)

    fun withY(newY: Double) = Vec3(x, newY, z)

    /** Projection onto the horizontal plane. */
    fun horizontal() = Vec3(x, 0.0, z)

    companion object {
        val ZERO = Vec3(0.0, 0.0, 0.0)
        val X = Vec3(1.0, 0.0, 0.0)
        val Y = Vec3(0.0, 1.0, 0.0)
        val Z = Vec3(0.0, 0.0, 1.0)
        fun lerp(a: Vec3, b: Vec3, t: Double) = a + (b - a) * t
    }
}
