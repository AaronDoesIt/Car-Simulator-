package com.carsimulator.app.sim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleTest {

    private val spec = VehicleSpec()

    @Test
    fun `full throttle accelerates from rest`() {
        val after = Vehicle(throttle = 1.0).step(spec, 1.0)
        assertEquals(spec.maxAccelerationMps2, after.speedMps, 1e-9)
    }

    @Test
    fun `braking never drives speed below zero`() {
        val after = Vehicle(speedMps = 1.0, brake = 1.0).step(spec, 1.0)
        assertEquals(0.0, after.speedMps, 0.0)
    }

    @Test
    fun `coasting slows the car`() {
        val after = Vehicle(speedMps = 10.0).step(spec, 1.0)
        assertTrue(after.speedMps < 10.0)
    }

    @Test
    fun `speed is capped at top speed`() {
        val after = Vehicle(speedMps = spec.topSpeedMps, throttle = 1.0).step(spec, 5.0)
        assertEquals(spec.topSpeedMps, after.speedMps, 0.0)
    }

    @Test
    fun `gear shifts up as speed crosses the band edge`() {
        val firstGearTop = spec.gearTopSpeedMps(1)
        val after = Vehicle(speedMps = firstGearTop - 0.5, throttle = 1.0, gear = 1).step(spec, 1.0)
        assertEquals(2, after.gear)
    }

    @Test
    fun `gear shifts down after slowing well into the previous band`() {
        val after = Vehicle(speedMps = 1.0, gear = 3).step(spec, 0.016)
        assertEquals(1, after.gear)
    }

    @Test
    fun `zero dt is a no-op`() {
        val start = Vehicle(speedMps = 12.0, throttle = 1.0, gear = 2)
        assertEquals(start, start.step(spec, 0.0))
    }

    @Test
    fun `rpm fraction stays within unit range`() {
        val v = Vehicle(speedMps = spec.gearTopSpeedMps(2) * 1.5, gear = 2)
        val f = v.rpmFraction(spec)
        assertTrue(f in 0.0..1.0)
    }
}
