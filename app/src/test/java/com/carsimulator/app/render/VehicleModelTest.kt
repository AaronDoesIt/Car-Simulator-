package com.carsimulator.app.render

import com.carsimulator.app.sim.DamageModel
import com.carsimulator.app.sim.Vec3
import com.carsimulator.app.sim.VehicleCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class VehicleModelTest {

    /** A textured quad in the CSM1 layout the import script writes. */
    private fun quadBytes(vertexCount: Int = 4, indices: ShortArray = shortArrayOf(0, 1, 2, 0, 2, 3)): ByteArray {
        val buf = ByteBuffer.allocate(12 + vertexCount * 32 + indices.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        buf.put('C'.code.toByte()).put('S'.code.toByte()).put('M'.code.toByte()).put('1'.code.toByte())
        buf.putInt(vertexCount).putInt(indices.size)
        val corners = listOf(
            floatArrayOf(-1f, 0f, -1f, 0f, 1f, 0f, 0f, 0f),
            floatArrayOf(1f, 0f, -1f, 0f, 1f, 0f, 1f, 0f),
            floatArrayOf(1f, 0f, 1f, 0f, 1f, 0f, 1f, 1f),
            floatArrayOf(-1f, 0f, 1f, 0f, 1f, 0f, 0f, 1f),
        )
        for (i in 0 until vertexCount) for (f in corners[i % 4]) buf.putFloat(f)
        for (s in indices) buf.putShort(s)
        return buf.array()
    }

    @Test
    fun `parses vertices, uvs and indices into a textured mesh`() {
        val m = VehicleModel.parse(quadBytes())
        assertEquals(4, m.vertexCount)
        assertEquals(6, m.indexCount)
        val v = m.vertexArray()
        val s = MeshData.STRIDE
        assertEquals(Material.TEXTURED, m.materialAt(2), 0f)
        assertEquals(1f, v[2 * s + MeshData.UV_OFFSET], 0f)
        assertEquals(1f, v[2 * s + MeshData.UV_OFFSET + 1], 0f)
        assertEquals(Vec3(1.0, 0.0, 1.0), m.positionAt(2))
        assertEquals(Vec3.Y, m.normalAt(2))
        assertTrue(m.indexArray().contentEquals(shortArrayOf(0, 1, 2, 0, 2, 3)))
    }

    @Test
    fun `rejects garbage, wrong magic, bad sizes and out-of-range indices`() {
        assertThrows(IllegalArgumentException::class.java) { VehicleModel.parse(ByteArray(3)) }
        val wrongMagic = quadBytes().also { it[0] = 'X'.code.toByte() }
        assertThrows(IllegalArgumentException::class.java) { VehicleModel.parse(wrongMagic) }
        val truncated = quadBytes().copyOf(60)
        assertThrows(IllegalArgumentException::class.java) { VehicleModel.parse(truncated) }
        assertThrows(IllegalArgumentException::class.java) { VehicleModel.parse(quadBytes(indices = shortArrayOf(0, 1, 9))) }
    }

    @Test
    fun `an imported body crumples with the same panel weights as a built one`() {
        val spec = VehicleCatalog.byId("s10_2001_lifted")
        assertEquals("vehicles/s10_2001_lifted", spec.modelAsset)
        // Two quads: one at the nose, one at the tail, in the body frame.
        val nose = spec.frontAxleX + spec.frontOverhang
        val tail = spec.rearAxleX - spec.rearOverhang
        val m = MeshData()
        for (x in listOf(nose, tail)) {
            val a = m.addVertex(Vec3(x, 0.0, -0.5), Vec3.X, 1f, 1f, 1f, Material.TEXTURED)
            val b = m.addVertex(Vec3(x, 0.5, -0.5), Vec3.X, 1f, 1f, 1f, Material.TEXTURED)
            val c = m.addVertex(Vec3(x, 0.5, 0.5), Vec3.X, 1f, 1f, 1f, Material.TEXTURED)
            val d = m.addVertex(Vec3(x, 0.0, 0.5), Vec3.X, 1f, 1f, 1f, Material.TEXTURED)
            m.addQuad(a, b, c, d)
        }
        val vm = VehicleMesh.fromModel(spec, m)
        val target = m.vertexArray()
        val damage = DamageModel(spec)
        damage.addImpact(Vec3(spec.lengthM / 2, 0.0, 0.0), 0.5 * spec.massKg * 40.0 * 40.0)
        vm.deform(damage, target)
        val s = MeshData.STRIDE
        val noseMoved = kotlin.math.abs(target[0] - nose.toFloat())
        val tailMoved = kotlin.math.abs(target[4 * s] - tail.toFloat())
        assertTrue("nose crumples", noseMoved > 0.05f)
        assertTrue("tail does not", tailMoved < 1e-4f)
    }
}
