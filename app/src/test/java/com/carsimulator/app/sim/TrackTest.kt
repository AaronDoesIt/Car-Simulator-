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
}
