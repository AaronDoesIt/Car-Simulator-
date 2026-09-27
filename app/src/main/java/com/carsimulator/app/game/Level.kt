package com.carsimulator.app.game

import com.carsimulator.app.sim.BoostStrip
import com.carsimulator.app.sim.Obstacle
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
 * obstacle course rotates through six layouts that each get nastier as the
 * level number rises.
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
        "Launch ramp",
        "Loading dock",
        "Crater field",
        "Staircase",
    )

    fun build(number: Int): Level {
        require(number >= 1)
        val mph = BASE_SPEED_MPH + SPEED_STEP_MPH * (number - 1)
        val mps = mph * Level.MPH_TO_MPS
        // Leave the driver a steering run that lasts about two seconds at speed.
        val obstacleStart = 60.0 + mps * 2.0
        val severity = (number - 1).toDouble()
        val course = (number - 1) % courseNames.size

        val obstacles: List<Obstacle> = when (course) {
            0 -> listOf(
                Obstacle.BumpField(
                    startX = obstacleStart, rows = 12, spacing = 3.0, bumpLength = 0.8,
                    height = 0.38 + 0.05 * severity, roadHalfWidth = ROAD_HALF_WIDTH,
                ),
            )
            1 -> listOf(
                Obstacle.Bump(startX = obstacleStart, length = 6.0, height = 1.2 + 0.12 * severity),
            )
            2 -> listOf(
                Obstacle.Ramp(startX = obstacleStart, length = 12.0, height = 2.0 + 0.25 * severity),
            )
            3 -> listOf(
                Obstacle.Curb(startX = obstacleStart, length = 30.0, height = 0.35 + 0.06 * severity),
            )
            4 -> listOf(
                Obstacle.Potholes(
                    startX = obstacleStart, rows = 8, spacing = 5.0, diameter = 3.2,
                    depth = 0.55 + 0.06 * severity, roadHalfWidth = ROAD_HALF_WIDTH,
                ),
            )
            else -> listOf(
                Obstacle.Stairs(startX = obstacleStart, steps = 8, stepLength = 1.2, stepHeight = 0.28 + 0.025 * severity),
            )
        }
        val obstacleEnd = obstacles.maxOf { it.endX }
        val track = Track(
            roadHalfWidth = ROAD_HALF_WIDTH,
            lengthX = obstacleEnd + 300.0 + mps * 5.0,
            obstacles = obstacles,
            boost = BoostStrip(BOOST_START_X, BOOST_END_X, mps),
            obstacleStartX = obstacleStart,
        )
        return Level(number, mph, courseNames[course], track)
    }
}
