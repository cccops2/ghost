package app.ghostmine.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ghostmine.btc.BitcoinAddress
import app.ghostmine.core.formatBtcPlain
import app.ghostmine.core.shortAddress
import app.ghostmine.data.Repo
import kotlinx.coroutines.delay

@Composable
fun WalletScreen() {
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var stage by remember { mutableStateOf(1) }
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val estTotal = Repo.sessions.sumOf { it.estBtc }
    val pool = Repo.activePool()

    LaunchedEffect(copied) { if (copied) { delay(1500); copied = false } }

    fun openEdit() { input = Repo.address; stage = 1; error = null; editing = true }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 12.dp)) {
        Label("Your Bitcoin")
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = androidx.compose.ui.Alignment.Bottom) {
            Text(formatBtcPlain(estTotal), color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text("  BTC", color = G.Dim, fontSize = 16.sp, modifier = Modifier.padding(bottom = 6.dp))
        }
        Label("Estimated from mining sessions. Balance and payouts are held by your pool, not this app.")
        Spacer(Modifier.height(24.dp))

        GCard(Modifier.fillMaxWidth()) {
            Label("Payout address")
            Spacer(Modifier.height(6.dp))
            Body(shortAddress(Repo.address), size = 20, weight = FontWeight.SemiBold, family = FontFamily.Monospace)
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GButton(if (copied) "Copied ✓" else "Copy", {
                    if (Repo.address.isNotBlank()) { clipboard.setText(AnnotatedString(Repo.address)); copied = true }
                }, Modifier.weight(1f), filled = false)
                GButton(if (Repo.address.isBlank()) "Add" else "Edit", { openEdit() }, Modifier.weight(1f))
            }
        }

        Spacer(Modifier.height(16.dp))
        GCard(Modifier.fillMaxWidth()) {
            MetricGrid(
                listOf(
                    "BTC Address" to (if (Repo.address.isBlank()) "Not set" else shortAddress(Repo.address)),
                    "Pending BTC" to "Not reported by pool",
                    "Total BTC Received" to "Not reported by pool",
                    "Next Estimated Payout" to "—",
                    "Minimum Payout" to (pool?.minPayout?.takeIf { it.isNotBlank() }?.let { "$it BTC" } ?: "—"),
                )
            )
        }
        Spacer(Modifier.height(16.dp))
        GCard(Modifier.fillMaxWidth()) {
            Label("Payment History")
            Spacer(Modifier.height(6.dp))
            Body("No payments recorded", color = G.Dim, size = 14)
        }
        Spacer(Modifier.height(16.dp))
        Label("Ghost Mine is non-custodial. It never asks for a seed phrase, private key or wallet password, and it never holds your funds.")
        Spacer(Modifier.height(24.dp))
    }

    if (editing) {
        if (stage == 1) {
            AlertDialog(
                onDismissRequest = { editing = false },
                containerColor = G.Card, titleContentColor = Color.White, textContentColor = G.Dim,
                title = { Text("Bitcoin address") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Enter a public mainnet address only (bc1…, 1… or 3…).")
                        GField(input, { input = it; error = null }, "Address")
                        error?.let { Text(it, color = G.Warn, fontSize = 12.sp) }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val t = input.trim()
                        error = when {
                            t.split(Regex("\\s+")).size >= 6 -> "That looks like a seed phrase. Never share it. Enter a public address only."
                            !BitcoinAddress.isValid(t) -> "Invalid Bitcoin address (mainnet only)."
                            else -> null
                        }
                        if (error == null) { input = t; stage = 2 }
                    }) { Text("Continue", color = G.Accent) }
                },
                dismissButton = { TextButton(onClick = { editing = false }) { Text("Cancel", color = G.Dim) } },
            )
        } else {
            AlertDialog(
                onDismissRequest = { editing = false },
                containerColor = G.Card, titleContentColor = Color.White, textContentColor = G.Dim,
                title = { Text("Confirm your Bitcoin address") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(input, color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 14.sp)
                        Text("Check every character. Mined coins are paid to this address and payments cannot be reversed.")
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        Repo.setAddress(input); editing = false
                        Toast.makeText(ctx, "Address saved", Toast.LENGTH_SHORT).show()
                    }) { Text("Confirm", color = G.Accent) }
                },
                dismissButton = { TextButton(onClick = { stage = 1 }) { Text("Back", color = G.Dim) } },
            )
        }
    }
}
