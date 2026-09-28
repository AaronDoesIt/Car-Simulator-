package com.carsimulator.app.render

import com.carsimulator.app.sim.Vec3
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Reader for the game's compact vehicle mesh asset, written by
 * scripts/import_vehicle_glb.py from an AI-generated GLB:
 *
 *   magic "CSM1", uint32 vertexCount, uint32 indexCount,
 *   vertexCount x (x y z nx ny nz u v) float32, indexCount x uint16.
 *
 * Coordinates are already in the body frame with the wheels cut out, so the
 * result drops straight into [VehicleMesh] as a textured, crumple-able shell.
 */
object VehicleModel {
    private const val MAGIC = 0x314D5343 // "CSM1" little-endian

    fun parse(bytes: ByteArray): MeshData {
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        require(buf.remaining() >= 12) { "vehicle model too short" }
        require(buf.int == MAGIC) { "not a CSM1 vehicle model" }
        val vertexCount = buf.int
        val indexCount = buf.int
        require(vertexCount in 1..65534 && indexCount >= 3 && indexCount % 3 == 0) { "bad vehicle model header" }
        require(buf.remaining() == vertexCount * 8 * 4 + indexCount * 2) { "vehicle model size mismatch" }
        val m = MeshData()
        for (i in 0 until vertexCount) {
            val p = Vec3(buf.float.toDouble(), buf.float.toDouble(), buf.float.toDouble())
            val n = Vec3(buf.float.toDouble(), buf.float.toDouble(), buf.float.toDouble())
            val u = buf.float; val v = buf.float
            m.addVertex(p, n, 1f, 1f, 1f, Material.TEXTURED, u, v)
        }
        for (i in 0 until indexCount / 3) {
            val a = buf.short.toInt() and 0xFFFF
            val b = buf.short.toInt() and 0xFFFF
            val c = buf.short.toInt() and 0xFFFF
            require(a < vertexCount && b < vertexCount && c < vertexCount) { "vehicle model index out of range" }
            m.addTriangle(a, b, c)
        }
        return m
    }
}
