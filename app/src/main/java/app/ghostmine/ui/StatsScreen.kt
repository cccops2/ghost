package app.ghostmine.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.ghostmine.core.formatBtc
import app.ghostmine.core.formatDuration
import app.ghostmine.core.formatHashrate
import app.ghostmine.data.Repo

@Composable
fun StatsScreen(onHistory: () -> Unit) {
    var range by remember { mutableIntStateOf(0) }
    val cutoff = when (range) {
        0 -> System.currentTimeMillis() - 24L * 3600_000
        1 -> System.currentTimeMillis() - 7L * 24 * 3600_000
        2 -> System.currentTimeMillis() - 30L * 24 * 3600_000
        else -> 0L
    }
    val sessions = Repo.sessions.filter { it.end >= cutoff }
    val samples = Repo.samples.filter { it.ts >= cutoff }
    val totalSec = sessions.sumOf { (it.end - it.start) / 1000 }
    val shares = sessions.sumOf { it.accepted }
    val btc = sessions.sumOf { it.estBtc }
    var acc = 0.0
    val cumulative = sessions.map { acc += it.estBtc; acc }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Heading("Statistics")
            Spacer(Modifier.weight(1f))
            Body("History", color = G.Accent, size = 14, modifier = Modifier.clickable { onHistory() }.padding(8.dp))
        }
        Spacer(Modifier.height(16.dp))
        Segmented(listOf("24H", "7D", "30D", "ALL"), range, { range = it })
        Spacer(Modifier.height(16.dp))
        StatBlock("Hashrate", if (samples.isEmpty()) "—" else formatHashrate(samples.map { it.hashrate }.average())) {
            LineChart(samples.map { it.hashrate })
        }
        Spacer(Modifier.height(12.dp))
        StatBlock("Mining Time", formatDuration(totalSec)) { BarChart(sessions.map { ((it.end - it.start) / 1000).toDouble() }) }
        Spacer(Modifier.height(12.dp))
        StatBlock("Shares", "$shares accepted") { BarChart(sessions.map { it.accepted.toDouble() }) }
        Spacer(Modifier.height(12.dp))
        StatBlock("Estimated BTC", formatBtc(btc)) { LineChart(cumulative) }
        Spacer(Modifier.height(12.dp))
        Label("Estimates are computed from real hash counts and network difficulty. They are not payouts.")
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun StatBlock(title: String, value: String, chart: @Composable () -> Unit) {
    GCard(Modifier.fillMaxWidth()) {
        Label(title)
        Spacer(Modifier.height(4.dp))
        Body(value, size = 20, weight = androidx.compose.ui.text.font.FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        chart()
    }
}
