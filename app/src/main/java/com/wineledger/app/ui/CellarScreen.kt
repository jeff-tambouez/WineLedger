package com.wineledger.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wineledger.app.AppViewModel
import com.wineledger.app.activeProfile
import com.wineledger.app.data.Profile
import com.wineledger.app.data.Taster
import com.wineledger.app.logic.BottleRow
import com.wineledger.app.logic.CellarSort
import com.wineledger.app.logic.Window
import com.wineledger.app.logic.cellarKpi
import com.wineledger.app.logic.cellarRows
import com.wineledger.app.logic.currentYear
import com.wineledger.app.logic.fmtMoney
import com.wineledger.app.logic.formatLabel
import com.wineledger.app.logic.pickTonight
import com.wineledger.app.logic.windowLabel

private val WINDOW_OPTIONS = listOf(
    "" to "All",
    "now" to "Drink now",
    "late" to "Past window",
    "wait" to "Waiting",
)

@Composable
fun CellarScreen(vm: AppViewModel) {
    val data by vm.data.collectAsStateWithLifecycle()
    val activeId by vm.activeId.collectAsStateWithLifecycle()
    val profile = data.activeProfile(activeId)
    val year = currentYear()

    var query by rememberSaveable { mutableStateOf("") }
    var style by rememberSaveable { mutableStateOf("") }
    var windowFilter by rememberSaveable { mutableStateOf("") }
    var sortName by rememberSaveable { mutableStateOf(CellarSort.PRODUCER.name) }
    var showFilters by rememberSaveable { mutableStateOf(false) }
    var tonightId by rememberSaveable { mutableStateOf(0L) }
    var tonightStyle by rememberSaveable { mutableStateOf("") }
    var opening by remember { mutableStateOf<BottleRow?>(null) }
    val sort = CellarSort.entries.firstOrNull { it.name == sortName } ?: CellarSort.PRODUCER

    val myAverages = remember(data.tastings, profile?.id) {
        data.tastings.filter { it.profileId == profile?.id }
            .groupBy { it.wineId }
            .mapValues { (_, l) -> l.map { it.score }.average() }
    }
    val kpi = remember(data.bottles, data.wines, data.openings, year) {
        cellarKpi(data.bottles, data.wines, data.openings, year)
    }
    val allRows = remember(data.bottles, data.wines, myAverages, year) {
        cellarRows(data.bottles, data.wines, myAverages, "", "", "", CellarSort.PRODUCER, year)
    }
    val rows = remember(data.bottles, data.wines, myAverages, query, style, windowFilter, sort, year) {
        cellarRows(data.bottles, data.wines, myAverages, query, style, windowFilter, sort, year)
    }
    val tonight = allRows.firstOrNull { it.bottle.id == tonightId }
    val activeFilters = listOf(style, windowFilter).count { it.isNotEmpty() }

    LazyColumn(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column {
                Eyebrow("The inventory of sleep")
                Text("The cellar", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    KpiTile(
                        kpi.bottles.toString(), "Bottles",
                        "${kpi.labels} label${if (kpi.labels == 1) "" else "s"}",
                        Modifier.weight(1f),
                    )
                    KpiTile(
                        if (kpi.value > 0) fmtMoney(kpi.value) else "–", "Estimated value",
                        if (kpi.value > 0) "${kpi.pricedBottles} priced bottle${if (kpi.pricedBottles == 1) "" else "s"}" else "add prices to see it",
                        Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    KpiTile(
                        kpi.drinkNow.toString(), "Drink now",
                        if (kpi.pastWindow > 0) "${kpi.pastWindow} past the window" else "within the year",
                        Modifier.weight(1f),
                    )
                    KpiTile(
                        kpi.openedLast12.toString(), "Opened in 12 months",
                        kpi.lastOpened?.let { "last: $it" } ?: "none yet",
                        Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(12.dp))
                GhostButton(
                    "What to drink tonight?",
                    onClick = {
                        tonightId = pickTonight(allRows, tonightStyle)?.bottle?.id ?: 0L
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = allRows.isNotEmpty(),
                )
            }
        }

        if (tonight != null) {
            item {
                TonightCard(
                    row = tonight,
                    style = tonightStyle,
                    onStyle = { tonightStyle = it; tonightId = pickTonight(allRows, it)?.bottle?.id ?: 0L },
                    onAnother = { tonightId = pickTonight(allRows, tonightStyle)?.bottle?.id ?: 0L },
                    onOpen = { opening = tonight },
                    onClose = { tonightId = 0L },
                    vm = vm,
                )
            }
        }

        item {
            Column {
                SearchField(query, { query = it }, "Wine, producer, grape, note…")
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    QuietButton(
                        if (showFilters) "Hide filters" else if (activeFilters > 0) "Filters ($activeFilters)" else "Filters",
                        onClick = { showFilters = !showFilters },
                    )
                    if (activeFilters > 0 || query.isNotEmpty()) {
                        QuietButton("Reset", onClick = { query = ""; style = ""; windowFilter = "" })
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        if (data.bottles.isEmpty()) "" else "${rows.sumOf { it.bottle.quantity }} of ${kpi.bottles} bottles",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (showFilters) {
                    Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        StyleChips(style, { style = it })
                        ChoiceChips(
                            WINDOW_OPTIONS.map { it.second },
                            WINDOW_OPTIONS.first { it.first == windowFilter }.second,
                            { label -> windowFilter = WINDOW_OPTIONS.first { it.second == label }.first },
                        )
                        DropdownField(
                            "Sort by", sort.label, CellarSort.entries.map { it.label },
                            { label -> CellarSort.entries.firstOrNull { it.label == label }?.let { sortName = it.name } },
                            emptyLabel = CellarSort.PRODUCER.label,
                        )
                    }
                }
            }
        }

        if (data.bottles.isEmpty()) {
            item {
                EmptyState(
                    "The cellar is still empty",
                    "Tap + to add the first bottle. It takes a few seconds: producer, wine, style.",
                )
            }
        } else if (rows.isEmpty()) {
            item { EmptyState("Nothing matches", "Widen the search or reset the filters.") }
        }

        items(rows, key = { "bottle" + it.bottle.id }) { row ->
            BottleCard(row, vm, onOpen = { opening = row })
        }
    }

    opening?.let { row ->
        OpenBottleDialog(
            row = row,
            profiles = data.profiles,
            activeId = profile?.id ?: -1L,
            onDismiss = { opening = null },
            onConfirm = { tasters ->
                vm.openBottle(row.bottle.id, tasters)
                if (tonightId == row.bottle.id) tonightId = 0L
                opening = null
            },
        )
    }
}

@Composable
private fun TonightCard(
    row: BottleRow,
    style: String,
    onStyle: (String) -> Unit,
    onAnother: () -> Unit,
    onOpen: () -> Unit,
    onClose: () -> Unit,
    vm: AppViewModel,
) {
    SoftCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Eyebrow("Tonight")
            Spacer(Modifier.height(4.dp))
            Text(
                row.wine.name + (row.bottle.vintage?.let { " $it" } ?: ""),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                row.wine.producer,
                style = MaterialTheme.typography.bodyMedium,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                StyleLabel(row.wine.style)
                val w = windowLabel(row.bottle, row.window)
                if (w.isNotEmpty()) {
                    Spacer(Modifier.width(8.dp))
                    WindowTag(row)
                }
                row.score?.let {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "in your ledger: ${com.wineledger.app.logic.fmtScore(it)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            StyleChips(style, onStyle)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 6.dp)) {
                PrimaryButton("Open it", onClick = onOpen)
                GhostButton("Another", onClick = onAnother)
                QuietButton("Close", onClick = onClose)
            }
        }
    }
}

@Composable
private fun WindowTag(row: BottleRow) {
    val label = windowLabel(row.bottle, row.window)
    if (label.isEmpty()) return
    val color: Color = when (row.window) {
        Window.LATE -> wl.burgundy
        Window.NOW -> wl.dessert
        Window.READY -> MaterialTheme.colorScheme.primary
        else -> wl.muted
    }
    Tag(label, color)
}

@Composable
private fun BottleCard(row: BottleRow, vm: AppViewModel, onOpen: () -> Unit) {
    val b = row.bottle
    val photo = b.photo ?: row.wine.photo
    SoftCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (photo != null) {
                    PhotoThumb(vm.photoFile(photo, true), 56.dp)
                    Spacer(Modifier.width(12.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        row.wine.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        row.wine.producer,
                        style = MaterialTheme.typography.bodyMedium,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        StyleLabel(row.wine.style)
                        val meta = buildList {
                            add(b.vintage?.toString() ?: "N.V.")
                            if (b.format != 0.75) add(formatLabel(b.format))
                            b.price?.let { add(fmtMoney(it)) }
                        }.joinToString(" · ")
                        Text(
                            "  ·  $meta",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        WindowTag(row)
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "× ${b.quantity}",
                        fontFamily = Fraunces,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    row.score?.let {
                        Spacer(Modifier.height(4.dp))
                        ScoreMedal(it, 40.dp)
                    }
                }
            }
            if (b.notes.isNotBlank()) {
                Text(
                    b.notes,
                    Modifier.padding(top = 6.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.End) {
                QuietButton("Opened", onClick = onOpen)
                QuietButton("Edit", onClick = { vm.editBottle(b.id) })
                QuietButton("Delete", onClick = { vm.deleteBottle(b.id) }, danger = true)
            }
        }
    }
}

/** Asks who drank the bottle, with each person's own score. */
@Composable
private fun OpenBottleDialog(
    row: BottleRow,
    profiles: List<Profile>,
    activeId: Long,
    onDismiss: () -> Unit,
    onConfirm: (List<Taster>) -> Unit,
) {
    // presence in the map = this person drank it; the value is the typed score
    val scores = remember { mutableStateMapOf<Long, String>().also { if (activeId >= 0) it[activeId] = "" } }
    val valid = scores.values.all { it.isEmpty() || validScore(it) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Opened: " + row.wine.name, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Who drank it?", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    profiles.forEach { p ->
                        FilterChip(
                            selected = scores.containsKey(p.id),
                            onClick = { if (scores.containsKey(p.id)) scores.remove(p.id) else scores[p.id] = "" },
                            label = { Text(p.name) },
                            leadingIcon = { ProfileDot(p, 22.dp) },
                        )
                    }
                }
                profiles.filter { scores.containsKey(it.id) }.forEach { p ->
                    Column {
                        Text(
                            "${p.name}'s score",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        ScoreInput(scores[p.id].orEmpty(), { scores[p.id] = it }, label = "Score /100 (empty = later)")
                    }
                }
                Text(
                    "One bottle leaves the cellar. A score becomes a tasting in the ledger; " +
                        "the other profile gets a card to say if they drank it too.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            PrimaryButton(
                "Open the bottle",
                onClick = {
                    onConfirm(
                        profiles.filter { scores.containsKey(it.id) }.map { p ->
                            val s = scores[p.id].orEmpty()
                            Taster(p.id, if (s.isEmpty()) null else s.replace(',', '.').toDouble())
                        }
                    )
                },
                enabled = valid,
            )
        },
        dismissButton = { QuietButton("Cancel", onClick = onDismiss) },
    )
}
