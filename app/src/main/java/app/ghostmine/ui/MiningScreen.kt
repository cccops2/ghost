package app.ghostmine.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ghostmine.core.Mode
import app.ghostmine.core.formatBtcPlain
import app.ghostmine.core.formatDuration
import app.ghostmine.core.formatHashrate
import app.ghostmine.core.formatTemp
import app.ghostmine.data.Repo
import app.ghostmine.hardware.HardwareMonitor
import app.ghostmine.miner.MinerController
import app.ghostmine.stratum.PoolState
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun MiningScreen(onPool: () -> Unit) {
    val ctx = LocalContext.current
    val st by MinerController.state.collectAsState()
    val info = remember { HardwareMonitor.info(ctx) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var pendingPerf by remember { mutableStateOf(false) }
    LaunchedEffect(st.running) { while (st.running) { now = System.currentTimeMillis(); delay(1000) } }

    val modes = listOf(Mode.ECO, Mode.BALANCED, Mode.PERFORMANCE, Mode.CUSTOM)
    val cpuLoad = if (st.running) (st.effectiveLimit * st.threads / info.cores.coerceAtLeast(1)) else 0
    val custom = Repo.custom

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 12.dp)) {
        Heading("Mining")
        Spacer(Modifier.height(20.dp))
        Text(if (st.running) formatHashrate(st.hashrate) else "0.00 H/s", color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.SemiBold)
        Label("Hashrate")
        Spacer(Modifier.height(12.dp))
        LineChart(st.history)
        Spacer(Modifier.height(16.dp))
        GCard(Modifier.fillMaxWidth()) {
            MetricGrid(
                listOf(
                    "CPU load (est.)" to "$cpuLoad%",
                    "Temperature" to formatTemp(st.tempC),
                    "Threads" to "${st.threads}",
                    "Uptime" to formatDuration(if (st.running) (now - st.startedAt) / 1000 else 0),
                    "Shares accepted" to "${st.accepted}",
                    "Shares rejected" to "${st.rejected}",
                    "Pool status" to st.pool.label(),
                    "Est. BTC" to formatBtcPlain(st.estBtc),
                )
            )
        }
        if (st.throttled) {
            Spacer(Modifier.height(10.dp))
            Label("Intensity reduced to keep the device cool.", color = G.Warn)
        }
        if (st.pool == PoolState.ERROR && st.poolMessage.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Label(st.poolMessage, color = G.Warn)
        }
        Spacer(Modifier.height(20.dp))
        Label("Intensity")
        Spacer(Modifier.height(8.dp))
        Segmented(listOf("Eco", "Balanced", "Performance", "Custom"), modes.indexOf(Repo.mode), { i ->
            val m = modes[i]
            if (m == Mode.PERFORMANCE && Repo.mode != Mode.PERFORMANCE) pendingPerf = true
            else { Repo.setMode(m); MinerController.reconfigure() }
        })
        Spacer(Modifier.height(8.dp))
        Label(
            when (Repo.mode) {
                Mode.ECO -> "Lowest heat and power use."
                Mode.BALANCED -> "Balance between speed and consumption."
                Mode.PERFORMANCE -> "Uses more resources, within safe thermal limits."
                Mode.CUSTOM -> "Set threads, CPU limit and temperature yourself."
            }
        )
        if (Repo.mode == Mode.CUSTOM) {
            Spacer(Modifier.height(12.dp))
            GCard(Modifier.fillMaxWidth()) {
                val maxT = maxOf(info.cores, 2)
                SliderRow("Threads", "${custom.threads}", custom.threads.toFloat(), 1f..maxT.toFloat(), (maxT - 2).coerceAtLeast(0)) {
                    Repo.setCustom(custom.copy(threads = it.roundToInt()))
                }
                SliderRow("CPU limit", "${custom.cpuLimit}%", custom.cpuLimit.toFloat(), 10f..100f, 17) {
                    Repo.setCustom(custom.copy(cpuLimit = it.roundToInt()))
                }
                SliderRow("Max temperature", "${custom.maxTemp} °C", custom.maxTemp.toFloat(), 35f..45f, 9) {
                    Repo.setCustom(custom.copy(maxTemp = it.roundToInt()))
                }
                SwitchRow("Only while charging", null, custom.onlyCharging) {
                    Repo.setCustom(custom.copy(onlyCharging = it)); MinerController.reconfigure()
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        GCard(Modifier.fillMaxWidth(), onClick = onPool) {
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Label("Mining Pool")
                    Spacer(Modifier.height(4.dp))
                    Body(Repo.activePool()?.let { it.name.ifBlank { it.url } } ?: "Not configured")
                }
                Body("Manage", color = G.Accent, size = 13)
            }
        }
        Spacer(Modifier.height(16.dp))
        Label("Reality check: a phone mines at kH/s to MH/s, the Bitcoin network at hundreds of EH/s. Expect ~0 BTC.")
        Spacer(Modifier.height(24.dp))
    }

    if (pendingPerf) {
        AlertDialog(
            onDismissRequest = { pendingPerf = false },
            containerColor = G.Card, titleContentColor = Color.White, textContentColor = G.Dim,
            title = { Text("Performance mode") },
            text = { Text("This uses most CPU cores and drains the battery faster. Ghost Mine still throttles automatically when the phone gets hot.") },
            confirmButton = {
                TextButton(onClick = { pendingPerf = false; Repo.setMode(Mode.PERFORMANCE); MinerController.reconfigure() }) {
                    Text("Enable", color = G.Accent)
                }
            },
            dismissButton = { TextButton(onClick = { pendingPerf = false }) { Text("Cancel", color = G.Dim) } },
        )
    }
}

@Composable
private fun SliderRow(
    label: String, value: String, v: Float, range: ClosedFloatingPointRange<Float>, steps: Int, onChange: (Float) -> Unit,
) {
    Row(Modifier.fillMaxWidth()) {
        Label(label)
        Spacer(Modifier.weight(1f))
        Body(value, size = 13, weight = FontWeight.SemiBold)
    }
    Slider(
        value = v, onValueChange = onChange, valueRange = range, steps = steps,
        onValueChangeFinished = { MinerController.reconfigure() },
        colors = SliderDefaults.colors(thumbColor = G.Accent, activeTrackColor = G.Accent, inactiveTrackColor = G.Line),
    )
}
