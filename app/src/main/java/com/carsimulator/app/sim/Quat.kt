package com.carsimulator.app.sim

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Unit quaternion for orientation. Rotates local-frame vectors into world frame. */
data class Quat(val w: Double, val x: Double, val y: Double, val z: Double) {

    operator fun times(o: Quat) = Quat(
        w * o.w - x * o.x - y * o.y - z * o.z,
        w * o.x + x * o.w + y * o.z - z * o.y,
        w * o.y - x * o.z + y * o.w + z * o.x,
        w * o.z + x * o.y - y * o.x + z * o.w,
    )

    fun conjugate() = Quat(w, -x, -y, -z)

    fun normalized(): Quat {
        val n = sqrt(w * w + x * x + y * y + z * z)
        return if (n < 1e-12) IDENTITY else Quat(w / n, x / n, y / n, z / n)
    }

    /** Rotate [v] from local into world frame. */
    fun rotate(v: Vec3): Vec3 {
        // v' = v + 2w (u × v) + 2 (u × (u × v)),  u = (x, y, z)
        val u = Vec3(x, y, z)
        val t = (u cross v) * 2.0
        return v + t * w + (u cross t)
    }

    /** Rotate [v] from world into local frame. */
    fun inverseRotate(v: Vec3): Vec3 = conjugate().rotate(v)

    val forward: Vec3 get() = rotate(Vec3.X)
    val up: Vec3 get() = rotate(Vec3.Y)
    val right: Vec3 get() = rotate(Vec3.Z)

    /**
     * Integrate angular velocity [omega] (world frame, rad/s) over [dt].
     * q' = q + 0.5 * (omega as quat) * q * dt
     */
    fun integrate(omega: Vec3, dt: Double): Quat {
        val oq = Quat(0.0, omega.x, omega.y, omega.z)
        val dq = oq * this
        return Quat(
            w + 0.5 * dq.w * dt,
            x + 0.5 * dq.x * dt,
            y + 0.5 * dq.y * dt,
            z + 0.5 * dq.z * dt,
        ).normalized()
    }

    /** Row-major 3x3 rotation matrix as 9 doubles. */
    fun toMatrix3(): DoubleArray {
        val xx = x * x; val yy = y * y; val zz = z * z
        val xy = x * y; val xz = x * z; val yz = y * z
        val wx = w * x; val wy = w * y; val wz = w * z
        return doubleArrayOf(
            1 - 2 * (yy + zz), 2 * (xy - wz), 2 * (xz + wy),
            2 * (xy + wz), 1 - 2 * (xx + zz), 2 * (yz - wx),
            2 * (xz - wy), 2 * (yz + wx), 1 - 2 * (xx + yy),
        )
    }

    companion object {
        val IDENTITY = Quat(1.0, 0.0, 0.0, 0.0)

        fun axisAngle(axis: Vec3, radians: Double): Quat {
            val a = axis.normalized()
            val h = radians / 2
            val s = sin(h)
            return Quat(cos(h), a.x * s, a.y * s, a.z * s)
        }
    }
}
