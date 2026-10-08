package com.spendwise.app.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.columnSeries
import com.spendwise.app.core.Money
import com.spendwise.app.data.local.CategoryTotal
import com.spendwise.app.data.repo.MonthTotal
import com.spendwise.app.ui.theme.categoryColor
import java.time.format.TextStyle
import java.util.Locale

/** Vico has no pie/donut, so this one is plain Canvas: ~20 lines and fully under our control. */
@Composable
fun DonutChart(slices: List<CategoryTotal>, modifier: Modifier = Modifier) {
    val total = slices.sumOf { it.total }.toFloat()
    val description = slices.joinToString { "${it.category.label} ${Money.format(it.total)}" }
    Box(modifier.size(180.dp).semantics { contentDescription = "Spending by category: $description" }, Alignment.Center) {
        Canvas(Modifier.size(180.dp)) {
            val stroke = 32.dp.toPx()
            var start = -90f
            slices.forEach {
                val sweep = it.total / total * 360f
                drawArc(
                    color = categoryColor(it.category),
                    startAngle = start,
                    sweepAngle = (sweep - 1.5f).coerceAtLeast(0.5f), // small gap between slices
                    useCenter = false,
                    topLeft = Offset(stroke / 2, stroke / 2),
                    size = Size(size.width - stroke, size.height - stroke),
                    style = Stroke(stroke),
                )
                start += sweep
            }
        }
        Text(Money.format(slices.sumOf { it.total }), style = MaterialTheme.typography.titleMedium)
    }
}

/**
 * Bars come from Vico (Compose-native, maintained, themeable). Money is converted to Double ONLY here,
 * for drawing; it never flows back into any calculation.
 */
@Composable
fun TrendChart(data: List<MonthTotal>, modifier: Modifier = Modifier) {
    val producer = remember { CartesianChartModelProducer() }
    LaunchedEffect(data) {
        producer.runTransaction { columnSeries { series(data.map { it.totalMinor / 100.0 }) } }
    }
    val labels = data.map { it.month.month.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberColumnCartesianLayer(),
            startAxis = VerticalAxis.rememberStart(),
            bottomAxis = HorizontalAxis.rememberBottom(valueFormatter = { _, x, _ -> labels.getOrElse(x.toInt()) { "" } }),
        ),
        modelProducer = producer,
        modifier = modifier.height(200.dp),
    )
}
