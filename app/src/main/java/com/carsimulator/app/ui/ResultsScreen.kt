package com.carsimulator.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carsimulator.app.game.LevelCatalog
import com.carsimulator.app.game.RunResult
import kotlin.math.roundToInt

@Composable
fun ResultsScreen(
    result: RunResult,
    onNextLevel: () -> Unit,
    onRetry: () -> Unit,
    onGarage: () -> Unit,
) {
    val next = LevelCatalog.build(result.level + 1)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0E0E10))
            .safeDrawingPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            if (result.damagePercent >= 60 || result.wheelsLost > 0) "CARNAGE" else "SURVIVED",
            color = if (result.damagePercent >= 60) Color(0xFFFF5252) else Color(0xFF69F0AE),
            fontSize = 40.sp, fontWeight = FontWeight.Black,
        )
        Text("${result.vehicleName} · level ${result.level}", color = Color.Gray)
        Spacer(Modifier.height(16.dp))
        Text("${result.score}", color = Color.White, fontSize = 56.sp, fontWeight = FontWeight.Black)
        Text("points", color = Color.Gray)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            Stat("Damage", "${result.damagePercent}%")
            Stat("Wheels lost", "${result.wheelsLost}")
            Stat("Air time", "%.1f s".format(result.longestAirSeconds))
            Stat("Top speed", "${result.maxSpeedMph.roundToInt()} mph")
            Stat("Hardest hit", "${result.strongestImpactMph.roundToInt()} mph")
            if (result.rolledOver) Stat("Rolled", "yes")
        }
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = onNextLevel,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
            ) {
                Text("NEXT: ${next.targetSpeedMph.roundToInt()} mph · ${next.courseName}", fontWeight = FontWeight.Bold)
            }
            Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF37474F))) {
                Text("RETRY")
            }
            TextButton(onClick = onGarage) { Text("GARAGE", color = Color.Gray) }
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(96.dp)) {
        Text(value, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(label, color = Color.Gray, style = MaterialTheme.typography.bodySmall)
    }
}
