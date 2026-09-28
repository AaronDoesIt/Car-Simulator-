package com.carsimulator.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carsimulator.app.game.LevelCatalog
import com.carsimulator.app.game.RunResult
import kotlin.math.roundToInt

/**
 * Score card laid over the frozen wreck. Stats and buttons wrap to whatever
 * width the phone gives them and the card scrolls if a large font pushes it
 * past the screen edge, so the buttons are always reachable.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ResultsOverlay(
    result: RunResult,
    onNextLevel: () -> Unit,
    onRetry: () -> Unit,
    onGarage: () -> Unit,
) {
    val next = LevelCatalog.build(result.level + 1)
    val carnage = result.damagePercent >= 60 || result.wheelsLost > 0
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.30f))
            // Swallow drags so the steering layer underneath never sees them.
            .pointerInput(Unit) {}
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .background(Color(0xE6101014), RoundedCornerShape(22.dp))
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                if (carnage) "CARNAGE" else "SURVIVED",
                color = if (carnage) Color(0xFFFF5252) else Color(0xFF69F0AE),
                fontSize = 30.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center,
            )
            Text("${result.vehicleName} · level ${result.level}", color = Color(0xFFB0B0B8), textAlign = TextAlign.Center)
            Spacer(Modifier.height(4.dp))
            Text("${result.score}", color = Color.White, fontSize = 42.sp, fontWeight = FontWeight.Black)
            Text("points", color = Color(0xFFB0B0B8))
            Spacer(Modifier.height(10.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(22.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Stat("Damage", "${result.damagePercent}%")
                Stat("Wheels lost", "${result.wheelsLost}")
                Stat("Air time", "%.1f s".format(result.longestAirSeconds))
                Stat("Top speed", "${result.maxSpeedMph.roundToInt()} mph")
                Stat("Hardest hit", "${result.strongestImpactMph.roundToInt()} mph")
                Stat("Hit angle", "${result.strongestImpactAngleDeg.roundToInt()}°")
                if (result.rolledOver) Stat("Rolled", "yes")
            }
            Spacer(Modifier.height(12.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Button(
                    onClick = onNextLevel,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32), contentColor = Color.White),
                ) {
                    Text("NEXT: ${next.targetSpeedMph.roundToInt()} mph · ${next.courseName}", fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF37474F), contentColor = Color.White),
                ) {
                    Text("RETRY", fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = onGarage) { Text("GARAGE", color = Color(0xFFB0B0B8), fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(min = 72.dp)) {
        Text(value, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(label, color = Color(0xFFB0B0B8), style = MaterialTheme.typography.bodySmall)
    }
}
