package com.carsimulator.app.render

import com.carsimulator.app.sim.Quat
import com.carsimulator.app.sim.Vec3
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * CPU-side triangle mesh. Vertices are interleaved: position (3), normal (3),
 * colour (3), material (1). Everything the game draws is built with this,
 * there are no model files.
 */
class MeshData {
    private var vertexData = FloatArray(STRIDE * 256)
    private var vertexFloats = 0
    private var indexData = ShortArray(512)
    private var indexShorts = 0

    val vertexCount: Int get() = vertexFloats / STRIDE
    val indexCount: Int get() = indexShorts

    fun vertexArray(): FloatArray = vertexData.copyOf(vertexFloats)
    fun indexArray(): ShortArray = indexData.copyOf(indexShorts)

    fun addVertex(p: Vec3, n: Vec3, r: Float, g: Float, b: Float, material: Float = Material.MATTE): Int {
        val i = vertexCount
        check(i < 65535) { "Mesh exceeds 16-bit index range" }
        if (vertexFloats + STRIDE > vertexData.size) vertexData = vertexData.copyOf(vertexData.size * 2)
        val d = vertexData
        var k = vertexFloats
        d[k++] = p.x.toFloat(); d[k++] = p.y.toFloat(); d[k++] = p.z.toFloat()
        d[k++] = n.x.toFloat(); d[k++] = n.y.toFloat(); d[k++] = n.z.toFloat()
        d[k++] = r; d[k++] = g; d[k++] = b
        d[k++] = material
        vertexFloats = k
        return i
    }

    fun addVertex(p: Vec3, n: Vec3, color: Int, material: Float): Int {
        val (r, g, b) = rgb(color)
        return addVertex(p, n, r, g, b, material)
    }

    fun addTriangle(a: Int, b: Int, c: Int) {
        if (indexShorts + 3 > indexData.size) indexData = indexData.copyOf(indexData.size * 2)
        indexData[indexShorts++] = a.toShort()
        indexData[indexShorts++] = b.toShort()
        indexData[indexShorts++] = c.toShort()
    }

    fun addQuad(a: Int, b: Int, c: Int, d: Int) {
        addTriangle(a, b, c)
        addTriangle(a, c, d)
    }

    /**
     * Flat quad from four corners in loop order, wound so its normal points the
     * way [outward] does. The workhorse for ad-hoc trim geometry.
     */
    fun addQuadFacing(p0: Vec3, p1: Vec3, p2: Vec3, p3: Vec3, outward: Vec3, color: Int, material: Float) {
        var n = (p1 - p0) cross (p2 - p0)
        if (n.lengthSquared < 1e-14) n = (p2 - p0) cross (p3 - p0)
        if (n.lengthSquared < 1e-14) return
        n = n.normalized()
        val flip = (n dot outward) < 0
        val nn = if (flip) -n else n
        val i0 = addVertex(p0, nn, color, material)
        val i1 = addVertex(p1, nn, color, material)
        val i2 = addVertex(p2, nn, color, material)
        val i3 = addVertex(p3, nn, color, material)
        if (flip) addQuad(i0, i3, i2, i1) else addQuad(i0, i1, i2, i3)
    }

    /** Axis-aligned box in local space, flat-shaded (4 vertices per face). */
    fun addBox(center: Vec3, size: Vec3, color: Int, material: Float = Material.MATTE) {
        addBoxRotated(center, size, color, material, null)
    }

    /** Box rotated by [rotation] about its own centre (null for axis-aligned). */
    fun addBoxRotated(center: Vec3, size: Vec3, color: Int, material: Float, rotation: Quat?) {
        val (r, g, b) = rgb(color)
        val hx = size.x / 2; val hy = size.y / 2; val hz = size.z / 2
        val faces = arrayOf(
            // normal, then four corners counter-clockwise seen from outside
            arrayOf(Vec3(1.0, 0.0, 0.0), Vec3(hx, -hy, -hz), Vec3(hx, hy, -hz), Vec3(hx, hy, hz), Vec3(hx, -hy, hz)),
            arrayOf(Vec3(-1.0, 0.0, 0.0), Vec3(-hx, -hy, hz), Vec3(-hx, hy, hz), Vec3(-hx, hy, -hz), Vec3(-hx, -hy, -hz)),
            arrayOf(Vec3(0.0, 1.0, 0.0), Vec3(-hx, hy, -hz), Vec3(-hx, hy, hz), Vec3(hx, hy, hz), Vec3(hx, hy, -hz)),
            arrayOf(Vec3(0.0, -1.0, 0.0), Vec3(-hx, -hy, hz), Vec3(-hx, -hy, -hz), Vec3(hx, -hy, -hz), Vec3(hx, -hy, hz)),
            arrayOf(Vec3(0.0, 0.0, 1.0), Vec3(-hx, -hy, hz), Vec3(hx, -hy, hz), Vec3(hx, hy, hz), Vec3(-hx, hy, hz)),
            arrayOf(Vec3(0.0, 0.0, -1.0), Vec3(hx, -hy, -hz), Vec3(-hx, -hy, -hz), Vec3(-hx, hy, -hz), Vec3(hx, hy, -hz)),
        )
        fun place(v: Vec3) = center + (rotation?.rotate(v) ?: v)
        for (f in faces) {
            val n = rotation?.rotate(f[0]) ?: f[0]
            val i0 = addVertex(place(f[1]), n, r, g, b, material)
            val i1 = addVertex(place(f[2]), n, r, g, b, material)
            val i2 = addVertex(place(f[3]), n, r, g, b, material)
            val i3 = addVertex(place(f[4]), n, r, g, b, material)
            addQuad(i0, i1, i2, i3)
        }
    }

    /**
     * Six-sided solid from eight corners: [c] 0..3 are the bottom quad and
     * 4..7 the top quad, both ordered rear-left, front-left, front-right,
     * rear-right (X forward, Z right). Faces are flat shaded and wound so
     * their normals point away from the centroid, whatever the corner layout.
     * [faceColors] order: bottom, top, front, back, left, right.
     */
    fun addHull(c: Array<Vec3>, faceColors: IntArray, material: Float = Material.MATTE) {
        require(c.size == 8 && faceColors.size == 6)
        val centroid = c.reduce { a, b -> a + b } / 8.0
        val faces = arrayOf(
            intArrayOf(0, 1, 2, 3), intArrayOf(4, 5, 6, 7),
            intArrayOf(1, 2, 6, 5), intArrayOf(0, 3, 7, 4),
            intArrayOf(0, 1, 5, 4), intArrayOf(3, 2, 6, 7),
        )
        for ((fi, f) in faces.withIndex()) {
            val p0 = c[f[0]]; val p1 = c[f[1]]; val p2 = c[f[2]]; val p3 = c[f[3]]
            val faceCentre = (p0 + p1 + p2 + p3) / 4.0
            addQuadFacing(p0, p1, p2, p3, faceCentre - centroid, faceColors[fi], material)
        }
    }

    /** Cone with its base on y = 0 and apex at y = [height], open underneath, smooth shaded. */
    fun addCone(center: Vec3, radius: Double, height: Double, segments: Int, color: Int, material: Float = Material.MATTE) {
        val (r, g, b) = rgb(color)
        val apex = center + Vec3(0.0, height, 0.0)
        // Surface normal of a cone at angle a: (h cos a, r, h sin a) normalised.
        val ring = IntArray(segments) { i ->
            val a = 2 * PI * i / segments
            val n = Vec3(height * cos(a), radius, height * sin(a)).normalized()
            addVertex(center + Vec3(cos(a) * radius, 0.0, sin(a) * radius), n, r, g, b, material)
        }
        for (i in 0 until segments) {
            val a = 2 * PI * (i + 0.5) / segments
            val n = Vec3(height * cos(a), radius, height * sin(a)).normalized()
            val top = addVertex(apex, n, r, g, b, material)
            // Angle increases clockwise seen from +Y, so wind i+1 -> i for an outward face.
            addTriangle(ring[(i + 1) % segments], ring[i], top)
        }
    }

    /** Flat disc in the XZ plane facing +Y, unit radius, centred on the origin. */
    fun addDisc(segments: Int, color: Int, material: Float = Material.MATTE) {
        val (r, g, b) = rgb(color)
        val centre = addVertex(Vec3.ZERO, Vec3.Y, r, g, b, material)
        val ring = IntArray(segments) { i ->
            val a = 2 * PI * i / segments
            addVertex(Vec3(cos(a), 0.0, sin(a)), Vec3.Y, r, g, b, material)
        }
        for (i in 0 until segments) {
            // Counter-clockwise seen from above (+Y): angle decreasing.
            addTriangle(centre, ring[(i + 1) % segments], ring[i])
        }
    }

    /** Cylinder whose axis runs along local Z (a wheel), centred on the origin. */
    fun addCylinderZ(radius: Double, width: Double, segments: Int, sideColor: Int, capColor: Int, material: Float = Material.MATTE) {
        val (sr, sg, sb) = rgb(sideColor)
        val (cr, cg, cb) = rgb(capColor)
        val hw = width / 2
        val ring = ArrayList<Vec3>()
        for (i in 0 until segments) {
            val a = 2 * PI * i / segments
            ring.add(Vec3(cos(a) * radius, sin(a) * radius, 0.0))
        }
        for (i in 0 until segments) {
            val p0 = ring[i]; val p1 = ring[(i + 1) % segments]
            val n = ((p0 + p1) * 0.5).normalized()
            val k = if (i % 2 == 0) 1f else 0.7f
            val a = addVertex(Vec3(p0.x, p0.y, -hw), n, sr * k, sg * k, sb * k, material)
            val b = addVertex(Vec3(p1.x, p1.y, -hw), n, sr * k, sg * k, sb * k, material)
            val c = addVertex(Vec3(p1.x, p1.y, hw), n, sr * k, sg * k, sb * k, material)
            val d = addVertex(Vec3(p0.x, p0.y, hw), n, sr * k, sg * k, sb * k, material)
            addQuad(a, b, c, d)
        }
        for (side in intArrayOf(-1, 1)) {
            val n = Vec3(0.0, 0.0, side.toDouble())
            val centre = addVertex(Vec3(0.0, 0.0, side * hw), n, cr, cg, cb, material)
            for (i in 0 until segments) {
                val p0 = ring[i]; val p1 = ring[(i + 1) % segments]
                val a = addVertex(Vec3(p0.x, p0.y, side * hw), n, cr, cg, cb, material)
                val b = addVertex(Vec3(p1.x, p1.y, side * hw), n, cr, cg, cb, material)
                if (side > 0) addTriangle(centre, a, b) else addTriangle(centre, b, a)
            }
        }
    }

    /** One vertex of a loft ring: where it is and what it is made of. */
    class LoftPoint(val p: Vec3, val color: Int, val material: Float)

    /**
     * Skins consecutive closed rings into one smooth surface. Every ring has
     * the same point count; vertices are shared between neighbouring quads so
     * normals average across them. Winding is decided once for the whole
     * surface by majority vote against [centreOf], the axis point each ring
     * wraps around, which keeps concave pockets (wheel wells, a truck bed)
     * facing the right way. Degenerate rings (all points coincident) are fine
     * and simply pinch the surface closed.
     */
    fun addLoft(
        rings: List<List<LoftPoint>>, centreOf: (Int) -> Vec3,
        capStart: Boolean = false, capEnd: Boolean = false,
        capColor: Int? = null, capMaterial: Float? = null,
    ) {
        require(rings.size >= 2)
        val n = rings[0].size
        require(rings.all { it.size == n })
        val normals = Array(rings.size) { Array(n) { Vec3.ZERO } }
        var vote = 0.0
        val faceNormals = Array(rings.size - 1) { Array(n) { Vec3.ZERO } }
        for (i in 0 until rings.size - 1) {
            val c = (centreOf(i) + centreOf(i + 1)) * 0.5
            for (k in 0 until n) {
                val k1 = (k + 1) % n
                val a = rings[i][k].p; val b = rings[i][k1].p
                val cc = rings[i + 1][k1].p; val d = rings[i + 1][k].p
                var fn = (b - a) cross (cc - a)
                if (fn.lengthSquared < 1e-16) fn = (cc - a) cross (d - a)
                faceNormals[i][k] = fn // area weighted
                val centre = (a + b + cc + d) * 0.25
                vote += fn dot (centre - c)
            }
        }
        val flip = vote < 0
        for (i in 0 until rings.size - 1) for (k in 0 until n) {
            val fn = if (flip) -faceNormals[i][k] else faceNormals[i][k]
            val k1 = (k + 1) % n
            normals[i][k] += fn; normals[i][k1] += fn
            normals[i + 1][k1] += fn; normals[i + 1][k] += fn
        }
        val ids = Array(rings.size) { i ->
            IntArray(n) { k ->
                val nn = normals[i][k].let { if (it.lengthSquared < 1e-18) Vec3.Y else it.normalized() }
                val lp = rings[i][k]
                addVertex(lp.p, nn, lp.color, lp.material)
            }
        }
        for (i in 0 until rings.size - 1) for (k in 0 until n) {
            val k1 = (k + 1) % n
            if (faceNormals[i][k].lengthSquared < 1e-16) continue
            if (flip) addQuad(ids[i][k], ids[i + 1][k], ids[i + 1][k1], ids[i][k1])
            else addQuad(ids[i][k], ids[i][k1], ids[i + 1][k1], ids[i + 1][k])
        }
        if (capStart) cap(rings.first(), centreOf(0), centreOf(1) - centreOf(0), true, capColor, capMaterial)
        if (capEnd) cap(rings.last(), centreOf(rings.size - 1), centreOf(rings.size - 1) - centreOf(rings.size - 2), false, capColor, capMaterial)
    }

    /** Flat fan closing a ring; [axis] runs from ring to ring so the cap faces away from the body. */
    private fun cap(ring: List<LoftPoint>, centre: Vec3, axis: Vec3, inward: Boolean, color: Int?, material: Float?) {
        val outward = if (inward) -axis else axis
        if (outward.lengthSquared < 1e-14) return
        val nrm = outward.normalized()
        val n = ring.size
        val ctr = addVertex(centre, nrm, color ?: ring[0].color, material ?: ring[0].material)
        val ids = IntArray(n) { addVertex(ring[it].p, nrm, color ?: ring[it].color, material ?: ring[it].material) }
        // Choose the fan winding from the first non-degenerate wedge.
        var sign = 0.0
        for (k in 0 until n) {
            val a = ring[k].p - centre; val b = ring[(k + 1) % n].p - centre
            sign = (a cross b) dot nrm
            if (abs(sign) > 1e-12) break
        }
        for (k in 0 until n) {
            val k1 = (k + 1) % n
            if (sign >= 0) addTriangle(ctr, ids[k], ids[k1]) else addTriangle(ctr, ids[k1], ids[k])
        }
    }

    /**
     * Surface of revolution about local Z. [profile] lists (radius, z) pairs
     * from one end to the other; the ends are left open unless capped.
     * [shadeStripe] darkens alternate angular segments so a tyre visibly spins.
     */
    fun addRevolveZ(
        profile: List<Pair<Double, Double>>, segments: Int, color: Int, material: Float,
        shadeStripe: Float = 1f, capStart: Boolean = false, capEnd: Boolean = false,
    ) {
        val (r, g, b) = rgb(color)
        val rings = profile.map { (radius, z) ->
            List(segments) { i ->
                val a = 2 * PI * i / segments
                val k = if (i % 2 == 0) 1f else shadeStripe
                LoftPoint(Vec3(cos(a) * radius, sin(a) * radius, z), pack(r * k, g * k, b * k), material)
            }
        }
        addLoft(rings, { i -> Vec3(0.0, 0.0, profile[i].second) }, capStart, capEnd)
    }

    fun positionsCopy(): FloatArray {
        val out = FloatArray(vertexCount * 3)
        for (i in 0 until vertexCount) {
            out[i * 3] = vertexData[i * STRIDE]
            out[i * 3 + 1] = vertexData[i * STRIDE + 1]
            out[i * 3 + 2] = vertexData[i * STRIDE + 2]
        }
        return out
    }

    fun materialAt(vertex: Int): Float = vertexData[vertex * STRIDE + MATERIAL_OFFSET]

    fun normalAt(vertex: Int): Vec3 = Vec3(
        vertexData[vertex * STRIDE + NORMAL_OFFSET].toDouble(),
        vertexData[vertex * STRIDE + NORMAL_OFFSET + 1].toDouble(),
        vertexData[vertex * STRIDE + NORMAL_OFFSET + 2].toDouble(),
    )

    fun positionAt(vertex: Int): Vec3 = Vec3(
        vertexData[vertex * STRIDE].toDouble(),
        vertexData[vertex * STRIDE + 1].toDouble(),
        vertexData[vertex * STRIDE + 2].toDouble(),
    )

    companion object {
        const val STRIDE = 10
        const val NORMAL_OFFSET = 3
        const val COLOR_OFFSET = 6
        const val MATERIAL_OFFSET = 9

        fun rgb(color: Int): Triple<Float, Float, Float> = Triple(
            ((color shr 16) and 0xFF) / 255f,
            ((color shr 8) and 0xFF) / 255f,
            (color and 0xFF) / 255f,
        )

        fun pack(r: Float, g: Float, b: Float): Int =
            ((r.coerceIn(0f, 1f) * 255).toInt() shl 16) or ((g.coerceIn(0f, 1f) * 255).toInt() shl 8) or (b.coerceIn(0f, 1f) * 255).toInt()

        /** Scale a packed colour's brightness. */
        fun shade(color: Int, k: Float): Int {
            val (r, g, b) = rgb(color)
            return pack(r * k, g * k, b * k)
        }
    }
}
