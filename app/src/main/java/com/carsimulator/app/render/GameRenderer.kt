package com.carsimulator.app.render

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import com.carsimulator.app.game.GameSession
import com.carsimulator.app.game.Phase
import com.carsimulator.app.sim.Quat
import com.carsimulator.app.sim.Vec3
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.atan2
import kotlin.math.sqrt

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
 * [onHud] every frame and [onResults] once when the run ends. Drawing keeps
 * going after that so the wreck stays on screen behind the results card.
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
    private var detailTexture = 0

    private val projection = FloatArray(16)
    private val view = FloatArray(16)
    private val viewProjection = FloatArray(16)
    private val model = FloatArray(16)
    private val mvp = FloatArray(16)

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
        detailTexture = uploadDetailTexture()
        skyMesh = GlMesh(SkyBuilder.dome())
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
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, detailTexture)
        GLES20.glUniform1i(program.uDetail, 0)
        GLES20.glUniform3f(program.uLightDir, LIGHT_DIR[0], LIGHT_DIR[1], LIGHT_DIR[2])
        GLES20.glUniform3f(program.uFog, FOG[0], FOG[1], FOG[2])
        GLES20.glUniform3f(program.uSkyZenith, SKY_ZENITH[0], SKY_ZENITH[1], SKY_ZENITH[2])
        GLES20.glUniform3f(program.uSkyHorizon, SKY_HORIZON[0], SKY_HORIZON[1], SKY_HORIZON[2])
        GLES20.glUniform3f(program.uGround, 0.32f, 0.31f, 0.25f)
        GLES20.glUniform3f(program.uSun, 1.0f, 0.95f, 0.86f)
        GLES20.glUniform1f(program.uAlpha, 1f)

        val cam = session.camera.state
        GLES20.glUniform3f(program.uEye, cam.eye.x.toFloat(), cam.eye.y.toFloat(), cam.eye.z.toFloat())
        Matrix.perspectiveM(projection, 0, cam.fovDegrees.toFloat(), aspect, 0.5f, 3000f)
        Matrix.setLookAtM(
            view, 0,
            cam.eye.x.toFloat(), cam.eye.y.toFloat(), cam.eye.z.toFloat(),
            cam.target.x.toFloat(), cam.target.y.toFloat(), cam.target.z.toFloat(),
            0f, 1f, 0f,
        )
        Matrix.multiplyMM(viewProjection, 0, projection, 0, view, 0)

        // Sky dome rides with the camera: no depth, no culling, shaded by direction.
        GLES20.glDepthMask(false)
        GLES20.glDisable(GLES20.GL_CULL_FACE)
        GLES20.glUniform1f(program.uMode, ShaderProgram.MODE_SKY)
        Matrix.setIdentityM(model, 0)
        Matrix.translateM(model, 0, cam.eye.x.toFloat(), cam.eye.y.toFloat(), cam.eye.z.toFloat())
        setMatrices(model)
        skyMesh.draw(program)
        GLES20.glUniform1f(program.uMode, ShaderProgram.MODE_LIT)
        GLES20.glEnable(GLES20.GL_CULL_FACE)
        GLES20.glDepthMask(true)

        // Landscape and track.
        Matrix.setIdentityM(model, 0)
        setMatrices(model)
        for (m in trackMeshes) m.draw(program)
        for (m in sceneryMeshes) m.draw(program)

        val v = session.vehicle

        // Soft shadow on the ground under the car, fading as it climbs.
        val groundY = session.level.track.heightAt(v.position.x, v.position.z)
        val heightAbove = (v.position.y - v.spec.cgHeightM - groundY).coerceAtLeast(0.0)
        val shadowAlpha = (0.50 * (1.0 - heightAbove / 10.0)).coerceIn(0.0, 0.50).toFloat()
        if (shadowAlpha > 0.02f) {
            val fwd = v.orientation.forward
            val yawDeg = Math.toDegrees(atan2(fwd.z, fwd.x)).toFloat()
            Matrix.setIdentityM(model, 0)
            Matrix.translateM(model, 0, v.position.x.toFloat(), (groundY + 0.06).toFloat(), v.position.z.toFloat())
            Matrix.rotateM(model, 0, -yawDeg, 0f, 1f, 0f)
            Matrix.scaleM(model, 0, (v.spec.lengthM * 0.55).toFloat(), 1f, (v.spec.widthM * 0.62).toFloat())
            GLES20.glEnable(GLES20.GL_BLEND)
            GLES20.glDepthMask(false)
            GLES20.glUniform1f(program.uMode, ShaderProgram.MODE_UNLIT)
            GLES20.glUniform1f(program.uAlpha, shadowAlpha)
            setMatrices(model)
            shadowMesh.draw(program)
            GLES20.glUniform1f(program.uAlpha, 1f)
            GLES20.glUniform1f(program.uMode, ShaderProgram.MODE_LIT)
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
        GLES20.glUniformMatrix4fv(program.uMvp, 1, false, mvp, 0)
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

    private fun uploadDetailTexture(): Int {
        val bytes = detailBytes
        val buf = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder())
        buf.put(bytes).position(0)
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ids[0])
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, DetailTexture.SIZE, DetailTexture.SIZE, 0,
            GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buf,
        )
        GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR_MIPMAP_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_REPEAT)
        return ids[0]
    }

    companion object {
        private val SKY_HORIZON = floatArrayOf(0.74f, 0.83f, 0.94f)
        private val SKY_ZENITH = floatArrayOf(0.19f, 0.40f, 0.84f)
        private val FOG = floatArrayOf(0.74f, 0.82f, 0.92f)
        private val LIGHT_DIR = floatArrayOf(0.35f, 0.75f, 0.45f).let { d ->
            val l = sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2])
            floatArrayOf(d[0] / l, d[1] / l, d[2] / l)
        }

        /** Generated once per process; every surface (re)creation re-uploads the same bytes. */
        private val detailBytes: ByteArray by lazy { DetailTexture.build() }

        /** Build the texture ahead of the first run so the GL thread does not stall on it. */
        fun warmUp() {
            Thread({ detailBytes.size }, "detail-texture").apply { isDaemon = true }.start()
        }
    }
}
