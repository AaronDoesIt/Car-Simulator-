package com.carsimulator.app.render

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import com.carsimulator.app.game.GameSession
import com.carsimulator.app.game.Phase
import com.carsimulator.app.sim.Quat
import com.carsimulator.app.sim.Vec3
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.atan2

/** Snapshot of what the HUD needs, published once per frame. */
data class HudState(
    val speedMph: Double = 0.0,
    val phase: Phase = Phase.LAUNCH,
    val countdown: Double = 0.0,
    val timeScale: Double = 1.0,
    val damagePercent: Int = 0,
    val wheelsLost: Int = 0,
    val boosted: Boolean = false,
)

/**
 * Draws one [GameSession] and steps it every frame on the GL thread.
 *
 * Input arrives through [steer] from the UI thread; output leaves through
 * [onHud] every frame and [onResults] once when the run ends.
 */
class GameRenderer(
    private val session: GameSession,
    private val onHud: (HudState) -> Unit,
    private val onResults: () -> Unit,
) : GLSurfaceView.Renderer {

    @Volatile var steer: Double = 0.0

    private lateinit var program: ShaderProgram
    private lateinit var skyMesh: GlMesh
    private lateinit var trackMeshes: List<GlMesh>
    private lateinit var sceneryMeshes: List<GlMesh>
    private lateinit var shadowMesh: GlMesh
    private lateinit var bodyMesh: GlMesh
    private lateinit var wheelMesh: GlMesh
    private lateinit var vehicleMesh: VehicleMesh

    private val projection = FloatArray(16)
    private val view = FloatArray(16)
    private val viewProjection = FloatArray(16)
    private val model = FloatArray(16)
    private val mvp = FloatArray(16)
    private val identity = FloatArray(16).also { Matrix.setIdentityM(it, 0) }

    private var aspect = 1f
    private var lastFrameNanos = 0L
    private var resultsSent = false
    private var lastDamageUploaded = -1.0

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(SKY_HORIZON[0], SKY_HORIZON[1], SKY_HORIZON[2], 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_CULL_FACE)
        GLES20.glCullFace(GLES20.GL_BACK)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        program = ShaderProgram()
        skyMesh = GlMesh(buildSky())
        trackMeshes = TrackMeshBuilder.build(session.level.track).map { GlMesh(it) }
        sceneryMeshes = SceneryBuilder.build(session.level.track).map { GlMesh(it) }
        shadowMesh = GlMesh(MeshData().also { it.addDisc(24, 0x000000) })
        vehicleMesh = VehicleMesh.build(session.spec)
        bodyMesh = GlMesh(vehicleMesh.data, dynamic = true)
        wheelMesh = GlMesh(VehicleMesh.buildWheel(session.spec))
        lastFrameNanos = 0L
        lastDamageUploaded = -1.0
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        aspect = width.toFloat() / height.coerceAtLeast(1)
    }

    override fun onDrawFrame(gl: GL10?) {
        val now = System.nanoTime()
        val dt = if (lastFrameNanos == 0L) 1.0 / 60 else (now - lastFrameNanos) / 1e9
        lastFrameNanos = now

        session.setSteer(steer)
        session.update(dt)
        publishHud()

        if (session.phase == Phase.RESULTS && !resultsSent) {
            resultsSent = true
            onResults()
        }

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        program.use()
        GLES20.glUniform3f(program.uLightDir, 0.30f, 0.80f, 0.52f)
        GLES20.glUniform3f(program.uFog, SKY_HORIZON[0], SKY_HORIZON[1], SKY_HORIZON[2])
        GLES20.glUniform3f(program.uSky, 0.62f, 0.74f, 0.92f)
        GLES20.glUniform3f(program.uGround, 0.38f, 0.36f, 0.30f)
        GLES20.glUniform1f(program.uAlpha, 1f)

        // Sky: full-screen gradient, no depth, no lighting.
        GLES20.glDepthMask(false)
        GLES20.glDisable(GLES20.GL_CULL_FACE)
        GLES20.glUniform1f(program.uUnlit, 1f)
        setMatricesRaw(identity, identity)
        skyMesh.draw(program)
        GLES20.glUniform1f(program.uUnlit, 0f)
        GLES20.glEnable(GLES20.GL_CULL_FACE)
        GLES20.glDepthMask(true)

        val cam = session.camera.state
        Matrix.perspectiveM(projection, 0, cam.fovDegrees.toFloat(), aspect, 0.5f, 3000f)
        Matrix.setLookAtM(
            view, 0,
            cam.eye.x.toFloat(), cam.eye.y.toFloat(), cam.eye.z.toFloat(),
            cam.target.x.toFloat(), cam.target.y.toFloat(), cam.target.z.toFloat(),
            0f, 1f, 0f,
        )
        Matrix.multiplyMM(viewProjection, 0, projection, 0, view, 0)

        // Landscape and track.
        Matrix.setIdentityM(model, 0)
        setMatrices(model)
        for (m in trackMeshes) m.draw(program)
        for (m in sceneryMeshes) m.draw(program)

        val v = session.vehicle

        // Soft shadow on the ground under the car, fading as it climbs.
        val groundY = session.level.track.heightAt(v.position.x, v.position.z)
        val heightAbove = (v.position.y - v.spec.cgHeightM - groundY).coerceAtLeast(0.0)
        val shadowAlpha = (0.45 * (1.0 - heightAbove / 10.0)).coerceIn(0.0, 0.45).toFloat()
        if (shadowAlpha > 0.02f) {
            val fwd = v.orientation.forward
            val yawDeg = Math.toDegrees(atan2(fwd.z, fwd.x)).toFloat()
            Matrix.setIdentityM(model, 0)
            Matrix.translateM(model, 0, v.position.x.toFloat(), (groundY + 0.06).toFloat(), v.position.z.toFloat())
            Matrix.rotateM(model, 0, -yawDeg, 0f, 1f, 0f)
            Matrix.scaleM(model, 0, (v.spec.lengthM * 0.55).toFloat(), 1f, (v.spec.widthM * 0.62).toFloat())
            GLES20.glEnable(GLES20.GL_BLEND)
            GLES20.glDepthMask(false)
            GLES20.glUniform1f(program.uUnlit, 1f)
            GLES20.glUniform1f(program.uAlpha, shadowAlpha)
            setMatrices(model)
            shadowMesh.draw(program)
            GLES20.glUniform1f(program.uAlpha, 1f)
            GLES20.glUniform1f(program.uUnlit, 0f)
            GLES20.glDepthMask(true)
            GLES20.glDisable(GLES20.GL_BLEND)
        }

        // Body, crumpled to the current damage.
        val damage = v.damage.total
        if (damage != lastDamageUploaded) {
            vehicleMesh.deform(v.damage, bodyMesh.vertexArray)
            bodyMesh.updateVertices()
            lastDamageUploaded = damage
        }
        modelFrom(v.position, v.orientation, model)
        setMatrices(model)
        bodyMesh.draw(program)

        // Wheels: attached ones follow the body; torn-off ones tumble on their own.
        for (w in v.wheels) {
            val rot = if (w.detached) {
                Quat.axisAngle(Vec3.Z, -w.spinAngle) * Quat.axisAngle(Vec3.Y, 0.3)
            } else {
                v.orientation * Quat.axisAngle(Vec3.Y, -w.steerAngle) * Quat.axisAngle(Vec3.Z, -w.spinAngle)
            }
            modelFrom(w.centerWorld, rot, model)
            setMatrices(model)
            wheelMesh.draw(program)
        }
    }

    private fun publishHud() {
        val v = session.vehicle
        onHud(
            HudState(
                speedMph = v.speed / 0.44704,
                phase = session.phase,
                countdown = session.countdownRemaining,
                timeScale = session.timeScale,
                damagePercent = (v.damage.total * 100).toInt(),
                wheelsLost = v.wheelsLost,
                boosted = v.boosted,
            ),
        )
    }

    private fun setMatrices(modelMatrix: FloatArray) {
        Matrix.multiplyMM(mvp, 0, viewProjection, 0, modelMatrix, 0)
        setMatricesRaw(mvp, modelMatrix)
    }

    private fun setMatricesRaw(mvpMatrix: FloatArray, modelMatrix: FloatArray) {
        GLES20.glUniformMatrix4fv(program.uMvp, 1, false, mvpMatrix, 0)
        GLES20.glUniformMatrix4fv(program.uModel, 1, false, modelMatrix, 0)
    }

    /** Column-major 4x4 from a position and a rotation quaternion. */
    private fun modelFrom(p: Vec3, q: Quat, out: FloatArray) {
        val r = q.toMatrix3() // row-major r[row*3+col]
        out[0] = r[0].toFloat(); out[1] = r[3].toFloat(); out[2] = r[6].toFloat(); out[3] = 0f
        out[4] = r[1].toFloat(); out[5] = r[4].toFloat(); out[6] = r[7].toFloat(); out[7] = 0f
        out[8] = r[2].toFloat(); out[9] = r[5].toFloat(); out[10] = r[8].toFloat(); out[11] = 0f
        out[12] = p.x.toFloat(); out[13] = p.y.toFloat(); out[14] = p.z.toFloat(); out[15] = 1f
    }

    /** Two triangles in clip space at the far plane: deep blue up top, pale at the horizon. */
    private fun buildSky(): MeshData {
        val m = MeshData()
        val z = 0.9999
        val bl = m.addVertex(Vec3(-1.0, -1.0, z), Vec3.Y, SKY_HORIZON[0], SKY_HORIZON[1], SKY_HORIZON[2])
        val br = m.addVertex(Vec3(1.0, -1.0, z), Vec3.Y, SKY_HORIZON[0], SKY_HORIZON[1], SKY_HORIZON[2])
        val tr = m.addVertex(Vec3(1.0, 1.0, z), Vec3.Y, SKY_ZENITH[0], SKY_ZENITH[1], SKY_ZENITH[2])
        val tl = m.addVertex(Vec3(-1.0, 1.0, z), Vec3.Y, SKY_ZENITH[0], SKY_ZENITH[1], SKY_ZENITH[2])
        m.addQuad(bl, br, tr, tl)
        return m
    }

    companion object {
        private val SKY_HORIZON = floatArrayOf(0.80f, 0.88f, 0.97f)
        private val SKY_ZENITH = floatArrayOf(0.22f, 0.46f, 0.90f)
    }
}
