package com.carsimulator.app

import androidx.lifecycle.ViewModel
import com.carsimulator.app.game.GameSession
import com.carsimulator.app.game.LevelCatalog
import com.carsimulator.app.game.RunResult
import com.carsimulator.app.render.HudState
import com.carsimulator.app.sim.VehicleCatalog
import com.carsimulator.app.sim.VehicleSpec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Which full-screen view is showing. */
sealed interface Screen {
    data object Garage : Screen
    data class Run(val session: GameSession) : Screen
    data class Results(val result: RunResult, val session: GameSession) : Screen
}

/** App-level state: chosen vehicle, current level, the run in progress. */
class GameViewModel : ViewModel() {

    private val _screen = MutableStateFlow<Screen>(Screen.Garage)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    private val _selectedVehicle = MutableStateFlow(VehicleCatalog.all.first())
    val selectedVehicle: StateFlow<VehicleSpec> = _selectedVehicle.asStateFlow()

    private val _level = MutableStateFlow(1)
    val level: StateFlow<Int> = _level.asStateFlow()

    private val _hud = MutableStateFlow(HudState())
    val hud: StateFlow<HudState> = _hud.asStateFlow()

    private val _bestScore = MutableStateFlow(0)
    val bestScore: StateFlow<Int> = _bestScore.asStateFlow()

    fun selectVehicle(spec: VehicleSpec) {
        _selectedVehicle.value = spec
    }

    fun startRun() {
        val session = GameSession(_selectedVehicle.value, LevelCatalog.build(_level.value))
        _hud.value = HudState()
        _screen.value = Screen.Run(session)
    }

    fun onHud(state: HudState) {
        _hud.value = state
    }

    fun finishRun(session: GameSession) {
        val result = session.result()
        if (result.score > _bestScore.value) _bestScore.value = result.score
        _screen.value = Screen.Results(result, session)
    }

    fun nextLevel() {
        _level.value += 1
        startRun()
    }

    fun retry() = startRun()

    fun backToGarage() {
        _screen.value = Screen.Garage
    }

    fun resetProgress() {
        _level.value = 1
        _screen.value = Screen.Garage
    }
}
