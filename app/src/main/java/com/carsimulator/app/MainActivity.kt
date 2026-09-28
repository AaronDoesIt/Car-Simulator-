package com.carsimulator.app

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.carsimulator.app.render.GameRenderer
import com.carsimulator.app.ui.GarageScreen
import com.carsimulator.app.ui.RunScreen

class MainActivity : ComponentActivity() {

    private val viewModel: GameViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        GameRenderer.warmUp()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                val screen by viewModel.screen.collectAsStateWithLifecycle()
                val selected by viewModel.selectedVehicle.collectAsStateWithLifecycle()
                val level by viewModel.level.collectAsStateWithLifecycle()
                val best by viewModel.bestScore.collectAsStateWithLifecycle()

                val s = screen
                if (s is Screen.Garage) {
                    GarageScreen(
                        level = level,
                        selected = selected,
                        bestScore = best,
                        onSelect = viewModel::selectVehicle,
                        onStart = viewModel::startRun,
                        onResetProgress = viewModel::resetProgress,
                    )
                } else {
                    // Run and Results share one call site so the GL view (and the wreck in it)
                    // survives the switch and the score card is drawn over it.
                    val session = when (s) {
                        is Screen.Run -> s.session
                        is Screen.Results -> s.session
                        else -> error("unreachable")
                    }
                    RunScreen(
                        session = session,
                        hudFlow = viewModel.hud,
                        onHud = viewModel::onHud,
                        onResults = { runOnUiThread { viewModel.finishRun(session) } },
                        result = (s as? Screen.Results)?.result,
                        onNextLevel = viewModel::nextLevel,
                        onRetry = viewModel::retry,
                        onGarage = viewModel::backToGarage,
                    )
                }
            }
        }
    }
}
