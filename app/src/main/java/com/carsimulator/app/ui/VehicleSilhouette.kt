package com.carsimulator.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import com.carsimulator.app.sim.BodyStyle
import com.carsimulator.app.sim.VehicleSpec

/** Side-on 2D silhouette in the spec's colour and proportions, for the garage cards. */
@Composable
fun VehicleSilhouette(spec: VehicleSpec, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val scale = size.width * 0.92f / spec.lengthM.toFloat()
        val groundY = size.height * 0.88f
        val x0 = size.width * 0.04f
        fun px(m: Double) = (m * scale).toFloat()
        val body = Color(0xFF000000.toInt() or spec.colorRgb)
        val dark = Color(0xFF222222)
        val glass = Color(0xFF1E2A3A)

        val clearance = px(spec.groundClearanceM)
        val bodyTop = groundY - px(spec.heightM)
        val beltFrac = when (spec.bodyStyle) {
            BodyStyle.PICKUP -> 0.52f
            BodyStyle.SUV, BodyStyle.OFFROAD -> 0.50f
            BodyStyle.SPORTS -> 0.58f
            BodyStyle.MUSCLE, BodyStyle.SEDAN -> 0.55f
        }
        val bodyBottom = groundY - clearance
        val belt = bodyBottom - (bodyBottom - bodyTop) * beltFrac
        val len = px(spec.lengthM)

        // Lower body.
        drawRect(body, Offset(x0, belt), Size(len, bodyBottom - belt))
        // Upper structure by style (front is on the right).
        val (cab0, cab1) = when (spec.bodyStyle) {
            BodyStyle.PICKUP -> 0.34f to 0.64f
            BodyStyle.SUV -> 0.02f to 0.72f
            BodyStyle.OFFROAD -> 0.02f to 0.68f
            BodyStyle.SPORTS -> 0.30f to 0.62f
            BodyStyle.MUSCLE, BodyStyle.SEDAN -> 0.24f to 0.64f
        }
        drawRect(body, Offset(x0 + len * cab0, bodyTop), Size(len * (cab1 - cab0), belt - bodyTop))
        drawRect(glass, Offset(x0 + len * cab0 + 4f, bodyTop + 4f), Size(len * (cab1 - cab0) - 8f, (belt - bodyTop) * 0.6f))
        if (spec.bodyStyle == BodyStyle.PICKUP) {
            drawRect(body, Offset(x0, belt - (belt - bodyTop) * 0.25f), Size(len * cab0, (belt - bodyTop) * 0.25f))
        }
        // Wheels.
        val r = px(spec.wheelRadiusM)
        val rearX = x0 + px(spec.rearOverhang)
        val frontX = x0 + len - px(spec.frontOverhang)
        for (cx in listOf(rearX, frontX)) {
            drawCircle(dark, r, Offset(cx, groundY - r))
            drawCircle(Color(0xFFB0B0B0), r * 0.45f, Offset(cx, groundY - r))
        }
    }
}
