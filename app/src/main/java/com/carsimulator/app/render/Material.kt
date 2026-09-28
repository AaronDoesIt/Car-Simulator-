package com.carsimulator.app.render

/**
 * Surface types the shader knows how to light, stored per vertex as a float
 * code so one program draws everything. Terrain packs its grass-to-rock blend
 * into the fractional part of code 0 so the detail texture can cross-fade.
 */
object Material {
    const val TERRAIN = 0f
    const val ASPHALT = 1f
    const val PAINT = 2f
    const val GLASS = 3f
    const val CHROME = 4f
    const val MATTE = 5f
    const val FOLIAGE = 6f
    const val BARK = 7f
    const val LAMP = 8f
    const val ALLOY = 9f
    /** Albedo comes from the vehicle texture (UV), lit like paint. */
    const val TEXTURED = 10f

    /** Terrain code carrying a 0..1 rock blend. */
    fun terrain(rockBlend: Float): Float = rockBlend.coerceIn(0f, 1f) * 0.9f
}
