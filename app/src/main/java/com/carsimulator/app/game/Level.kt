package com.carsimulator.app.game

import com.carsimulator.app.sim.BoostStrip
import com.carsimulator.app.sim.Obstacle
import com.carsimulator.app.sim.RoadProfile
import com.carsimulator.app.sim.Terrain
import com.carsimulator.app.sim.Track

/** One round: how fast the strip launches the car and what it runs into. */
data class Level(
    val number: Int,
    val targetSpeedMph: Double,
    val courseName: String,
    val track: Track,
) {
    val targetSpeedMps: Double get() = targetSpeedMph * MPH_TO_MPS

    companion object {
        const val MPH_TO_MPS = 0.44704
    }
}

/**
 * Builds levels on demand. Speed climbs by a fixed step every round and the
 * course rotates through nine layouts, six bolted-on obstacles and three
 * shapes of the road itself, each nastier as the level number rises. The
 * landscape beside the strip is always hilly and gets taller too.
 */
object LevelCatalog {

    const val ROAD_HALF_WIDTH = 10.0
    const val BASE_SPEED_MPH = 45.0
    const val SPEED_STEP_MPH = 25.0
    const val BOOST_START_X = 15.0
    const val BOOST_END_X = 30.0

    val courseNames = listOf(
        "Speed-bump grid",
        "Monster bump",
        "Crest jump",
        "Launch ramp",
        "Loading dock",
        "Rollercoaster",
        "Crater field",
        "Staircase",
        "Canyon drop",
    )

    fun build(number: Int): Level {
        require(number >= 1)
        val mph = BASE_SPEED_MPH + SPEED_STEP_MPH * (number - 1)
        val mps = mph * Level.MPH_TO_MPS
        // Leave the driver a steering run that lasts about two seconds at speed.
        val obstacleStart = 60.0 + mps * 2.0
        val severity = (number - 1).toDouble()
        val course = (number - 1) % courseNames.size
        val terrain = Terrain(seed = number, hillAmplitude = 30.0 + 2.5 * severity, mountainAmplitude = 140.0 + 5 * severity)

        var obstacles: List<Obstacle> = emptyList()
        var profile: RoadProfile = RoadProfile.Flat
        var cameraCut = obstacleStart

        when (course) {
            0 -> obstacles = listOf(
                Obstacle.BumpField(
                    startX = obstacleStart, rows = 12, spacing = 3.0, bumpLength = 0.8,
                    height = 0.38 + 0.05 * severity, roadHalfWidth = ROAD_HALF_WIDTH,
                ),
            )
            1 -> obstacles = listOf(
                Obstacle.Bump(startX = obstacleStart, length = 6.0, height = 1.2 + 0.12 * severity),
            )
            2 -> {
                val crest = RoadProfile.CrestJump(
                    startX = obstacleStart, climbLength = 90.0, height = 14.0 + 2.0 * severity, dropLength = 5.0,
                )
                profile = crest
                cameraCut = crest.crestX - 12.0
            }
            3 -> obstacles = listOf(
                Obstacle.Ramp(startX = obstacleStart, length = 12.0, height = 2.0 + 0.25 * severity),
            )
            4 -> obstacles = listOf(
                Obstacle.Curb(startX = obstacleStart, length = 30.0, height = 0.35 + 0.06 * severity),
            )
            5 -> profile = RoadProfile.RollerCoaster(
                startX = obstacleStart - 40.0, endX = obstacleStart + 300.0,
                amplitude = 8.0 + 1.5 * severity, wavelength = 55.0,
            )
            6 -> obstacles = listOf(
                Obstacle.Potholes(
                    startX = obstacleStart, rows = 8, spacing = 5.0, diameter = 3.2,
                    depth = 0.55 + 0.06 * severity, roadHalfWidth = ROAD_HALF_WIDTH,
                ),
            )
            7 -> obstacles = listOf(
                Obstacle.Stairs(startX = obstacleStart, steps = 8, stepLength = 1.2, stepHeight = 0.28 + 0.025 * severity),
            )
            else -> {
                profile = RoadProfile.CanyonDrop(startX = obstacleStart, depth = 12.0 + 2.0 * severity)
                cameraCut = obstacleStart - 6.0
            }
        }
        val track = Track(
            roadHalfWidth = ROAD_HALF_WIDTH,
            lengthX = obstacleStart + 500.0 + mps * 5.0,
            obstacles = obstacles,
            boost = BoostStrip(BOOST_START_X, BOOST_END_X, mps),
            obstacleStartX = obstacleStart,
            profile = profile,
            terrain = terrain,
            cameraCutX = cameraCut,
        )
        return Level(number, mph, courseNames[course], track)
    }
}
