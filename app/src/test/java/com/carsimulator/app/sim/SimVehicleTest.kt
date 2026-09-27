package com.carsimulator.app.sim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class SimVehicleTest {

    private val s10 = VehicleCatalog.byId("s10_2001_lifted")

    private fun flatTrack(boostMps: Double = 30.0, obstacles: List<Obstacle> = emptyList()) = Track(
        roadHalfWidth = 10.0, lengthX = 2000.0, obstacles = obstacles,
        boost = BoostStrip(15.0, 30.0, boostMps), obstacleStartX = 100.0,
    )

    private fun run(v: SimVehicle, seconds: Double, frameDt: Double = 1.0 / 60) {
        var t = 0.0
        while (t < seconds) { v.advance(frameDt); t += frameDt }
    }

    @Test
    fun `parked car settles level at its design ride height`() {
        val v = SimVehicle(s10, flatTrack(), startX = 0.0)
        run(v, 4.0)
        assertEquals(s10.cgHeightM, v.position.y, 0.05)
        assertTrue("stayed upright", v.orientation.up.y > 0.995)
        assertTrue("stopped", v.speed < 0.05)
        assertEquals(4, v.wheelsOnGround)
        assertTrue("no damage", v.damage.total < 1e-6)
    }

    @Test
    fun `every catalog vehicle parks without drifting`() {
        for (spec in VehicleCatalog.all) {
            val v = SimVehicle(spec, flatTrack())
            run(v, 3.0)
            assertTrue("${spec.id} upright", v.orientation.up.y > 0.99)
            assertTrue("${spec.id} stopped, speed=${v.speed}", v.speed < 0.1)
            assertTrue("${spec.id} ride height", abs(v.position.y - spec.cgHeightM) < 0.08)
        }
    }

    @Test
    fun `throttle drives it straight down the strip`() {
        val v = SimVehicle(s10, flatTrack(boostMps = 0.0).let {
            Track(it.roadHalfWidth, it.lengthX, it.obstacles, BoostStrip(-100.0, -99.0, 0.0), it.obstacleStartX)
        })
        v.throttle = 1.0
        run(v, 4.0)
        assertTrue("moved forward, x=${v.position.x}", v.position.x > 15.0)
        assertTrue("kept its lane, z=${v.position.z}", abs(v.position.z) < 0.5)
        assertTrue("faster than 8 m/s, got ${v.forwardSpeed}", v.forwardSpeed > 8.0)
    }

    @Test
    fun `boost strip sets the forward speed to the target`() {
        val target = 60.0
        val v = SimVehicle(s10, flatTrack(boostMps = target))
        v.throttle = 1.0
        run(v, 3.0)
        assertTrue("boosted", v.boosted)
        assertTrue("speed near target, got ${v.forwardSpeed}", v.forwardSpeed > target * 0.85)
        assertTrue("finite", v.position.x.isFinite())
    }

    @Test
    fun `steering right moves the car to positive z`() {
        val v = SimVehicle(s10, flatTrack(boostMps = 20.0))
        v.throttle = 1.0
        run(v, 2.0)
        v.steerInput = 0.6
        run(v, 3.0)
        assertTrue("turned right, z=${v.position.z}", v.position.z > 2.0)
        assertTrue("still upright", v.orientation.up.y > 0.8)
    }

    @Test
    fun `speed bump grid at 100 mph launches the truck`() {
        val mps = 100 * 0.44704
        val field = Obstacle.BumpField(100.0, rows = 10, spacing = 3.5, bumpLength = 0.9, height = 0.3, roadHalfWidth = 10.0)
        val v = SimVehicle(s10, flatTrack(boostMps = mps, obstacles = listOf(field)))
        v.throttle = 1.0
        run(v, 8.0)
        assertTrue("left the ground, air=${v.longestAirTime}", v.longestAirTime > 0.1)
        assertTrue("finite", v.position.y.isFinite() && v.position.x.isFinite())
        assertTrue("passed the field, x=${v.position.x}", v.position.x > 140.0)
    }

    @Test
    fun `loading dock curb at 150 mph crushes the front end`() {
        val mps = 150 * 0.44704
        val curb = Obstacle.Curb(100.0, length = 30.0, height = 0.6)
        val v = SimVehicle(s10, flatTrack(boostMps = mps, obstacles = listOf(curb)))
        v.throttle = 1.0
        run(v, 6.0)
        assertTrue("took damage, got ${v.damage.total}", v.damage.total > 0.05)
        assertTrue("finite", v.position.y.isFinite())
    }

    @Test
    fun `four hundred mph into stairs stays numerically sane`() {
        val mps = 400 * 0.44704
        val stairs = Obstacle.Stairs(500.0, steps = 8, stepLength = 1.2, stepHeight = 0.5)
        val v = SimVehicle(VehicleCatalog.byId("suburban_2001_lifted"), flatTrack(boostMps = mps, obstacles = listOf(stairs)))
        v.throttle = 1.0
        run(v, 12.0)
        val p = v.position
        assertTrue("finite position $p", p.x.isFinite() && p.y.isFinite() && p.z.isFinite())
        assertTrue("finite orientation", v.orientation.w.isFinite())
        assertTrue("did not sink, y=${p.y}", p.y > -5.0)
        assertTrue("under Mach 1, speed=${v.maxSpeed}", v.maxSpeed < 340.0)
        assertTrue("damaged or wheels lost", v.damage.total > 0.05 || v.wheelsLost > 0)
        for (w in v.wheels) assertTrue("wheel finite", w.centerWorld.x.isFinite() && w.centerWorld.y.isFinite())
    }

    @Test
    fun `a square curb hit does more damage than a ramp of the same height`() {
        val mps = 80 * 0.44704
        val curb = SimVehicle(s10, flatTrack(boostMps = mps, obstacles = listOf(Obstacle.Curb(100.0, 30.0, 0.5))))
        val ramp = SimVehicle(s10, flatTrack(boostMps = mps, obstacles = listOf(Obstacle.Ramp(100.0, 6.0, 0.5))))
        curb.throttle = 1.0; ramp.throttle = 1.0
        run(curb, 6.0); run(ramp, 6.0)
        assertTrue("curb ${curb.damage.total} vs ramp ${ramp.damage.total}", curb.damage.total > ramp.damage.total * 1.5)
    }

    @Test
    fun `crest jump sends the truck flying and it lands hard`() {
        val mps = 120 * 0.44704
        val crest = RoadProfile.CrestJump(startX = 100.0, climbLength = 90.0, height = 16.0, dropLength = 5.0)
        val t = Track(10.0, 2000.0, emptyList(), BoostStrip(15.0, 30.0, mps), 100.0, profile = crest, terrain = Terrain(1))
        val v = SimVehicle(s10, t)
        v.throttle = 1.0
        run(v, 12.0)
        assertTrue("flew, air=${v.longestAirTime}", v.longestAirTime > 0.8)
        assertTrue("came back down, y=${v.position.y}", v.position.y < 5.0)
        assertTrue("finite", v.position.x.isFinite())
        assertTrue("landing hurt, damage=${v.damage.total}", v.damage.total > 0.05)
    }

    @Test
    fun `rollercoaster at 150 mph stays sane`() {
        val mps = 150 * 0.44704
        val coaster = RoadProfile.RollerCoaster(startX = 60.0, endX = 400.0, amplitude = 12.0, wavelength = 55.0)
        val t = Track(10.0, 2000.0, emptyList(), BoostStrip(15.0, 30.0, mps), 100.0, profile = coaster, terrain = Terrain(2))
        val v = SimVehicle(VehicleCatalog.byId("corvette_c7_2016"), t)
        v.throttle = 1.0
        run(v, 10.0)
        assertTrue("finite", v.position.x.isFinite() && v.position.y.isFinite())
        assertTrue("got airborne on the hills, air=${v.longestAirTime}", v.longestAirTime > 0.2)
        assertTrue("passed the hills, x=${v.position.x}", v.position.x > 300.0)
    }
}
