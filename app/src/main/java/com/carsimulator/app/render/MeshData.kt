package com.carsimulator.app.render

import com.carsimulator.app.sim.Vec3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * CPU-side triangle mesh. Vertices are interleaved: position (3), normal (3),
 * colour (3). Everything the game draws is built with this, there are no
 * model files.
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

    fun addVertex(p: Vec3, n: Vec3, r: Float, g: Float, b: Float): Int {
        val i = vertexCount
        check(i < 65535) { "Mesh exceeds 16-bit index range" }
        if (vertexFloats + STRIDE > vertexData.size) vertexData = vertexData.copyOf(vertexData.size * 2)
        val d = vertexData
        var k = vertexFloats
        d[k++] = p.x.toFloat(); d[k++] = p.y.toFloat(); d[k++] = p.z.toFloat()
        d[k++] = n.x.toFloat(); d[k++] = n.y.toFloat(); d[k++] = n.z.toFloat()
        d[k++] = r; d[k++] = g; d[k++] = b
        vertexFloats = k
        return i
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

    /** Axis-aligned box in local space, flat-shaded (4 vertices per face). */
    fun addBox(center: Vec3, size: Vec3, color: Int) {
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
        for (f in faces) {
            val n = f[0]
            val i0 = addVertex(center + f[1], n, r, g, b)
            val i1 = addVertex(center + f[2], n, r, g, b)
            val i2 = addVertex(center + f[3], n, r, g, b)
            val i3 = addVertex(center + f[4], n, r, g, b)
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
    fun addHull(c: Array<Vec3>, faceColors: IntArray) {
        require(c.size == 8 && faceColors.size == 6)
        val centroid = c.reduce { a, b -> a + b } / 8.0
        val faces = arrayOf(
            intArrayOf(0, 1, 2, 3), intArrayOf(4, 5, 6, 7),
            intArrayOf(1, 2, 6, 5), intArrayOf(0, 3, 7, 4),
            intArrayOf(0, 1, 5, 4), intArrayOf(3, 2, 6, 7),
        )
        for ((fi, f) in faces.withIndex()) {
            val p0 = c[f[0]]; val p1 = c[f[1]]; val p2 = c[f[2]]; val p3 = c[f[3]]
            var n = ((p1 - p0) cross (p2 - p0))
            if (n.lengthSquared < 1e-12) n = ((p2 - p0) cross (p3 - p0))
            n = n.normalized()
            val faceCentre = (p0 + p1 + p2 + p3) / 4.0
            val outward = (n dot (faceCentre - centroid)) >= 0
            val (r, g, b) = rgb(faceColors[fi])
            val nn = if (outward) n else -n
            val i0 = addVertex(p0, nn, r, g, b)
            val i1 = addVertex(p1, nn, r, g, b)
            val i2 = addVertex(p2, nn, r, g, b)
            val i3 = addVertex(p3, nn, r, g, b)
            if (outward) addQuad(i0, i1, i2, i3) else addQuad(i0, i3, i2, i1)
        }
    }

    /** Cone with its base on y = 0 and apex at y = [height], open underneath. */
    fun addCone(center: Vec3, radius: Double, height: Double, segments: Int, color: Int) {
        val (r, g, b) = rgb(color)
        val apex = center + Vec3(0.0, height, 0.0)
        for (i in 0 until segments) {
            val a0 = 2 * PI * i / segments
            val a1 = 2 * PI * (i + 1) / segments
            val p0 = center + Vec3(cos(a0) * radius, 0.0, sin(a0) * radius)
            val p1 = center + Vec3(cos(a1) * radius, 0.0, sin(a1) * radius)
            val n = ((p1 - p0) cross (apex - p0)).normalized()
            val outward = (n dot ((p0 + p1) * 0.5 - center)) >= 0
            val nn = if (outward) n else -n
            val i0 = addVertex(p0, nn, r, g, b)
            val i1 = addVertex(p1, nn, r, g, b)
            val i2 = addVertex(apex, nn, r, g, b)
            if (outward) addTriangle(i0, i1, i2) else addTriangle(i0, i2, i1)
        }
    }

    /** Flat disc in the XZ plane facing +Y, unit radius, centred on the origin. */
    fun addDisc(segments: Int, color: Int) {
        val (r, g, b) = rgb(color)
        val centre = addVertex(Vec3.ZERO, Vec3.Y, r, g, b)
        val ring = IntArray(segments) { i ->
            val a = 2 * PI * i / segments
            addVertex(Vec3(cos(a), 0.0, sin(a)), Vec3.Y, r, g, b)
        }
        for (i in 0 until segments) {
            // Counter-clockwise seen from above (+Y): angle decreasing.
            addTriangle(centre, ring[(i + 1) % segments], ring[i])
        }
    }

    /** Cylinder whose axis runs along local Z (a wheel), centred on the origin. */
    fun addCylinderZ(radius: Double, width: Double, segments: Int, sideColor: Int, capColor: Int) {
        val (sr, sg, sb) = rgb(sideColor)
        val (cr, cg, cb) = rgb(capColor)
        val hw = width / 2
        val ring = ArrayList<Vec3>()
        for (i in 0 until segments) {
            val a = 2 * PI * i / segments
            ring.add(Vec3(cos(a) * radius, sin(a) * radius, 0.0))
        }
        // Sides, with alternating shade so the tread reads as tread when it spins.
        for (i in 0 until segments) {
            val p0 = ring[i]; val p1 = ring[(i + 1) % segments]
            val n = ((p0 + p1) * 0.5).normalized()
            val k = if (i % 2 == 0) 1f else 0.7f
            val a = addVertex(Vec3(p0.x, p0.y, -hw), n, sr * k, sg * k, sb * k)
            val b = addVertex(Vec3(p1.x, p1.y, -hw), n, sr * k, sg * k, sb * k)
            val c = addVertex(Vec3(p1.x, p1.y, hw), n, sr * k, sg * k, sb * k)
            val d = addVertex(Vec3(p0.x, p0.y, hw), n, sr * k, sg * k, sb * k)
            addQuad(a, b, c, d)
        }
        // Caps with a hub-coloured spoke look: alternate wedge colours.
        for (side in intArrayOf(-1, 1)) {
            val n = Vec3(0.0, 0.0, side.toDouble())
            val centre = addVertex(Vec3(0.0, 0.0, side * hw), n, cr, cg, cb)
            for (i in 0 until segments) {
                val p0 = ring[i]; val p1 = ring[(i + 1) % segments]
                val dark = i % 2 == 0
                val kr = if (dark) cr * 0.55f else cr; val kg = if (dark) cg * 0.55f else cg; val kb = if (dark) cb * 0.55f else cb
                val a = addVertex(Vec3(p0.x, p0.y, side * hw), n, kr, kg, kb)
                val b = addVertex(Vec3(p1.x, p1.y, side * hw), n, kr, kg, kb)
                if (side > 0) addTriangle(centre, a, b) else addTriangle(centre, b, a)
            }
        }
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

    companion object {
        const val STRIDE = 9
        fun rgb(color: Int): Triple<Float, Float, Float> = Triple(
            ((color shr 16) and 0xFF) / 255f,
            ((color shr 8) and 0xFF) / 255f,
            (color and 0xFF) / 255f,
        )
    }
}
