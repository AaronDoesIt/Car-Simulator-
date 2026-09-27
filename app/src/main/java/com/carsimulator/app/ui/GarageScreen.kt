package com.carsimulator.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carsimulator.app.game.LevelCatalog
import com.carsimulator.app.sim.VehicleCatalog
import com.carsimulator.app.sim.VehicleSpec
import kotlin.math.roundToInt

@Composable
fun GarageScreen(
    level: Int,
    selected: VehicleSpec,
    bestScore: Int,
    onSelect: (VehicleSpec) -> Unit,
    onStart: () -> Unit,
    onResetProgress: () -> Unit,
) {
    val preview = LevelCatalog.build(level)
    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0E0E10))
            .safeDrawingPadding()
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(
            modifier = Modifier
                .width(260.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.Center,
        ) {
            Text("CAR SIMULATOR", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black)
            Text("Speed strip carnage", color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(20.dp))
            Text("LEVEL $level", color = Color(0xFFFFA000), fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("${preview.targetSpeedMph.roundToInt()} mph into the ${preview.courseName.lowercase()}", color = Color.White)
            Spacer(Modifier.height(6.dp))
            Text("Best score: $bestScore", color = Color.Gray)
            Spacer(Modifier.height(20.dp))
            Text(selected.displayName, color = Color.White, fontWeight = FontWeight.Bold)
            Text(
                "${(selected.massKg * 2.2046).roundToInt()} lb · ${"%.1f".format(selected.wheelbaseM * 39.37)} in wheelbase",
                color = Color.Gray, style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onStart,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
            ) {
                Text("START RUN", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            TextButton(onClick = onResetProgress) { Text("Reset to level 1", color = Color.Gray) }
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 170.dp),
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(VehicleCatalog.all, key = { it.id }) { spec ->
                VehicleCard(spec, spec.id == selected.id) { onSelect(spec) }
            }
        }
    }
}

@Composable
private fun VehicleCard(spec: VehicleSpec, isSelected: Boolean, onClick: () -> Unit) {
    val border = if (isSelected) Color(0xFFFFA000) else Color(0xFF2A2A2E)
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF1A1A1E))
            .border(2.dp, border, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        VehicleSilhouette(spec, modifier = Modifier.fillMaxWidth().height(70.dp))
        Spacer(Modifier.height(6.dp))
        Text(
            spec.displayName,
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
        )
        Text(
            "${(spec.massKg * 2.2046).roundToInt()} lb",
            color = Color.Gray,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
