package com.carsimulator.app.sim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackTest {

    private fun track(vararg obstacles: Obstacle) = Track(
        roadHalfWidth = 10.0, lengthX = 500.0, obstacles = obstacles.toList(),
        boost = BoostStrip(15.0, 30.0, 30.0), obstacleStartX = 100.0,
    )

    @Test
    fun `flat road is zero height with an upward normal`() {
        val t = track()
        assertEquals(0.0, t.heightAt(50.0, 0.0), 1e-12)
        assertEquals(1.0, t.normalAt(50.0, 0.0).y, 1e-9)
    }

    @Test
    fun `bump peaks at its centre and vanishes at its edges`() {
        val t = track(Obstacle.Bump(startX = 100.0, length = 4.0, height = 1.0))
        assertEquals(1.0, t.heightAt(102.0, 0.0), 1e-9)
        assertEquals(0.0, t.heightAt(100.0, 0.0), 1e-9)
        assertEquals(0.0, t.heightAt(104.0, 0.0), 1e-9)
        assertEquals(0.0, t.heightAt(99.0, 0.0), 1e-9)
    }

    @Test
    fun `staggered bump field alternates coverage across rows`() {
        val field = Obstacle.BumpField(
            startX = 100.0, rows = 4, spacing = 3.0, bumpLength = 1.0, height = 0.3,
            roadHalfWidth = 10.0, segmentWidth = 2.5,
        )
        val t = track(field)
        val z = -10.0 + 1.25 // first segment
        val row0 = t.heightAt(100.5, z)
        val row1 = t.heightAt(103.5, z)
        assertTrue((row0 > 0.0) != (row1 > 0.0))
    }

    @Test
    fun `potholes are below the road`() {
        val t = track(Obstacle.Potholes(100.0, rows = 2, spacing = 4.0, diameter = 2.0, depth = 0.3, roadHalfWidth = 10.0))
        var minH = 0.0
        var x = 100.0
        while (x < 108.0) {
            var z = -10.0
            while (z < 10.0) { minH = minOf(minH, t.heightAt(x, z)); z += 0.1 }
            x += 0.1
        }
        assertEquals(-0.3, minH, 1e-9)
    }

    @Test
    fun `stairs climb one step per tread`() {
        val t = track(Obstacle.Stairs(100.0, steps = 3, stepLength = 1.0, stepHeight = 0.2))
        assertEquals(0.2, t.heightAt(100.5, 0.0), 1e-9)
        assertEquals(0.4, t.heightAt(101.5, 0.0), 1e-9)
        assertEquals(0.6, t.heightAt(102.5, 0.0), 1e-9)
    }

    @Test
    fun `road profiles shape the centreline`() {
        val crest = RoadProfile.CrestJump(startX = 100.0, climbLength = 90.0, height = 14.0, dropLength = 5.0)
        assertEquals(14.0, crest.heightAt(190.0), 1e-9)
        assertEquals(0.0, crest.heightAt(100.0), 1e-9)
        assertEquals(0.0, crest.heightAt(200.0), 1e-9)
        assertTrue(crest.heightAt(192.5) < 8.0 && crest.heightAt(192.5) > 6.0)

        val canyon = RoadProfile.CanyonDrop(startX = 100.0, depth = 12.0)
        assertEquals(-12.0, canyon.heightAt(120.0), 1e-9)
        assertEquals(0.0, canyon.heightAt(99.0), 1e-9)
        assertEquals(0.0, canyon.heightAt(300.0), 1e-9)

        val coaster = RoadProfile.RollerCoaster(startX = 100.0, endX = 400.0, amplitude = 8.0, wavelength = 50.0)
        var peak = 0.0
        var x = 100.0
        while (x < 400.0) { peak = maxOf(peak, coaster.heightAt(x)); x += 0.5 }
        assertEquals(8.0, peak, 0.2)
    }

    @Test
    fun `terrain leaves the road and shoulder flat and rises beyond`() {
        val terrain = Terrain(seed = 3, hillAmplitude = 30.0)
        val t = Track(10.0, 500.0, emptyList(), BoostStrip(15.0, 30.0, 30.0), 100.0, terrain = terrain)
        assertEquals(0.0, t.heightAt(50.0, 0.0), 1e-12)
        assertEquals(0.0, t.heightAt(50.0, 9.9), 1e-12)
        var maxFar = 0.0
        var x = 0.0
        while (x < 500.0) { maxFar = maxOf(maxFar, t.heightAt(x, 60.0)); x += 5.0 }
        assertTrue("hills exist far out, max=$maxFar", maxFar > 8.0)
        assertTrue("hills bounded", maxFar < 60.0)
        assertEquals("deterministic", t.heightAt(123.0, 70.0), t.heightAt(123.0, 70.0), 0.0)
    }
}
