package com.carsimulator.app.render

import com.carsimulator.app.game.LevelCatalog
import com.carsimulator.app.sim.DamageModel
import com.carsimulator.app.sim.Vec3
import com.carsimulator.app.sim.VehicleCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MeshBuildersTest {

    @Test
    fun `box has 24 vertices and 12 triangles`() {
        val m = MeshData()
        m.addBox(Vec3.ZERO, Vec3(1.0, 1.0, 1.0), 0xFF0000)
        assertEquals(24, m.vertexCount)
        assertEquals(36, m.indexCount)
        val idx = m.indexArray()
        assertTrue(idx.all { it in 0 until 24 })
    }

    @Test
    fun `track meshes stay under the 16-bit index limit at every level`() {
        for (level in listOf(1, 6, 12, 20, 30)) {
            val track = LevelCatalog.build(level).track
            val meshes = TrackMeshBuilder.build(track)
            assertTrue("level $level has meshes", meshes.isNotEmpty())
            for (m in meshes) {
                assertTrue("level $level chunk under limit: ${m.vertexCount}", m.vertexCount < 65535)
                val idx = m.indexArray()
                assertTrue(idx.all { it.toInt() and 0xFFFF < m.vertexCount })
                val v = m.vertexArray()
                assertTrue(v.all { it.isFinite() })
            }
        }
    }

    @Test
    fun `track mesh samples the obstacle heights`() {
        val level = LevelCatalog.build(2) // monster bump
        val meshes = TrackMeshBuilder.build(level.track)
        var maxY = 0f
        for (m in meshes) {
            val v = m.vertexArray()
            for (i in 0 until m.vertexCount) maxY = maxOf(maxY, v[i * MeshData.STRIDE + 1])
        }
        val bump = level.track.obstacles.first()
        val peak = level.track.heightAt((bump.startX + bump.endX) / 2, 0.0)
        assertEquals(peak.toFloat(), maxY, 0.05f)
    }

    @Test
    fun `every vehicle builds a body and deforms without blowing up`() {
        for (spec in VehicleCatalog.all) {
            val vm = VehicleMesh.build(spec)
            assertTrue("${spec.id} has geometry", vm.data.vertexCount > 100)
            val target = vm.data.vertexArray()
            val undamaged = target.copyOf()

            val damage = DamageModel(spec)
            vm.deform(damage, target)
            for (i in target.indices) assertEquals("no damage means no change", undamaged[i], target[i], 1e-6f)

            damage.addImpact(Vec3(spec.lengthM / 2, 0.0, 0.0), 0.5 * spec.massKg * 40.0 * 40.0)
            vm.deform(damage, target)
            var moved = 0
            for (i in 0 until vm.data.vertexCount) {
                val dx = target[i * MeshData.STRIDE] - undamaged[i * MeshData.STRIDE]
                if (kotlin.math.abs(dx) > 1e-4f) moved++
                assertTrue(target[i * MeshData.STRIDE].isFinite())
            }
            assertTrue("${spec.id} front-end crumpled some vertices", moved > 10)
            assertTrue("${spec.id} but not all of them", moved < vm.data.vertexCount)

            val wheel = VehicleMesh.buildWheel(spec)
            assertTrue(wheel.indexCount > 0)
        }
    }
}
