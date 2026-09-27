package com.carsimulator.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.carsimulator.app.R
import com.carsimulator.app.SimulationViewModel
import kotlin.math.roundToInt

/**
 * Landscape dashboard: brake pedal on the left, gauges in the middle,
 * accelerator on the right. Pedals are press-and-hold.
 */
@Composable
fun DashboardScreen(viewModel: SimulationViewModel) {
    val vehicle by viewModel.vehicle.collectAsStateWithLifecycle()

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .safeDrawingPadding()
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Pedal(
            label = stringResource(R.string.brake),
            color = Color(0xFFB71C1C),
            onPressedChange = { pressed -> viewModel.setBrake(if (pressed) 1.0 else 0.0) },
            modifier = Modifier.weight(1f),
        )

        Column(
            modifier = Modifier
                .weight(2f)
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(R.string.speed_label),
                color = Color.Gray,
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                text = vehicle.speedKmh.roundToInt().toString(),
                color = Color.White,
                fontSize = 96.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.speed_units),
                color = Color.Gray,
                style = MaterialTheme.typography.labelLarge,
            )

            Spacer(Modifier.height(24.dp))

            Text(
                text = "${stringResource(R.string.gear_label)} ${vehicle.gear}",
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall,
            )

            Spacer(Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { vehicle.rpmFraction(viewModel.spec).toFloat() },
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp)),
                color = Color(0xFFFFA000),
                trackColor = Color.DarkGray,
            )

            Spacer(Modifier.height(24.dp))

            TextButton(onClick = viewModel::reset) {
                Text("Reset", color = Color.Gray)
            }
        }

        Pedal(
            label = stringResource(R.string.accelerate),
            color = Color(0xFF2E7D32),
            onPressedChange = { pressed -> viewModel.setThrottle(if (pressed) 1.0 else 0.0) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun Pedal(
    label: String,
    color: Color,
    onPressedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(24.dp))
            .background(color)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    onPressedChange(true)
                    waitForUpOrCancellation()
                    onPressedChange(false)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = Color.White,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}
