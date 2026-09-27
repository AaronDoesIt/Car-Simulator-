package com.carsimulator.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carsimulator.app.sim.Vehicle
import com.carsimulator.app.sim.VehicleSpec
import com.carsimulator.app.sim.step
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Owns the simulation loop and exposes the current [Vehicle] to the UI.
 *
 * The loop runs at a fixed [TICK_MS] cadence on the view-model scope so it
 * survives configuration changes and stops automatically when cleared.
 */
class SimulationViewModel(
    val spec: VehicleSpec = VehicleSpec(),
) : ViewModel() {

    private val _vehicle = MutableStateFlow(Vehicle())
    val vehicle: StateFlow<Vehicle> = _vehicle

    private var loop: Job? = null

    init {
        start()
    }

    fun setThrottle(value: Double) = _vehicle.update { it.copy(throttle = value.coerceIn(0.0, 1.0)) }

    fun setBrake(value: Double) = _vehicle.update { it.copy(brake = value.coerceIn(0.0, 1.0)) }

    fun reset() = _vehicle.update { Vehicle() }

    private fun start() {
        loop?.cancel()
        loop = viewModelScope.launch {
            var last = System.nanoTime()
            while (true) {
                delay(TICK_MS)
                val now = System.nanoTime()
                val dt = (now - last) / 1_000_000_000.0
                last = now
                _vehicle.update { it.step(spec, dt) }
            }
        }
    }

    companion object {
        const val TICK_MS = 16L
    }
}
