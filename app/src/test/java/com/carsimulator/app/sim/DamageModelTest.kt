package com.carsimulator.app.sim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DamageModelTest {

    private val spec = VehicleCatalog.byId("s10_2001_lifted")
    private val nose = Vec3(spec.lengthM / 2, 0.0, 0.0)
    private val energy = 0.5 * spec.massKg * 15.0 * 15.0

    @Test
    fun `a square hit crushes more than a glancing one of equal energy`() {
        val square = DamageModel(spec).also { it.addImpact(nose, energy, Vec3(-1.0, 0.0, 0.0), obliquity = 1.0) }
        val glancing = DamageModel(spec).also { it.addImpact(nose, energy, Vec3(-1.0, 0.0, 0.0), obliquity = 0.1) }
        assertTrue(square.level(Panel.FRONT) > glancing.level(Panel.FRONT) * 2.5)
    }

    @Test
    fun `the panel facing the surface takes the hit`() {
        // Nose corner: equally front and right by position. A wall in front hits FRONT, not RIGHT.
        val corner = Vec3(spec.lengthM / 2, 0.0, spec.widthM / 2)
        val wallAhead = DamageModel(spec).also { it.addImpact(corner, energy, Vec3(-1.0, 0.0, 0.0)) }
        assertTrue(wallAhead.level(Panel.FRONT) > wallAhead.level(Panel.RIGHT) * 3)

        val wallBeside = DamageModel(spec).also { it.addImpact(corner, energy, Vec3(0.0, 0.0, -1.0)) }
        assertTrue(wallBeside.level(Panel.RIGHT) > wallBeside.level(Panel.FRONT) * 3)
    }

    @Test
    fun `angle information is optional and defaults to a square hit`() {
        val a = DamageModel(spec).also { it.addImpact(nose, energy) }
        val b = DamageModel(spec).also { it.addImpact(nose, energy, null, 1.0) }
        assertEquals(a.total, b.total, 1e-12)
    }
}
