package com.carsimulator.app.render

import com.carsimulator.app.sim.BodyStyle
import com.carsimulator.app.sim.DamageModel
import com.carsimulator.app.sim.Panel
import com.carsimulator.app.sim.Quat
import com.carsimulator.app.sim.Vec3
import com.carsimulator.app.sim.VehicleSpec
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Builds a body for a [VehicleSpec] in the body frame (centre of gravity at
 * the origin, X forward, Y up, Z right) plus the per-vertex panel weights
 * that let [deform] crumple it as damage grows.
 *
 * The shell is one smooth loft: a cross-section is swept nose to tail and
 * changes shape along the way, so the hood slopes, the windscreen rakes,
 * the roof rounds off with tumblehome, the sides pinch at the ends and the
 * wheel wells are real pockets the tyres sit inside. Trim (lights, grille,
 * bumpers, mirrors, handles, spoilers, racks, flares) is bolted on after.
 * Every proportion comes from the spec, so a Suburban is long and boxy and a
 * Corvette low and pinched without any per-model geometry.
 */
class VehicleMesh private constructor(
    val data: MeshData,
    private val restPositions: FloatArray,
    private val panelWeights: Array<FloatArray>,
    private val bodyCentre: Vec3,
) {
    /** Write crumpled positions into [target] (interleaved GL array) for the current damage. */
    fun deform(damage: DamageModel, target: FloatArray) {
        val levels = FloatArray(Panel.entries.size) { damage.level(Panel.entries[it]).toFloat() }
        val count = restPositions.size / 3
        for (i in 0 until count) {
            var d = 0f
            val w = panelWeights[i]
            for (p in levels.indices) d = max(d, w[p] * levels[p])
            val rx = restPositions[i * 3]; val ry = restPositions[i * 3 + 1]; val rz = restPositions[i * 3 + 2]
            val pull = 0.30f * d
            val ripple = 0.10f * d
            val hx = sin(i * 12.9898f) * ripple
            val hy = sin(i * 78.233f) * ripple
            val hz = sin(i * 37.719f) * ripple
            target[i * MeshData.STRIDE] = rx - (rx - bodyCentre.x.toFloat()) * pull + hx
            target[i * MeshData.STRIDE + 1] = ry - (ry - bodyCentre.y.toFloat()) * pull * 1.3f + hy
            target[i * MeshData.STRIDE + 2] = rz - (rz - bodyCentre.z.toFloat()) * pull + hz
        }
    }

    /** Proportions of one silhouette family, all as fractions of length or body height. */
    private class Shape(
        val beltFrac: Double,
        val cab0: Double, val cab1: Double,
        val wsLen: Double, val rearLen: Double,
        val hoodDrop: Double, val deckRise: Double,
        val shoulderR: Double, val roofR: Double, val floorR: Double,
        val glassBase: Double, val roofWidth: Double,
        val noseTaper: Double, val tailTaper: Double,
        val bed: Boolean, val bedDepth: Double,
        val flares: Boolean,
    )

    companion object {
        private const val GLASS_RGB = 0x18222E
        private const val CHROME_RGB = 0xE4E7EA
        private const val LAMP_RGB = 0xFFF4D0
        private const val TAIL_RGB = 0xE2302A
        private const val DARK_RGB = 0x161618
        private const val UNDER_RGB = 0x1C1C1E
        private const val WELL_RGB = 0x0A0A0B
        private const val BED_RGB = 0x202022
        private const val PLATE_RGB = 0xEFEFEF
        private const val ALLOY_RGB = 0xC8CBD0
        private const val RIM_DISH_RGB = 0x5A5D63
        private const val RUBBER_RGB = 0x1B1B1D
        private const val ARC_STEPS = 3

        private fun shapeFor(style: BodyStyle): Shape = when (style) {
            BodyStyle.PICKUP -> Shape(
                beltFrac = 0.56, cab0 = 0.36, cab1 = 0.64, wsLen = 0.10, rearLen = 0.03,
                hoodDrop = 0.06, deckRise = 0.0, shoulderR = 0.08, roofR = 0.10, floorR = 0.08,
                glassBase = 0.93, roofWidth = 0.80, noseTaper = 0.05, tailTaper = 0.03,
                bed = true, bedDepth = 0.32, flares = true,
            )
            BodyStyle.SUV -> Shape(
                beltFrac = 0.52, cab0 = 0.03, cab1 = 0.70, wsLen = 0.12, rearLen = 0.05,
                hoodDrop = 0.07, deckRise = 0.0, shoulderR = 0.09, roofR = 0.12, floorR = 0.08,
                glassBase = 0.93, roofWidth = 0.80, noseTaper = 0.06, tailTaper = 0.03,
                bed = false, bedDepth = 0.0, flares = false,
            )
            BodyStyle.OFFROAD -> Shape(
                beltFrac = 0.50, cab0 = 0.03, cab1 = 0.66, wsLen = 0.07, rearLen = 0.03,
                hoodDrop = 0.03, deckRise = 0.0, shoulderR = 0.05, roofR = 0.06, floorR = 0.05,
                glassBase = 0.95, roofWidth = 0.86, noseTaper = 0.03, tailTaper = 0.02,
                bed = false, bedDepth = 0.0, flares = true,
            )
            BodyStyle.SPORTS -> Shape(
                beltFrac = 0.60, cab0 = 0.28, cab1 = 0.60, wsLen = 0.20, rearLen = 0.24,
                hoodDrop = 0.22, deckRise = 0.02, shoulderR = 0.14, roofR = 0.12, floorR = 0.10,
                glassBase = 0.92, roofWidth = 0.70, noseTaper = 0.12, tailTaper = 0.08,
                bed = false, bedDepth = 0.0, flares = false,
            )
            BodyStyle.MUSCLE -> Shape(
                beltFrac = 0.57, cab0 = 0.24, cab1 = 0.62, wsLen = 0.16, rearLen = 0.18,
                hoodDrop = 0.14, deckRise = 0.04, shoulderR = 0.11, roofR = 0.11, floorR = 0.09,
                glassBase = 0.92, roofWidth = 0.74, noseTaper = 0.09, tailTaper = 0.06,
                bed = false, bedDepth = 0.0, flares = false,
            )
            BodyStyle.SEDAN -> Shape(
                beltFrac = 0.56, cab0 = 0.22, cab1 = 0.62, wsLen = 0.15, rearLen = 0.13,
                hoodDrop = 0.12, deckRise = 0.05, shoulderR = 0.10, roofR = 0.11, floorR = 0.09,
                glassBase = 0.92, roofWidth = 0.76, noseTaper = 0.08, tailTaper = 0.06,
                bed = false, bedDepth = 0.0, flares = false,
            )
        }

        private fun smooth(t: Double): Double {
            val c = t.coerceIn(0.0, 1.0)
            return c * c * (3 - 2 * c)
        }

        /** Half linear, half eased: straight enough for a windscreen, soft at the ends. */
        private fun ease(t: Double): Double = 0.5 * t.coerceIn(0.0, 1.0) + 0.5 * smooth(t)

        fun build(spec: VehicleSpec): VehicleMesh {
            val m = MeshData()
            val s = shapeFor(spec.bodyStyle)
            val bottom = -(spec.cgHeightM - spec.groundClearanceM)
            val top = spec.heightM - spec.cgHeightM
            val xF = spec.frontAxleX + spec.frontOverhang
            val xR = spec.rearAxleX - spec.rearOverhang
            val len = xF - xR
            val hw = spec.widthM / 2
            val bh = top - bottom
            val belt = bottom + bh * s.beltFrac
            val body = spec.colorRgb
            val wheelY = spec.wheelRadiusM - spec.cgHeightM
            val r = spec.wheelRadiusM
            val rArch = r * 1.22 + 0.04
            val wellInnerZ = spec.trackWidthM / 2 - spec.wheelWidthM / 2 - 0.03
            val cab0x = xR + s.cab0 * len
            val cab1x = xR + s.cab1 * len
            val chrome = spec.year < 2005 || spec.bodyStyle == BodyStyle.OFFROAD

            fun halfWAt(t: Double) =
                hw * (1 - s.tailTaper * (1 - smooth(t / 0.12)) - s.noseTaper * (1 - smooth((1 - t) / 0.12)))
            fun yTopAt(t: Double): Double = when {
                t > s.cab1 -> belt - s.hoodDrop * bh * ease((t - s.cab1) / (1 - s.cab1))
                s.bed && t > 0.035 && t < s.cab0 - 0.02 -> belt - s.bedDepth * bh
                t < s.cab0 -> belt + s.deckRise * bh
                else -> belt
            }

            // ---- The shell -------------------------------------------------------------
            val stationCount = (len / 0.07).toInt().coerceIn(30, 110)
            val rings = ArrayList<List<MeshData.LoftPoint>>()
            val centres = ArrayList<Vec3>()
            for (i in 0..stationCount) {
                val t = i.toDouble() / stationCount
                val x = xR + t * len
                val yFloor = bottom
                val bedHere = s.bed && t > 0.035 && t < s.cab0 - 0.02
                val yTop = yTopAt(t)
                val rise = when {
                    t >= s.cab1 - s.wsLen && t <= s.cab1 -> (s.cab1 - t) / s.wsLen
                    t >= s.cab0 && t <= s.cab0 + s.rearLen -> (t - s.cab0) / s.rearLen
                    t > s.cab0 + s.rearLen && t < s.cab1 - s.wsLen -> 1.0
                    else -> 0.0
                }
                val rr = ease(rise)
                val yRoof = yTop + (top - yTop) * rr
                val glassRoof = rr > 0.02 && rr < 0.985
                val inCabin = rr > 0.02
                val halfW = halfWAt(t)

                var yArch = yFloor; var wellW = s.floorR; var lipR = s.floorR; var inArch = false
                for (ax in listOf(spec.rearAxleX, spec.frontAxleX)) {
                    val dx = x - ax
                    if (abs(dx) < rArch) {
                        val h = wheelY + sqrt(rArch * rArch - dx * dx)
                        if (h > yFloor + 0.02) {
                            inArch = true
                            yArch = min(h, belt - s.shoulderR - 0.06)
                            wellW = (halfW - wellInnerZ).coerceIn(0.15, halfW - 0.10)
                            lipR = 0.02
                        }
                    }
                }

                val topMat = if (bedHere) Material.MATTE else Material.PAINT
                val topCol = if (bedHere) BED_RGB else body
                val glassSide = inCabin
                val roofMat = if (glassRoof) Material.GLASS else topMat
                val roofCol = if (glassRoof) GLASS_RGB else topCol
                val wellCol = if (inArch) WELL_RGB else UNDER_RGB

                val right = ArrayList<MeshData.LoftPoint>()
                fun pt(z: Double, y: Double, color: Int, material: Float) = right.add(MeshData.LoftPoint(Vec3(x, y, z), color, material))

                pt(0.0, yFloor, UNDER_RGB, Material.MATTE)
                pt(halfW - wellW, yFloor, UNDER_RGB, Material.MATTE)
                pt(halfW - wellW, yArch, wellCol, Material.MATTE)
                pt(halfW - lipR, yArch, wellCol, Material.MATTE)
                pt(halfW - lipR, yArch, body, Material.PAINT) // crisp edge: same spot, painted
                for (k in 1..ARC_STEPS) {
                    val a = -PI / 2 + (PI / 2) * k / ARC_STEPS
                    pt(halfW - lipR + lipR * cos(a), yArch + lipR + lipR * sin(a), body, Material.PAINT)
                }
                val sR = min(s.shoulderR, max(0.01, (belt - yArch - lipR) / 2))
                pt(halfW, belt - sR, body, Material.PAINT)
                for (k in 1..ARC_STEPS) {
                    val a = (PI / 2) * k / ARC_STEPS
                    pt(halfW - sR + sR * cos(a), belt - sR + sR * sin(a), body, Material.PAINT)
                }
                val topZ = if (bedHere) halfW - 0.12 else halfW - sR
                pt(topZ, belt, body, Material.PAINT)
                pt(topZ, belt, topCol, topMat)
                pt(topZ, yTop, topCol, topMat)
                val glassZ = min(halfW * s.glassBase, topZ - 0.01)
                pt(glassZ, yTop, topCol, topMat)
                pt(glassZ, yTop, if (glassSide) GLASS_RGB else topCol, if (glassSide) Material.GLASS else topMat)
                val gh = yRoof - yTop
                val rG = min(s.roofR, gh / 2)
                val roofZ = min(halfW * s.roofWidth, glassZ)
                pt(roofZ, yRoof - rG, if (glassSide) GLASS_RGB else topCol, if (glassSide) Material.GLASS else topMat)
                pt(roofZ, yRoof - rG, roofCol, roofMat)
                for (k in 1..ARC_STEPS) {
                    val a = (PI / 2) * k / ARC_STEPS
                    pt(roofZ - rG + rG * cos(a), yRoof - rG + rG * sin(a), roofCol, roofMat)
                }
                pt(0.0, yRoof, roofCol, roofMat)

                val ring = ArrayList<MeshData.LoftPoint>(right.size * 2)
                ring.addAll(right)
                for (j in right.indices.reversed()) {
                    val p = right[j]
                    ring.add(MeshData.LoftPoint(Vec3(p.p.x, p.p.y, -p.p.z), p.color, p.material))
                }
                rings.add(ring)
                centres.add(Vec3(x, (yFloor + yRoof) / 2, 0.0))
            }
            m.addLoft(rings, { centres[it] }, capStart = true, capEnd = true, capColor = body, capMaterial = Material.PAINT)

            // ---- Trim ------------------------------------------------------------------
            val halfWNose = halfWAt(1.0)
            val halfWTail = halfWAt(0.0)
            val yNose = yTopAt(1.0)
            val yTail = yTopAt(0.0)
            val bumperCol = if (chrome) CHROME_RGB else body
            val bumperMat = if (chrome) Material.CHROME else Material.PAINT

            // Front face: lights, grille, bumper, valance.
            for (side in listOf(-1.0, 1.0)) {
                m.addBox(Vec3(xF + 0.02, yNose - bh * 0.11, side * halfWNose * 0.68), Vec3(0.06, bh * 0.10, halfWNose * 0.40), LAMP_RGB, Material.LAMP)
            }
            m.addBox(Vec3(xF + 0.01, yNose - bh * 0.12, 0.0), Vec3(0.05, bh * 0.13, halfWNose * 0.78), DARK_RGB, Material.MATTE)
            val bars = if (chrome) 3 else 1
            for (b in 0 until bars) {
                val y = yNose - bh * 0.12 + (b - (bars - 1) / 2.0) * bh * 0.04
                m.addBox(Vec3(xF + 0.04, y, 0.0), Vec3(0.02, 0.025, halfWNose * 0.76), CHROME_RGB, Material.CHROME)
            }
            m.addBox(Vec3(xF - 0.06, bottom + bh * 0.16, 0.0), Vec3(0.18, bh * 0.13, halfWNose * 2 + 0.04), bumperCol, bumperMat)
            m.addBox(Vec3(xF - 0.04, bottom + bh * 0.05, 0.0), Vec3(0.12, bh * 0.08, halfWNose * 1.9), DARK_RGB, Material.MATTE)

            // Rear face: tail lights, bumper, plate, exhaust.
            for (side in listOf(-1.0, 1.0)) {
                m.addBox(Vec3(xR - 0.02, yTail - bh * 0.12, side * halfWTail * 0.70), Vec3(0.05, bh * 0.09, halfWTail * 0.36), TAIL_RGB, Material.LAMP)
            }
            m.addBox(Vec3(xR + 0.06, bottom + bh * 0.16, 0.0), Vec3(0.18, bh * 0.13, halfWTail * 2 + 0.04), bumperCol, bumperMat)
            m.addBox(Vec3(xR - 0.02, bottom + bh * 0.32, 0.0), Vec3(0.02, 0.14, 0.32), PLATE_RGB, Material.MATTE)
            m.addBox(Vec3(xR + 0.02, bottom + 0.07, -halfWTail * 0.55), Vec3(0.16, 0.08, 0.08), CHROME_RGB, Material.CHROME)

            // Mirrors on the A-pillar base.
            run {
                val x = cab1x - 0.28
                val hwx = halfWAt((x - xR) / len)
                for (side in listOf(-1.0, 1.0)) {
                    m.addBox(Vec3(x, belt + 0.09, side * (hwx + 0.14)), Vec3(0.13, 0.10, 0.20), body, Material.PAINT)
                    m.addBox(Vec3(x, belt + 0.06, side * (hwx + 0.03)), Vec3(0.05, 0.03, 0.08), DARK_RGB, Material.MATTE)
                }
            }

            // Door handles: one per door, second row when the cab is long enough for four doors.
            val fourDoor = when (spec.bodyStyle) {
                BodyStyle.SUV, BodyStyle.SEDAN -> true
                BodyStyle.PICKUP -> (cab1x - cab0x) > 1.6
                else -> false
            }
            val handleXs = if (fourDoor) listOf(0.62, 0.20) else listOf(0.60)
            for (f in handleXs) {
                val x = cab0x + (cab1x - cab0x) * f
                val hwx = halfWAt((x - xR) / len)
                for (side in listOf(-1.0, 1.0)) {
                    m.addBox(Vec3(x, belt - 0.13, side * (hwx + 0.012)), Vec3(0.14, 0.025, 0.025), CHROME_RGB, Material.CHROME)
                }
            }

            when (spec.bodyStyle) {
                BodyStyle.MUSCLE -> {
                    m.addBox(Vec3(xR + 0.16, yTail + 0.11, 0.0), Vec3(0.16, 0.03, halfWTail * 1.6), DARK_RGB, Material.MATTE)
                    for (side in listOf(-1.0, 1.0)) {
                        m.addBox(Vec3(xR + 0.16, yTail + 0.05, side * halfWTail * 0.7), Vec3(0.08, 0.10, 0.04), DARK_RGB, Material.MATTE)
                    }
                }
                BodyStyle.OFFROAD -> {
                    val x0 = cab0x + 0.25; val x1 = cab1x - 0.45
                    val rz = hw * s.roofWidth * 0.85
                    for (side in listOf(-1.0, 1.0)) {
                        m.addBox(Vec3((x0 + x1) / 2, top + 0.06, side * rz), Vec3(x1 - x0, 0.04, 0.05), DARK_RGB, Material.MATTE)
                    }
                    for (k in 0..2) {
                        m.addBox(Vec3(x0 + (x1 - x0) * k / 2, top + 0.09, 0.0), Vec3(0.05, 0.03, rz * 2), DARK_RGB, Material.MATTE)
                    }
                }
                else -> Unit
            }

            // Fender flares on the trucks and off-roaders.
            if (s.flares) {
                val flareCol = if (spec.bodyStyle == BodyStyle.OFFROAD) DARK_RGB else body
                val flareMat = if (spec.bodyStyle == BodyStyle.OFFROAD) Material.MATTE else Material.PAINT
                for (ax in listOf(spec.rearAxleX, spec.frontAxleX)) {
                    val hwx = halfWAt((ax - xR) / len)
                    for (side in listOf(-1.0, 1.0)) {
                        flare(m, ax, wheelY, rArch, side, hwx, flareCol, flareMat)
                    }
                }
            }

            return withDamageWeights(spec, m)
        }

        /**
         * Wrap an imported body ([VehicleModel]) so it crumples like a built one.
         * The model is already in the body frame with its wheels cut out.
         */
        fun fromModel(spec: VehicleSpec, model: MeshData): VehicleMesh = withDamageWeights(spec, model)

        /** Per-vertex panel weights from where each vertex sits in the spec's envelope. */
        private fun withDamageWeights(spec: VehicleSpec, m: MeshData): VehicleMesh {
            val bottom = -(spec.cgHeightM - spec.groundClearanceM)
            val top = spec.heightM - spec.cgHeightM
            val xF = spec.frontAxleX + spec.frontOverhang
            val xR = spec.rearAxleX - spec.rearOverhang
            val hw = spec.widthM / 2
            val rest = m.positionsCopy()
            val centre = Vec3((xF + xR) / 2, (top + bottom) / 2, 0.0)
            val halfLen = (xF - xR) / 2
            val weights = Array(m.vertexCount) { i ->
                val x = rest[i * 3].toDouble() - centre.x
                val y = rest[i * 3 + 1].toDouble()
                val z = rest[i * 3 + 2].toDouble()
                val arr = FloatArray(Panel.entries.size)
                arr[Panel.FRONT.ordinal] = weight(x / halfLen)
                arr[Panel.REAR.ordinal] = weight(-x / halfLen)
                arr[Panel.RIGHT.ordinal] = weight(z / hw)
                arr[Panel.LEFT.ordinal] = weight(-z / hw)
                arr[Panel.ROOF.ordinal] = if (top > 0) weight(y / top) else 0f
                arr[Panel.UNDERBODY.ordinal] = if (bottom < 0) weight(y / bottom) else 0f
                arr
            }
            return VehicleMesh(m, rest, weights, centre)
        }

        /** Curved lip proud of the body around a wheel opening, from the nose side over the top to the tail side. */
        private fun flare(m: MeshData, ax: Double, wheelY: Double, rArch: Double, side: Double, hwx: Double, color: Int, material: Float) {
            val zIn = side * (hwx - 0.03); val zOut = side * (hwx + 0.07)
            val r0 = rArch + 0.005; val r1 = rArch + 0.10
            val segs = 14
            for (k in 0 until segs) {
                val a0 = PI * k / segs; val a1 = PI * (k + 1) / segs
                val am = (a0 + a1) / 2
                val radial = Vec3(cos(am), sin(am), 0.0)
                val pi0 = Vec3(ax + r0 * cos(a0), wheelY + r0 * sin(a0), 0.0)
                val pi1 = Vec3(ax + r0 * cos(a1), wheelY + r0 * sin(a1), 0.0)
                val po0 = Vec3(ax + r1 * cos(a0), wheelY + r1 * sin(a0), 0.0)
                val po1 = Vec3(ax + r1 * cos(a1), wheelY + r1 * sin(a1), 0.0)
                m.addQuadFacing(po0.withZ(zIn), po1.withZ(zIn), po1.withZ(zOut), po0.withZ(zOut), radial, color, material)
                m.addQuadFacing(pi0.withZ(zOut), po0.withZ(zOut), po1.withZ(zOut), pi1.withZ(zOut), Vec3(0.0, 0.0, side), color, material)
                m.addQuadFacing(pi0.withZ(zIn), pi1.withZ(zIn), pi1.withZ(zOut), pi0.withZ(zOut), -radial, WELL_RGB, Material.MATTE)
            }
        }

        private fun Vec3.withZ(z: Double) = Vec3(x, y, z)

        /** 0 below 0.2, ramping to 1 at 1.0, so only vertices near a panel move with it. */
        private fun weight(t: Double): Float {
            val c = ((t - 0.2) / 0.8).coerceIn(0.0, 1.0)
            return (c * c).toFloat()
        }

        /**
         * Tyre with rounded shoulders, a barrel and dished alloy rims with five
         * spokes on both faces (both show when a wheel tears off and tumbles).
         */
        fun buildWheel(spec: VehicleSpec): MeshData {
            val m = MeshData()
            val r = spec.wheelRadiusM
            val hw = spec.wheelWidthM / 2
            val truck = spec.bodyStyle == BodyStyle.PICKUP || spec.bodyStyle == BodyStyle.SUV || spec.bodyStyle == BodyStyle.OFFROAD
            val rRim = r * if (truck) 0.58 else 0.66
            m.addRevolveZ(
                listOf(
                    rRim to -hw * 0.80, r * 0.88 to -hw * 0.97, r * 0.975 to -hw * 0.80, r to -hw * 0.45,
                    r to hw * 0.45, r * 0.975 to hw * 0.80, r * 0.88 to hw * 0.97, rRim to hw * 0.80,
                ),
                segments = 28, color = RUBBER_RGB, material = Material.MATTE, shadeStripe = 0.84f,
            )
            // Barrel between the beads, seen between the spokes.
            m.addRevolveZ(listOf(rRim * 0.98 to -hw * 0.80, rRim * 0.98 to hw * 0.80), 28, RIM_DISH_RGB, Material.ALLOY)
            for (side in listOf(-1.0, 1.0)) {
                val zf = side * hw * 0.62
                m.addRevolveZ(listOf(0.0 to zf, rRim * 0.92 to zf), 28, RIM_DISH_RGB, Material.ALLOY)
                for (k in 0 until 5) {
                    val a = 2 * PI * k / 5 + 0.3
                    val c = Vec3(cos(a) * rRim * 0.48, sin(a) * rRim * 0.48, side * hw * 0.70)
                    m.addBoxRotated(c, Vec3(rRim * 0.92, rRim * 0.20, hw * 0.16), ALLOY_RGB, Material.ALLOY, Quat.axisAngle(Vec3.Z, a))
                }
            }
            m.addCylinderZ(rRim * 0.20, hw * 1.5, 12, CHROME_RGB, CHROME_RGB, Material.CHROME)
            return m
        }
    }
}
