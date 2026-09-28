package com.carsimulator.app.render

import com.carsimulator.app.game.LevelCatalog
import com.carsimulator.app.sim.DamageModel
import com.carsimulator.app.sim.Vec3
import com.carsimulator.app.sim.VehicleCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class MeshBuildersTest {

    private val s = MeshData.STRIDE

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
            for (i in 0 until m.vertexCount) {
                val onRoad = abs(v[i * s + 2]) <= level.track.roadHalfWidth
                if (onRoad) maxY = maxOf(maxY, v[i * s + 1])
            }
        }
        val bump = level.track.obstacles.first()
        val peak = level.track.heightAt((bump.startX + bump.endX) / 2, 0.0)
        assertEquals(peak.toFloat(), maxY, 0.05f)
    }

    @Test
    fun `track mesh tags road as asphalt and hills as terrain`() {
        val track = LevelCatalog.build(1).track
        val road = TrackMeshBuilder.surfaceAt(track, 5.0, 3.0, Vec3.Y)
        assertEquals(Material.ASPHALT, road.material, 0f)
        val boost = TrackMeshBuilder.surfaceAt(track, (track.boost.startX + track.boost.endX) / 2, 0.0, Vec3.Y)
        assertEquals(Material.LAMP, boost.material, 0f)
        val hill = TrackMeshBuilder.surfaceAt(track, 5.0, 60.0, track.normalAt(5.0, 60.0, 1.0))
        assertTrue("terrain code carries the rock blend in 0..0.9", hill.material in 0f..0.9f)
        // The grid reaches far enough sideways that its edge is inside the fog.
        assertTrue(TrackMeshBuilder.lateralSamples().last() >= 1000.0)
    }

    @Test
    fun `every vehicle builds a body and deforms without blowing up`() {
        for (spec in VehicleCatalog.all) {
            val vm = VehicleMesh.build(spec)
            assertTrue("${spec.id} has geometry", vm.data.vertexCount > 1000)
            assertTrue("${spec.id} under index limit", vm.data.vertexCount < 65535)
            val target = vm.data.vertexArray()
            val undamaged = target.copyOf()
            assertTrue(undamaged.all { it.isFinite() })

            val damage = DamageModel(spec)
            vm.deform(damage, target)
            for (i in target.indices) assertEquals("no damage means no change", undamaged[i], target[i], 1e-6f)

            damage.addImpact(Vec3(spec.lengthM / 2, 0.0, 0.0), 0.5 * spec.massKg * 40.0 * 40.0)
            vm.deform(damage, target)
            var moved = 0
            for (i in 0 until vm.data.vertexCount) {
                val dx = target[i * s] - undamaged[i * s]
                if (abs(dx) > 1e-4f) moved++
                assertTrue(target[i * s].isFinite())
            }
            assertTrue("${spec.id} front-end crumpled some vertices", moved > 10)
            assertTrue("${spec.id} but not all of them", moved < vm.data.vertexCount)

            val wheel = VehicleMesh.buildWheel(spec)
            assertTrue(wheel.indexCount > 0)
            assertTrue(wheel.vertexArray().all { it.isFinite() })
        }
    }

    @Test
    fun `vehicle shell has paint, glass and wheel wells within the spec envelope`() {
        for (spec in VehicleCatalog.all) {
            val vm = VehicleMesh.build(spec)
            val d = vm.data
            var paint = 0; var glass = 0; var lamps = 0
            var minY = Double.MAX_VALUE; var maxY = -Double.MAX_VALUE
            var maxZ = 0.0; var minX = Double.MAX_VALUE; var maxX = -Double.MAX_VALUE
            for (i in 0 until d.vertexCount) {
                when (d.materialAt(i)) {
                    Material.PAINT -> paint++
                    Material.GLASS -> glass++
                    Material.LAMP -> lamps++
                }
                val p = d.positionAt(i)
                minY = minOf(minY, p.y); maxY = maxOf(maxY, p.y)
                maxZ = maxOf(maxZ, abs(p.z)); minX = minOf(minX, p.x); maxX = maxOf(maxX, p.x)
                val n = d.normalAt(i)
                assertEquals("${spec.id} unit normal", 1.0, n.length, 1e-3)
            }
            assertTrue("${spec.id} is mostly paint", paint > d.vertexCount / 4)
            assertTrue("${spec.id} has a greenhouse", glass > 100)
            assertTrue("${spec.id} has lights", lamps >= 16)
            val top = spec.heightM - spec.cgHeightM
            val bottom = -(spec.cgHeightM - spec.groundClearanceM)
            assertEquals("${spec.id} roof at spec height", top, maxY, 0.15)
            assertEquals("${spec.id} floor at ground clearance", bottom, minY, 0.02)
            assertTrue("${spec.id} no wider than spec plus mirrors", maxZ <= spec.widthM / 2 + 0.30)
            assertEquals("${spec.id} length", spec.lengthM, maxX - minX, 0.30)

            // A wheel well: at the axle the shell has a dark pocket whose roof sits above the
            // wheel centre and inboard of the body side, so the tyre is seen inside it.
            val wheelY = spec.wheelRadiusM - spec.cgHeightM
            var wellVertices = 0
            for (i in 0 until d.vertexCount) {
                val p = d.positionAt(i)
                if (abs(p.x - spec.frontAxleX) < 0.05 && p.y > wheelY + 0.05 && p.y < top &&
                    abs(p.z) < spec.widthM / 2 - 0.1 && abs(p.z) > 0.3 && d.materialAt(i) == Material.MATTE
                ) wellVertices++
            }
            assertTrue("${spec.id} has a wheel well pocket", wellVertices > 0)
        }
    }

    @Test
    fun `loft normals point outward on a convex tube and the revolve makes a closed ring`() {
        val m = MeshData()
        val rings = (0..4).map { i ->
            val x = i.toDouble()
            (0 until 12).map { k ->
                val a = 2 * Math.PI * k / 12
                MeshData.LoftPoint(Vec3(x, Math.sin(a), Math.cos(a)), 0xFFFFFF, Material.PAINT)
            }
        }
        m.addLoft(rings, { i -> Vec3(i.toDouble(), 0.0, 0.0) }, capStart = true, capEnd = true)
        for (i in 0 until m.vertexCount) {
            val p = m.positionAt(i); val n = m.normalAt(i)
            val radial = Vec3(0.0, p.y, p.z)
            val axial = Vec3(p.x - 2.0, 0.0, 0.0)
            assertTrue("normal $i faces out", (n dot radial) > 0.5 || (n dot axial) > 0.5)
        }
        val w = MeshData()
        w.addRevolveZ(listOf(0.5 to -0.1, 1.0 to -0.1, 1.0 to 0.1, 0.5 to 0.1), 16, 0x808080, Material.MATTE)
        assertEquals(4 * 16, w.vertexCount)
        assertEquals(3 * 16 * 6, w.indexCount)
        for (i in 0 until w.vertexCount) {
            val p = w.positionAt(i); val n = w.normalAt(i)
            assertTrue("revolve normal faces away from the axis", (n dot Vec3(p.x, p.y, 0.0)) > -1e-6)
        }
    }

    @Test
    fun `hull faces point outward and a disc faces up`() {
        val m = MeshData()
        m.addHull(
            arrayOf(
                Vec3(-1.0, 0.0, -1.0), Vec3(1.0, 0.0, -1.0), Vec3(1.0, 0.0, 1.0), Vec3(-1.0, 0.0, 1.0),
                Vec3(-0.5, 1.0, -0.5), Vec3(0.5, 1.0, -0.5), Vec3(0.5, 1.0, 0.5), Vec3(-0.5, 1.0, 0.5),
            ),
            IntArray(6) { 0xFFFFFF },
        )
        val centroid = Vec3(0.0, 0.5, 0.0)
        for (i in 0 until m.vertexCount) {
            val p = m.positionAt(i)
            val n = m.normalAt(i)
            assertTrue("normal points away from centroid", (n dot (p - centroid)) > 0)
        }
        val d = MeshData().also { it.addDisc(12, 0) }
        val dv = d.vertexArray()
        for (i in 0 until d.vertexCount) assertEquals(1f, dv[i * s + MeshData.NORMAL_OFFSET + 1], 1e-6f)
    }

    @Test
    fun `scenery builds within index limits on a hilly level`() {
        val track = LevelCatalog.build(9).track
        val meshes = SceneryBuilder.build(track)
        assertTrue("some trees", meshes.isNotEmpty() && meshes.sumOf { it.vertexCount } > 1000)
        var foliage = 0
        for (m in meshes) {
            assertTrue(m.vertexCount < 65535)
            val v = m.vertexArray()
            assertTrue(v.all { it.isFinite() })
            for (i in 0 until m.vertexCount) {
                assertTrue("trees stay off the road", abs(v[i * s + 2]) > track.roadHalfWidth + 2)
                if (m.materialAt(i) == Material.FOLIAGE) foliage++
            }
        }
        assertTrue("conifers are tagged foliage", foliage > 500)
    }

    @Test
    fun `sky dome is small and surrounds the camera`() {
        val dome = SkyBuilder.dome()
        assertTrue(dome.vertexCount < 2000)
        var above = 0; var below = 0
        for (i in 0 until dome.vertexCount) {
            val p = dome.positionAt(i)
            assertEquals(SkyBuilder.RADIUS, p.length, 1e-2)
            if (p.y > 0) above++ else below++
        }
        assertTrue("covers the sky", above > below)
        assertTrue("dips under the horizon to hide the world's edge", below > 0)
    }

    @Test
    fun `detail texture is deterministic, tileable and has contrast in every channel`() {
        val a = DetailTexture.build()
        val b = DetailTexture.build()
        assertEquals(DetailTexture.SIZE * DetailTexture.SIZE * 4, a.size)
        assertTrue(a.contentEquals(b))
        val n = DetailTexture.SIZE
        for (ch in 0 until 4) {
            var sum = 0.0; var sumSq = 0.0
            for (i in 0 until n * n) {
                val v = (a[i * 4 + ch].toInt() and 0xFF) / 255.0
                sum += v; sumSq += v * v
            }
            val mean = sum / (n * n)
            val std = Math.sqrt(sumSq / (n * n) - mean * mean)
            assertTrue("channel $ch mean $mean in a usable range", mean in 0.25..0.75)
            assertTrue("channel $ch has contrast: $std", std > 0.05)
            // The seam between the last column and the first is no rougher than any other pair.
            var seam = 0.0; var inner = 0.0
            for (y in 0 until n) {
                fun px(x: Int) = (a[(y * n + x) * 4 + ch].toInt() and 0xFF).toDouble()
                seam += abs(px(0) - px(n - 1))
                inner += abs(px(n / 2) - px(n / 2 - 1))
            }
            assertTrue("channel $ch tiles: seam $seam vs inner $inner", seam < inner * 2.5 + n * 2.0)
        }
    }
}
