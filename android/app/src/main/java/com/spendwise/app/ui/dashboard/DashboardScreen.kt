package com.spendwise.app.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spendwise.app.core.Money
import com.spendwise.app.ui.common.ErrorView
import com.spendwise.app.ui.common.LoadingView
import com.spendwise.app.ui.common.MessageView
import com.spendwise.app.ui.theme.categoryColor

@Composable
fun DashboardScreen(vm: DashboardViewModel = hiltViewModel()) {
    val s by vm.uiState.collectAsStateWithLifecycle()
    when {
        s.loading -> LoadingView()
        s.error != null -> ErrorView(s.error!!)
        s.isEmpty -> MessageView(Icons.Default.PieChart, "Nothing to chart yet", "Add a few expenses and your summary will appear here.")
        else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(s.monthLabel, style = MaterialTheme.typography.labelLarge)
                        Text(Money.format(s.monthTotalMinor), style = MaterialTheme.typography.displaySmall)
                        Text("spent this month", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (s.categories.isNotEmpty()) item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("By category", style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth())
                        DonutChart(s.categories)
                        s.categories.forEach { c ->
                            val pct = (c.total * 100 / s.monthTotalMinor).toInt()
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(12.dp).clip(CircleShape).background(categoryColor(c.category)))
                                Text("${c.category.label}  ·  $pct%", Modifier.weight(1f).padding(start = 8.dp))
                                Text(Money.format(c.total))
                            }
                        }
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Last 6 months", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
                        TrendChart(s.trend)
                    }
                }
            }
            if (s.topMerchants.isNotEmpty()) item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Top merchants", style = MaterialTheme.typography.titleMedium)
                        s.topMerchants.forEachIndexed { i, m ->
                            Row(Modifier.fillMaxWidth()) {
                                Text("${i + 1}. ${m.name}", Modifier.weight(1f))
                                Text(Money.format(m.total))
                            }
                        }
                    }
                }
            }
        }
    }
}
