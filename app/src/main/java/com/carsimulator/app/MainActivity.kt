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
import com.carsimulator.app.ui.GarageScreen
import com.carsimulator.app.ui.ResultsScreen
import com.carsimulator.app.ui.RunScreen

class MainActivity : ComponentActivity() {

    private val viewModel: GameViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

                when (val s = screen) {
                    is Screen.Garage -> GarageScreen(
                        level = level,
                        selected = selected,
                        bestScore = best,
                        onSelect = viewModel::selectVehicle,
                        onStart = viewModel::startRun,
                        onResetProgress = viewModel::resetProgress,
                    )
                    is Screen.Run -> RunScreen(
                        session = s.session,
                        hudFlow = viewModel.hud,
                        onHud = viewModel::onHud,
                        onResults = { runOnUiThread { viewModel.finishRun(s.session) } },
                    )
                    is Screen.Results -> ResultsScreen(
                        result = s.result,
                        onNextLevel = viewModel::nextLevel,
                        onRetry = viewModel::retry,
                        onGarage = viewModel::backToGarage,
                    )
                }
            }
        }
    }
}
