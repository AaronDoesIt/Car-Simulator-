package com.carsimulator.app.ui

import android.opengl.GLSurfaceView
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.carsimulator.app.game.GameSession
import com.carsimulator.app.game.Phase
import com.carsimulator.app.game.RunResult
import com.carsimulator.app.render.GameRenderer
import com.carsimulator.app.render.HudState
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Hosts the GL view. Dragging a finger left or right anywhere on the screen
 * steers; the wheel returns to centre when the finger lifts. Once [result]
 * arrives the wreck stays on screen and the score card is laid over it.
 */
@Composable
fun RunScreen(
    session: GameSession,
    hudFlow: StateFlow<HudState>,
    onHud: (HudState) -> Unit,
    onResults: () -> Unit,
    result: RunResult?,
    onNextLevel: () -> Unit,
    onRetry: () -> Unit,
    onGarage: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val hud by hudFlow.collectAsStateWithLifecycle()

    val assets = context.assets
    val renderer = remember(session) {
        GameRenderer(session, onHud, onResults) { path ->
            try {
                assets.open(path).use { it.readBytes() }
            } catch (e: java.io.IOException) {
                null
            }
        }
    }
    val glView = remember(session) {
        GLSurfaceView(context).apply {
            setEGLContextClientVersion(2)
            setEGLConfigChooser(8, 8, 8, 8, 16, 0)
            setRenderer(renderer)
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        }
    }

    DisposableEffect(lifecycleOwner, glView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> glView.onResume()
                Lifecycle.Event.ON_PAUSE -> glView.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { glView }, modifier = Modifier.fillMaxSize())

        // Drag layer: absolute offset from where the finger went down maps to steering.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(session) {
                    val fullLockPx = size.width * 0.22f
                    var total = 0f
                    detectDragGestures(
                        onDragStart = { total = 0f; renderer.steer = 0.0 },
                        onDragEnd = { renderer.steer = 0.0 },
                        onDragCancel = { renderer.steer = 0.0 },
                        onDrag = { change, amount ->
                            change.consume()
                            total += amount.x
                            renderer.steer = (total / fullLockPx).coerceIn(-1f, 1f).toDouble()
                        },
                    )
                },
        )

        if (result == null) {
            Hud(hud, session, modifier = Modifier.align(Alignment.TopStart))

            if (hud.phase == Phase.LAUNCH && hud.countdown > 0) {
                Text(
                    text = ceil(hud.countdown).toInt().toString(),
                    color = Color.White,
                    fontSize = 120.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            if (hud.phase == Phase.LAUNCH && hud.countdown <= 0 && !hud.boosted) {
                Text(
                    text = "DRAG TO STEER  →  HIT THE BLUE STRIP",
                    color = Color.White.copy(alpha = 0.85f),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp),
                )
            }
            if (hud.timeScale < 0.6) {
                Text(
                    text = "SLOW MOTION",
                    color = Color(0xFFFFA000),
                    fontWeight = FontWeight.Black,
                    fontSize = 22.sp,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp),
                )
            }
        } else {
            ResultsOverlay(result, onNextLevel, onRetry, onGarage)
        }
    }
}

@Composable
private fun Hud(hud: HudState, session: GameSession, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .safeDrawingPadding()
            .padding(16.dp)
            .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(
            "${hud.speedMph.roundToInt()} mph",
            color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Black,
        )
        Text(
            "Level ${session.level.number} · ${session.level.courseName} · target ${session.level.targetSpeedMph.roundToInt()} mph",
            color = Color(0xFFDDDDDD), style = MaterialTheme.typography.bodySmall,
        )
        Text(
            "Damage ${hud.damagePercent}%" + if (hud.wheelsLost > 0) " · wheels lost ${hud.wheelsLost}" else "",
            color = if (hud.damagePercent > 50) Color(0xFFFF5252) else Color(0xFFDDDDDD),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
