package app.ghostmine.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import app.ghostmine.stratum.PoolState

@Composable fun Label(t: String, modifier: Modifier = Modifier, color: Color = G.Dim) {
    Text(t, modifier, color = color, fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp)
}

@Composable fun Heading(t: String, modifier: Modifier = Modifier) {
    Text(t, modifier, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
}

@Composable fun Body(
    t: String, modifier: Modifier = Modifier, color: Color = Color.White, size: Int = 15,
    weight: FontWeight = FontWeight.Medium, family: FontFamily? = null, align: TextAlign? = null,
) {
    Text(t, modifier, color = color, fontSize = size.sp, fontWeight = weight, fontFamily = family, textAlign = align)
}

fun PoolState.label(): String = when (this) {
    PoolState.IDLE -> "Not connected"
    PoolState.CONNECTING -> "Connecting…"
    PoolState.CONNECTED -> "Connected"
    PoolState.ERROR -> "Error"
}

private val ghostPath = Path().apply {
    fillType = PathFillType.EvenOdd
    moveTo(10f, 50f)
    arcTo(Rect(10f, 10f, 90f, 90f), 180f, 180f, false)
    lineTo(90f, 92f); lineTo(76.67f, 80f); lineTo(63.33f, 92f); lineTo(50f, 80f)
    lineTo(36.67f, 92f); lineTo(23.33f, 80f); lineTo(10f, 92f)
    close()
    addOval(Rect(32f, 42f, 44f, 54f))
    addOval(Rect(56f, 42f, 68f, 54f))
}

@Composable
fun GhostLogo(size: Dp, active: Boolean = false, modifier: Modifier = Modifier, tint: Color = G.Accent) {
    val inf = rememberInfiniteTransition(label = "ghost")
    val f by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "f")
    val glow by inf.animateFloat(0.15f, 0.4f, infiniteRepeatable(tween(2000), RepeatMode.Reverse), label = "g")
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension / 100f
        val dy = if (active) (f - 0.5f) * 5f * s else 0f
        if (active) drawCircle(tint.copy(alpha = glow * 0.3f), radius = 46f * s, center = Offset(50f * s, 50f * s + dy))
        translate(top = dy) { scale(s, Offset.Zero) { drawPath(ghostPath, tint) } }
    }
}

@Composable
fun PulseDot(active: Boolean) {
    val t = rememberInfiniteTransition(label = "dot")
    val a by t.animateFloat(0.35f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "a")
    Box(
        Modifier.size(8.dp).clip(CircleShape)
            .background(if (active) G.Accent.copy(alpha = a) else G.Dim.copy(alpha = 0.5f))
    )
}

@Composable
fun GCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(20.dp)).background(G.Card)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(16.dp),
        content = content,
    )
}

@Composable
fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    GCard(modifier) {
        Label(label)
        Spacer(Modifier.height(6.dp))
        Text(value, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
fun MetricGrid(items: List<Pair<String, String>>) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        items.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { (l, v) ->
                    Column(Modifier.weight(1f)) {
                        Label(l)
                        Spacer(Modifier.height(4.dp))
                        Body(v, size = 16, weight = FontWeight.SemiBold)
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun GButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, filled: Boolean = true) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val sc by animateFloatAsState(if (pressed) 0.97f else 1f, tween(90), label = "press")
    Box(
        modifier.height(56.dp).graphicsLayer { scaleX = sc; scaleY = sc }
            .clip(RoundedCornerShape(18.dp))
            .background(if (filled) G.Accent else G.CardHi)
            .clickable(interactionSource = src, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (filled) G.OnAccent else Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, letterSpacing = 1.sp)
    }
}

@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(G.Card).padding(4.dp)) {
        options.forEachIndexed { i, o ->
            val sel = i == selected
            val bg by animateColorAsState(if (sel) G.CardHi else Color.Transparent, tween(150), label = "seg")
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(bg)
                    .clickable { onSelect(i) }.padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(o, color = if (sel) G.Accent else G.Dim, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
            }
        }
    }
}

@Composable
fun LineChart(values: List<Double>, modifier: Modifier = Modifier.fillMaxWidth().height(110.dp)) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        if (values.size < 2) {
            drawLine(G.Line, Offset(0f, h / 2), Offset(w, h / 2), 2f)
            return@Canvas
        }
        val mx = values.max()
        val mn = values.min()
        val range = if (mx - mn > 1e-12) mx - mn else 1.0
        fun px(i: Int) = i.toFloat() / (values.size - 1) * w
        fun py(v: Double) = h - (((v - mn) / range) * 0.8 + 0.1).toFloat() * h
        val line = Path()
        values.forEachIndexed { i, v -> if (i == 0) line.moveTo(px(i), py(v)) else line.lineTo(px(i), py(v)) }
        val fill = Path().apply { addPath(line); lineTo(w, h); lineTo(0f, h); close() }
        drawPath(fill, Brush.verticalGradient(listOf(G.Accent.copy(alpha = 0.16f), Color.Transparent)))
        drawPath(line, G.Accent, style = Stroke(3f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
fun BarChart(values: List<Double>, modifier: Modifier = Modifier.fillMaxWidth().height(80.dp)) {
    Canvas(modifier) {
        if (values.isEmpty()) {
            drawLine(G.Line, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 2f)
            return@Canvas
        }
        val mx = values.max().coerceAtLeast(1e-12)
        val n = values.size
        val gap = 4f
        val bw = ((size.width - gap * (n - 1)) / n).coerceAtLeast(2f)
        values.forEachIndexed { i, v ->
            val bh = (v / mx).toFloat() * size.height * 0.9f + 2f
            drawRoundRect(
                G.Accent.copy(alpha = 0.85f), Offset(i * (bw + gap), size.height - bh), Size(bw, bh), CornerRadius(3f, 3f)
            )
        }
    }
}

@Composable
fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Body(title)
            if (subtitle != null) Label(subtitle)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked, onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = G.Accent, checkedThumbColor = G.OnAccent,
                uncheckedTrackColor = G.CardHi, uncheckedThumbColor = G.Dim, uncheckedBorderColor = G.Line,
            ),
        )
    }
}

@Composable
fun GField(
    value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier,
    password: Boolean = false, keyboard: KeyboardType = KeyboardType.Text, singleLine: Boolean = true,
) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = singleLine,
        modifier = modifier.fillMaxWidth(),
        visualTransformation = if (password) androidx.compose.ui.text.input.PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboard),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = G.Accent, unfocusedBorderColor = G.Line, cursorColor = G.Accent,
            focusedLabelColor = G.Accent, unfocusedLabelColor = G.Dim,
            focusedTextColor = Color.White, unfocusedTextColor = Color.White,
        ),
    )
}

@Composable
fun OverlayPage(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.IconButton(onClick = onBack) {
                androidx.compose.material3.Icon(
                    androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White
                )
            }
            Heading(title)
        }
        Column(
            Modifier.weight(1f).fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content,
        )
    }
}
