package com.wineledger.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wineledger.app.AppViewModel
import com.wineledger.app.data.AppData
import com.wineledger.app.logic.GrapeColor
import com.wineledger.app.logic.GrapeDb
import com.wineledger.app.logic.GrapeDef
import com.wineledger.app.logic.GrapeShare
import com.wineledger.app.logic.fmtNumber
import com.wineledger.app.logic.formatGrapeShares
import com.wineledger.app.logic.grapeTotal
import com.wineledger.app.logic.grapesOf
import com.wineledger.app.logic.norm
import com.wineledger.app.logic.parseGrapeShares
import com.wineledger.app.logic.tidyGrapeName

/**
 * Grapes of a wine: pick them from the database, give each an optional percentage, or add a
 * grape the database does not have. [value] and [onChange] use the text form of
 * [formatGrapeShares], so the rest of the app keeps working with a plain string.
 */
@Composable
fun GrapesEditor(
    value: String,
    onChange: (String) -> Unit,
    data: AppData,
    vm: AppViewModel,
    modifier: Modifier = Modifier,
) {
    // the rows are the source of truth while typing; they are rebuilt when the form sets another text
    var rows by remember { mutableStateOf(parseGrapeShares(value)) }
    LaunchedEffect(value) {
        if (formatGrapeShares(rows) != value) rows = parseGrapeShares(value)
    }
    var picking by remember { mutableStateOf(false) }

    fun update(list: List<GrapeShare>) {
        rows = list
        onChange(formatGrapeShares(list))
    }

    val options = remember(data.wines, data.customGrapes) {
        GrapeDb.options(data.customGrapes, data.wines.values.flatMap { grapesOf(it.grapes) })
    }
    val total = grapeTotal(rows)

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Grapes", style = MaterialTheme.typography.labelLarge)
        if (rows.isEmpty()) {
            Text(
                "No grape yet. A percentage is optional for each one.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        rows.forEachIndexed { i, g ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    g.name,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                OutlinedTextField(
                    value = g.pct,
                    onValueChange = { v ->
                        val clean = v.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(5)
                        update(rows.toMutableList().also { it[i] = g.copy(pct = clean) })
                    },
                    placeholder = { Text("–") },
                    suffix = { Text("%") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.width(112.dp),
                    shape = RoundedCornerShape(12.dp),
                )
                IconButton(
                    onClick = { update(rows.filterIndexed { j, _ -> j != i }) },
                    modifier = Modifier.size(MinTouch),
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Remove ${g.name}")
                }
            }
        }
        if (total != null) {
            val over = total > 100.0
            Text(
                if (over) "Total ${fmtNumber(total)}%: more than 100" else "Total ${fmtNumber(total)}%",
                style = MaterialTheme.typography.bodySmall,
                color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        GhostButton(if (rows.isEmpty()) "Add a grape" else "Add another grape", onClick = { picking = true })
    }

    if (picking) {
        GrapePicker(
            options = options,
            taken = rows.map { it.name },
            onPick = { name -> update(rows + GrapeShare(name)) },
            onCreate = { raw ->
                val name = tidyGrapeName(raw)
                vm.addCustomGrape(name)
                update(rows + GrapeShare(name))
            },
            onDone = { picking = false },
        )
    }
}

@Composable
private fun GrapePicker(
    options: List<GrapeDef>,
    taken: List<String>,
    onPick: (String) -> Unit,
    onCreate: (String) -> Unit,
    onDone: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val takenKeys = taken.map { norm(it) }.toSet()
    val shown = GrapeDb.search(options.filter { norm(it.name) !in takenKeys }, query)
    val typed = query.trim()
    // offer to create the grape when nothing in the database (or already chosen) has this name
    val exists = typed.isNotEmpty() && (options.any { it.isNamed(typed) } || norm(typed) in takenKeys)
    val canCreate = typed.isNotEmpty() && !exists

    AlertDialog(
        onDismissRequest = onDone,
        title = { Text("Add grapes") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SearchField(query, { query = it }, "Search or type a new grape")
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 340.dp)) {
                    if (canCreate) {
                        item(key = "create") {
                            Text(
                                "Add “${tidyGrapeName(typed)}” as a new grape",
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = MinTouch)
                                    .clickable { onCreate(typed); query = "" }
                                    .padding(horizontal = 4.dp, vertical = 12.dp),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                    if (query.isBlank() && shown.any { it.popular }) {
                        item(key = "popular") { Eyebrow("Most used", Modifier.padding(top = 4.dp, bottom = 2.dp)) }
                    }
                    items(shown, key = { it.name }) { g ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = MinTouch)
                                .clickable { onPick(g.name); query = "" }
                                .padding(horizontal = 4.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            GrapeDot(g.color)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(g.name, style = MaterialTheme.typography.bodyLarge)
                                if (g.aliases.isNotEmpty()) {
                                    Text(
                                        g.aliases.joinToString(", "),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                    if (shown.isEmpty() && !canCreate) {
                        item(key = "empty") {
                            Text(
                                "Every matching grape is already in the wine.",
                                Modifier.padding(vertical = 12.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { PrimaryButton("Done", onClick = onDone) },
    )
}

@Composable
private fun GrapeDot(color: GrapeColor?) {
    val c = when (color) {
        GrapeColor.RED -> styleColor("Red")
        GrapeColor.WHITE -> styleColor("White")
        null -> MaterialTheme.colorScheme.outline
    }
    Box(Modifier.size(10.dp).clip(CircleShape).background(c))
}
