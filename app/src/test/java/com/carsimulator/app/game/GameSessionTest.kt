package com.carsimulator.app.game

import com.carsimulator.app.sim.VehicleCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GameSessionTest {

    @Test
    fun `levels get faster and rotate courses`() {
        val l1 = LevelCatalog.build(1)
        val l2 = LevelCatalog.build(2)
        val l10 = LevelCatalog.build(10)
        assertTrue(l2.targetSpeedMph > l1.targetSpeedMph)
        assertEquals(l1.courseName, l10.courseName)
        assertEquals(LevelCatalog.courseNames.size, (1..9).map { LevelCatalog.build(it).courseName }.toSet().size)
        assertTrue(l1.courseName != l2.courseName)
        assertTrue(l2.track.obstacleStartX > l1.track.obstacleStartX)
    }

    @Test
    fun `a run goes launch, impact, results with a side camera cut`() {
        val session = GameSession(VehicleCatalog.byId("s10_2001_lifted"), LevelCatalog.build(1))
        assertEquals(Phase.LAUNCH, session.phase)
        assertEquals(CameraMode.CHASE, session.camera.mode)

        var t = 0.0
        var sawImpact = false
        var sawSlowMotion = false
        while (session.phase != Phase.RESULTS && t < 60.0) {
            session.update(1.0 / 60)
            t += 1.0 / 60
            if (session.phase == Phase.IMPACT) {
                sawImpact = true
                if (session.timeScale < 0.5) sawSlowMotion = true
                assertEquals(CameraMode.SIDE, session.camera.mode)
            }
        }
        assertTrue("reached impact", sawImpact)
        assertTrue("slow motion happened", sawSlowMotion)
        assertEquals(Phase.RESULTS, session.phase)
        assertTrue("boosted", session.vehicle.boosted)

        val r = session.result()
        assertTrue(r.score >= 0)
        assertTrue("reached target speed, got ${r.maxSpeedMph}", r.maxSpeedMph > 40.0)
        assertTrue("camera eye finite", session.camera.state.eye.x.isFinite())
    }

    @Test
    fun `steering input during launch shifts the lane`() {
        val session = GameSession(VehicleCatalog.byId("corvette_c7_2016"), LevelCatalog.build(1))
        session.setSteer(-0.5)
        var t = 0.0
        while (session.phase == Phase.LAUNCH && t < 20.0) { session.update(1.0 / 60); t += 1.0 / 60 }
        assertTrue("moved left, z=${session.vehicle.position.z}", session.vehicle.position.z < -1.0)
    }

    @Test
    fun `level twenty is survivable by the simulator`() {
        val session = GameSession(VehicleCatalog.byId("hummer_h1_1998"), LevelCatalog.build(20))
        var t = 0.0
        while (session.phase != Phase.RESULTS && t < 90.0) { session.update(1.0 / 60); t += 1.0 / 60 }
        assertEquals(Phase.RESULTS, session.phase)
        val r = session.result()
        assertTrue("finite score", r.score in 0..1_000_000)
        assertTrue("reached high speed, got ${r.maxSpeedMph}", r.maxSpeedMph > 300.0)
    }

    @Test
    fun `hill courses run to results with the camera above the ground`() {
        for (level in listOf(3, 6, 9)) {
            val session = GameSession(VehicleCatalog.byId("suburban_2001_lifted"), LevelCatalog.build(level))
            var t = 0.0
            var minClearance = Double.MAX_VALUE
            while (session.phase != Phase.RESULTS && t < 90.0) {
                session.update(1.0 / 60)
                t += 1.0 / 60
                val eye = session.camera.state.eye
                minClearance = minOf(minClearance, eye.y - session.level.track.heightAt(eye.x, eye.z))
            }
            assertEquals("level $level finished", Phase.RESULTS, session.phase)
            assertTrue("level $level camera stayed above ground, min=$minClearance", minClearance > 0.5)
            assertTrue("level $level finite score", session.result().score in 0..1_000_000)
        }
    }
}
