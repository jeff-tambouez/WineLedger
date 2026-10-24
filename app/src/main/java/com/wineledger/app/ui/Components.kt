@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.wineledger.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.wineledger.app.data.Profile
import com.wineledger.app.logic.Awards
import com.wineledger.app.logic.Counted
import com.wineledger.app.logic.dateDigitsToIso
import com.wineledger.app.logic.fmtScore
import java.io.File
import androidx.compose.ui.graphics.Color as ComposeColor

val MinTouch = 48.dp

// =====================================================================
// Profile switcher
// =====================================================================

@Composable
fun ProfileDot(profile: Profile, size: Dp = 30.dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(ComposeColor(profile.accent)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            profile.name.trim().take(1).uppercase(),
            color = Color.White,
            fontFamily = Fraunces,
            fontWeight = FontWeight.SemiBold,
            fontSize = (size.value * 0.5f).sp,
        )
    }
}

@Composable
fun ProfileBar(
    profiles: List<Profile>,
    activeId: Long,
    both: Boolean,
    showBoth: Boolean,
    onSelect: (Long) -> Unit,
    onBoth: () -> Unit,
    onSettings: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            profiles.forEach { p ->
                val selected = !both && p.id == activeId
                ProfilePill(selected, onClick = { onSelect(p.id) }) {
                    ProfileDot(p, 28.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(p.name, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                }
            }
            if (showBoth && profiles.size > 1) {
                ProfilePill(both, onClick = onBoth) {
                    Text("Both of us", style = MaterialTheme.typography.labelLarge, maxLines = 1)
                }
            }
        }
        IconButton(onClick = onSettings, modifier = Modifier.size(MinTouch)) {
            Icon(Icons.Filled.Settings, contentDescription = "Settings")
        }
    }
}

@Composable
private fun ProfilePill(selected: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    val border = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Row(
        Modifier
            .heightIn(min = MinTouch)
            .clip(RoundedCornerShape(24.dp))
            .background(bg)
            .border(BorderStroke(if (selected) 1.5.dp else 1.dp, border), RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

// =====================================================================
// Small visual pieces
// =====================================================================

@Composable
fun ScoreMedal(score: Double, size: Dp = 46.dp, modifier: Modifier = Modifier) {
    val borderColor = when {
        score >= 95 -> wl.gold
        score >= 90 -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline
    }
    val width = if (score >= 95) 2.dp else 1.5.dp
    Box(
        modifier.size(size).clip(CircleShape).border(width, borderColor, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            fmtScore(score),
            fontFamily = Fraunces,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.36f).sp,
            color = if (score >= 95) wl.burgundy else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
fun StyleLabel(style: String, modifier: Modifier = Modifier) {
    if (style.isEmpty()) return
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(styleColor(style)))
        Spacer(Modifier.width(6.dp))
        Text(
            style.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 1.sp,
        )
    }
}

@Composable
fun Tag(text: String, color: Color = MaterialTheme.colorScheme.primary, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier
            .border(1.dp, color.copy(alpha = 0.55f), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 2.dp),
        style = MaterialTheme.typography.labelSmall,
        color = color,
        fontSize = 9.sp,
        letterSpacing = 0.8.sp,
        maxLines = 1,
    )
}

@Composable
fun AwardBadge(c: Char) {
    val color = if (c == Awards.POLE) Color(0xFF3F6FB5) else wl.gold
    Row(
        Modifier
            .border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(Awards.glyph(c), color = color, fontSize = 11.sp)
        Spacer(Modifier.width(5.dp))
        Text(
            Awards.name(c).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontSize = 9.sp,
            letterSpacing = 0.6.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier,
        style = MaterialTheme.typography.labelSmall,
        color = wl.gold,
        letterSpacing = 1.6.sp,
    )
}

@Composable
fun EmptyState(title: String, text: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("✦", fontFamily = Fraunces, fontSize = 40.sp, color = wl.gold)
        Spacer(Modifier.height(8.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun KpiTile(value: String, label: String, sub: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(14.dp),
    ) {
        Text(
            value,
            fontFamily = Fraunces,
            fontWeight = FontWeight.SemiBold,
            fontSize = 28.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 1.sp,
        )
        if (sub.isNotEmpty()) {
            Text(
                sub,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun SoftCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) { content() }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = MinTouch),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

@Composable
fun GhostButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = MinTouch),
        shape = RoundedCornerShape(12.dp),
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

@Composable
fun QuietButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, danger: Boolean = false) {
    TextButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = MinTouch),
        contentPadding = PaddingValues(horizontal = 12.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        )
    }
}

// =====================================================================
// Inputs
// =====================================================================

@Composable
fun AppField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboard: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    placeholder: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        singleLine = singleLine,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
    )
}

@Composable
fun SearchField(value: String, onValueChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(Icons.Filled.Close, contentDescription = "Clear")
                }
            }
        },
        singleLine = true,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
    )
}

/** Text field with an inline list of suggestions shown while it has focus. */
@Composable
fun SuggestField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    suggestions: List<String>,
    modifier: Modifier = Modifier,
    onPick: (String) -> Unit = onValueChange,
) {
    var focused by remember { mutableStateOf(false) }
    val shown = if (focused) suggestions.filter { !it.equals(value, ignoreCase = true) }.take(5) else emptyList()
    Column(modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
            shape = RoundedCornerShape(12.dp),
        )
        if (shown.isNotEmpty()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                shown.forEachIndexed { i, s ->
                    Text(
                        s,
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = MinTouch)
                            .clickable { onPick(s) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (i < shown.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

/** Read-only field opening a menu. The first option may stand for "no selection". */
@Composable
fun DropdownField(
    label: String,
    selected: String,
    options: List<String>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    emptyLabel: String = "All",
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedTextField(
            value = selected.ifEmpty { emptyLabel },
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        )
        Box(Modifier.matchParentSize().clickable { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(emptyLabel) }, onClick = { onSelect(""); open = false })
            options.forEach { o ->
                DropdownMenuItem(text = { Text(o) }, onClick = { onSelect(o); open = false })
            }
        }
    }
}

@Composable
fun StyleChips(selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier, styles: List<String> = com.wineledger.app.logic.STYLES) {
    FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        styles.forEach { s ->
            FilterChip(
                selected = selected == s,
                onClick = { onSelect(if (selected == s) "" else s) },
                label = { Text(s) },
                modifier = Modifier.heightIn(min = MinTouch).padding(vertical = 2.dp),
                leadingIcon = {
                    Box(Modifier.size(9.dp).clip(CircleShape).background(styleColor(s)))
                },
            )
        }
    }
}

@Composable
fun ChoiceChips(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { o ->
            FilterChip(
                selected = selected == o,
                onClick = { onSelect(o) },
                label = { Text(o) },
                modifier = Modifier.heightIn(min = 40.dp).padding(vertical = 2.dp),
            )
        }
    }
}

@Composable
fun Stepper(value: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier, min: Int = 1, max: Int = 999) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        StepButton("−", enabled = value > min) { onChange(value - 1) }
        Text(
            value.toString(),
            Modifier.widthIn(min = 56.dp),
            textAlign = TextAlign.Center,
            fontFamily = Fraunces,
            fontWeight = FontWeight.SemiBold,
            fontSize = 24.sp,
        )
        StepButton("+", enabled = value < max) { onChange(value + 1) }
    }
}

@Composable
private fun StepButton(symbol: String, enabled: Boolean, onClick: () -> Unit) {
    val bg = if (enabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
    Box(
        Modifier
            .size(MinTouch + 4.dp)
            .clip(CircleShape)
            .background(bg)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, fontSize = 26.sp, color = MaterialTheme.colorScheme.onSurface, fontFamily = FontFamily.SansSerif)
    }
}

/** Score entry: a number field plus a slider for quick setting. */
@Composable
fun ScoreInput(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, label: String = "Score /100") {
    val number = value.replace(',', '.').toDoubleOrNull()
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = value,
                onValueChange = { onChange(it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(5)) },
                label = { Text(label) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.width(130.dp),
                shape = RoundedCornerShape(12.dp),
                textStyle = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.width(12.dp))
            Slider(
                value = (number ?: 88.0).coerceIn(70.0, 100.0).toFloat(),
                onValueChange = { onChange(fmtScore(Math.round(it).toDouble())) },
                valueRange = 70f..100f,
                modifier = Modifier.weight(1f),
            )
        }
        if (number != null && (number < 0 || number > 100)) {
            Text("Between 0 and 100", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}

fun validScore(text: String): Boolean {
    val n = text.replace(',', '.').toDoubleOrNull() ?: return false
    return n in 0.0..100.0
}

/** Shows the typed digits as DD/MM/YYYY, adding the slashes itself. */
private object DateMask : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text.take(8)
        val shown = StringBuilder()
        digits.forEachIndexed { i, c ->
            if (i == 2 || i == 4) shown.append('/')
            shown.append(c)
        }
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int =
                when {
                    offset <= 2 -> offset
                    offset <= 4 -> offset + 1
                    else -> offset + 2
                }.coerceAtMost(shown.length)

            override fun transformedToOriginal(offset: Int): Int =
                when {
                    offset <= 2 -> offset
                    offset <= 5 -> offset - 1
                    else -> offset - 2
                }.coerceIn(0, digits.length)
        }
        return TransformedText(AnnotatedString(shown.toString()), mapping)
    }
}

/** True when the digits are empty or make a real DD/MM/YYYY date. */
fun validDateDigits(digits: String): Boolean = digits.isEmpty() || dateDigitsToIso(digits) != null

/**
 * Date entry without typing separators: the person types the eight digits (23102026)
 * and the field displays 23/10/2026. [digits] holds only the digits.
 */
@Composable
fun DateField(
    digits: String,
    onDigits: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        OutlinedTextField(
            value = digits,
            onValueChange = { onDigits(it.filter { c -> c in '0'..'9' }.take(8)) },
            label = { Text(label) },
            placeholder = { Text("DD/MM/YYYY") },
            singleLine = true,
            visualTransformation = DateMask,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            isError = !validDateDigits(digits) && digits.length == 8,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        )
        if (digits.length == 8 && !validDateDigits(digits)) {
            Text("Not a real date.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        } else if (digits.isNotEmpty() && digits.length < 8) {
            Text(
                "Type day, month and year: 8 digits.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

// =====================================================================
// Photo
// =====================================================================

/**
 * Square thumbnail. When the photo exists, tapping it opens it full screen;
 * [full] is the large version, [file] is used when it is missing.
 */
@Composable
fun PhotoThumb(file: File?, size: Dp, modifier: Modifier = Modifier, full: File? = null) {
    val exists = file != null && file.exists()
    var open by remember { mutableStateOf(false) }
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .then(if (exists) Modifier.clickable { open = true } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (exists) {
            AsyncImage(
                model = file,
                contentDescription = "Wine photo, tap to enlarge",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().fillMaxHeight(),
            )
        } else {
            Text("🍷", fontSize = (size.value * 0.4f).sp)
        }
    }
    if (open && exists) {
        val big = full?.takeIf { it.exists() } ?: file!!
        PhotoViewer(big, onDismiss = { open = false })
    }
}

/** Full-screen photo on a dark backdrop. Pinch to zoom, drag when zoomed, tap anywhere to close. */
@Composable
fun PhotoViewer(file: File, onDismiss: () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.94f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDismiss() },
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = file,
                contentDescription = "Wine photo",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            offset = if (scale == 1f) Offset.Zero else offset + pan
                        }
                    }
                    .graphicsLayer(
                        scaleX = scale, scaleY = scale,
                        translationX = offset.x, translationY = offset.y,
                    ),
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(8.dp),
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
            }
        }
    }
}

// =====================================================================
// Charts, drawn with plain Compose
// =====================================================================

@Composable
fun BarList(items: List<Counted>, color: Color, modifier: Modifier = Modifier, labelPrefix: (String) -> String = { it }) {
    val max = (items.maxOfOrNull { it.count } ?: 1).coerceAtLeast(1)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { c ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    labelPrefix(c.label),
                    Modifier.width(104.dp),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Box(Modifier.weight(1f).height(16.dp)) {
                    Box(
                        Modifier
                            .fillMaxWidth(c.count.toFloat() / max)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp))
                            .background(color)
                    )
                }
                Text(
                    "${c.count} · ${fmtScore(c.avg)}",
                    Modifier.width(72.dp),
                    textAlign = TextAlign.End,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun Donut(slices: List<Pair<String, Int>>, colors: List<Color>, modifier: Modifier = Modifier, size: Dp = 140.dp) {
    val total = slices.sumOf { it.second }.coerceAtLeast(1)
    val track = MaterialTheme.colorScheme.surfaceContainerHigh
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(size)) {
            val stroke = size.toPx() * 0.2f
            val inset = stroke / 2
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            var start = -90f
            slices.forEachIndexed { i, s ->
                val sweep = 360f * s.second / total
                drawArc(
                    colors[i % colors.size], start, (sweep - 1.5f).coerceAtLeast(0.5f), false,
                    Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Butt),
                )
                start += sweep
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            slices.forEachIndexed { i, s ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(colors[i % colors.size]))
                    Spacer(Modifier.width(8.dp))
                    Text("${s.first} · ${s.second}", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

/** Bars for the number of tastings per year and a line for the average score. */
@Composable
fun YearTrend(points: List<Counted>, modifier: Modifier = Modifier) {
    if (points.isEmpty()) return
    val barColor = wl.gold.copy(alpha = 0.45f)
    val lineColor = wl.burgundy
    val grid = MaterialTheme.colorScheme.outlineVariant
    val maxCount = points.maxOf { it.count }.coerceAtLeast(1)
    val minAvg = (points.minOf { it.avg } - 3).coerceAtMost(88.0).coerceAtLeast(0.0)
    val maxAvg = (points.maxOf { it.avg } + 1).coerceAtMost(100.0).coerceAtLeast(minAvg + 5)
    Column(modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(170.dp)) {
            val w = size.width
            val h = size.height
            val slot = w / points.size
            for (i in 0..3) {
                val y = h * i / 3f
                drawLine(grid, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
            }
            points.forEachIndexed { i, p ->
                val bh = h * p.count / maxCount * 0.85f
                val bw = (slot * 0.5f).coerceAtMost(36.dp.toPx())
                drawRect(barColor, Offset(slot * i + (slot - bw) / 2, h - bh), Size(bw, bh))
            }
            var prev: Offset? = null
            points.forEachIndexed { i, p ->
                val x = slot * i + slot / 2
                val y = (h - h * ((p.avg - minAvg) / (maxAvg - minAvg)).toFloat()).coerceIn(4f, h - 4f)
                val pt = Offset(x, y)
                prev?.let { drawLine(lineColor, it, pt, strokeWidth = 3f, cap = StrokeCap.Round) }
                drawCircle(lineColor, 5.dp.toPx(), pt)
                prev = pt
            }
        }
        Row(Modifier.fillMaxWidth()) {
            points.forEach { p ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(p.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        fmtScore(p.avg),
                        style = MaterialTheme.typography.labelSmall,
                        color = wl.burgundy,
                    )
                }
            }
        }
    }
}

// =====================================================================
// Bottom bar icons, drawn by hand so no icon library is needed
// =====================================================================

enum class TabGlyph { LEDGER, CELLAR, STAR, STATS }

@Composable
fun TabIcon(glyph: TabGlyph, tint: Color) {
    if (glyph == TabGlyph.STAR) {
        Icon(Icons.Filled.Star, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        return
    }
    Canvas(Modifier.size(24.dp)) {
        val s = size.width
        val sw = s * 0.09f
        when (glyph) {
            TabGlyph.LEDGER -> {
                for (i in 0..2) {
                    val y = s * (0.25f + 0.25f * i)
                    drawLine(tint, Offset(s * 0.18f, y), Offset(s * 0.82f, y), strokeWidth = sw, cap = StrokeCap.Round)
                }
            }
            TabGlyph.CELLAR -> {
                drawRoundRect(
                    tint, Offset(s * 0.36f, s * 0.08f), Size(s * 0.28f, s * 0.22f),
                    androidx.compose.ui.geometry.CornerRadius(s * 0.05f),
                )
                drawRoundRect(
                    tint, Offset(s * 0.28f, s * 0.3f), Size(s * 0.44f, s * 0.62f),
                    androidx.compose.ui.geometry.CornerRadius(s * 0.1f),
                )
            }
            TabGlyph.STATS -> {
                val bw = s * 0.18f
                drawRect(tint, Offset(s * 0.18f, s * 0.55f), Size(bw, s * 0.3f))
                drawRect(tint, Offset(s * 0.41f, s * 0.3f), Size(bw, s * 0.55f))
                drawRect(tint, Offset(s * 0.64f, s * 0.45f), Size(bw, s * 0.4f))
            }
            else -> Unit
        }
    }
}
