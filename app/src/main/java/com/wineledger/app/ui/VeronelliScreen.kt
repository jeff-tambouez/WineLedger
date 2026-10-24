@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.wineledger.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wineledger.app.AppViewModel
import com.wineledger.app.activeProfile
import com.wineledger.app.logic.ArchiveIndex
import com.wineledger.app.logic.Awards
import com.wineledger.app.logic.GuideFilter
import com.wineledger.app.logic.GuideSort
import com.wineledger.app.logic.GuideStatus
import com.wineledger.app.logic.GuideWine
import com.wineledger.app.logic.STYLES
import com.wineledger.app.logic.filterGuide
import com.wineledger.app.logic.fmtScore
import kotlinx.coroutines.launch

private val STATE_OPTIONS = listOf(
    "" to "All wines",
    "cellar" to "In my cellar",
    "tasted" to "Already tasted",
    "known" to "Wineries I know",
    "wish" to "On my wishlist",
    "never" to "Never crossed",
)

@Composable
fun VeronelliScreen(vm: AppViewModel) {
    val data by vm.data.collectAsStateWithLifecycle()
    val guide by vm.guide.collectAsStateWithLifecycle()
    val activeId by vm.activeId.collectAsStateWithLifecycle()
    val profile = data.activeProfile(activeId)
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    var query by rememberSaveable { mutableStateOf("") }
    var region by rememberSaveable { mutableStateOf("") }
    var style by rememberSaveable { mutableStateOf("") }
    var vintage by rememberSaveable { mutableStateOf("") }
    var minPoints by rememberSaveable { mutableStateOf("") }
    var state by rememberSaveable { mutableStateOf("") }
    var awardsText by rememberSaveable { mutableStateOf("") }
    var sortName by rememberSaveable { mutableStateOf(GuideSort.POINTS.name) }
    var showFilters by rememberSaveable { mutableStateOf(false) }
    var openKey by rememberSaveable { mutableStateOf("") }
    val sort = GuideSort.entries.firstOrNull { it.name == sortName } ?: GuideSort.POINTS
    val awards = awardsText.toSet()

    val index = remember(data.wines, data.tastings, data.bottles, profile?.id) {
        val best = HashMap<Long, Double>()
        data.tastings.filter { it.profileId == profile?.id }.forEach { t ->
            best[t.wineId] = maxOf(best[t.wineId] ?: 0.0, t.score)
        }
        val qty = HashMap<Long, Int>()
        data.bottles.forEach { qty[it.wineId] = (qty[it.wineId] ?: 0) + it.quantity }
        ArchiveIndex(data.wines.values, best, qty)
    }
    val wished = remember(data.wishlist, profile?.id) {
        data.wishlist.filter { it.profileId == profile?.id }.map { it.guideKey }.toSet()
    }
    val filter = GuideFilter(
        query = query, region = region, style = style, vintage = vintage,
        minPoints = minPoints.toIntOrNull(), awards = awards, state = state,
    )
    val list = remember(guide, filter, index, wished, sort) {
        filterGuide(guide.wines, filter, index, wished, sort)
    }
    val activeFilters = listOf(region, style, vintage, minPoints, state, awardsText).count { it.isNotEmpty() }

    LazyColumn(
        Modifier.fillMaxWidth(),
        state = listState,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column {
                Eyebrow("Guida Oro I Vini di Veronelli")
                Text("Veronelli ${guide.edition}", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Every award-winning wine of the edition, embedded in the app. Works offline.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Awards.order.forEach { c ->
                        val n = guide.wines.count { c in it.awards }
                        FilterChip(
                            selected = c in awards,
                            onClick = {
                                awardsText = if (c in awards) awardsText.replace(c.toString(), "") else awardsText + c
                            },
                            label = { Text("${Awards.glyph(c)} ${Awards.name(c)} · $n") },
                            modifier = Modifier.heightIn(min = 40.dp).padding(vertical = 2.dp),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                SearchField(query, { query = it }, "Wine, winery, town…")
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    QuietButton(
                        if (showFilters) "Hide filters" else if (activeFilters > 0) "Filters ($activeFilters)" else "Filters",
                        onClick = { showFilters = !showFilters },
                    )
                    if (activeFilters > 0 || query.isNotEmpty()) {
                        QuietButton("Reset", onClick = {
                            query = ""; region = ""; style = ""; vintage = ""; minPoints = ""
                            state = ""; awardsText = ""; openKey = ""
                        })
                    }
                    Spacer(Modifier.weight(1f))
                    QuietButton("At random", onClick = {
                        if (list.isNotEmpty()) {
                            val i = list.indices.random()
                            openKey = list[i].key
                            scope.launch { listState.animateScrollToItem(1 + i) }
                        }
                    })
                }
                Text(
                    if (list.size == guide.wines.size) "${guide.wines.size} award-winning wines"
                    else "${list.size} of ${guide.wines.size} wines",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (showFilters) {
                    Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        StyleChips(style, { style = it }, styles = STYLES)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            DropdownField("Region", region, guide.regions, { region = it }, Modifier.weight(1f))
                            DropdownField("Vintage", vintage, guide.vintages, { vintage = it }, Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            AppField(
                                minPoints, { minPoints = it.filter { c -> c.isDigit() }.take(3) },
                                "Min. points", Modifier.weight(1f), keyboard = KeyboardType.Number,
                            )
                            DropdownField(
                                "In my archive",
                                STATE_OPTIONS.first { it.first == state }.second.takeIf { state.isNotEmpty() } ?: "",
                                STATE_OPTIONS.drop(1).map { it.second },
                                { label -> state = STATE_OPTIONS.firstOrNull { it.second == label }?.first ?: "" },
                                Modifier.weight(1f),
                                emptyLabel = "All wines",
                            )
                        }
                        DropdownField(
                            "Sort by", sort.label, GuideSort.entries.map { it.label },
                            { label -> GuideSort.entries.firstOrNull { it.label == label }?.let { sortName = it.name } },
                            emptyLabel = GuideSort.POINTS.label,
                        )
                    }
                }
            }
        }

        if (guide.wines.isEmpty()) {
            item { EmptyState("Loading the guide…", "One moment.") }
        } else if (list.isEmpty()) {
            item { EmptyState("No wine matches these filters", "Drop an award, lower the points or reset everything.") }
        }

        items(list, key = { it.key }) { w ->
            GuideCard(
                w = w,
                status = index.status(w),
                wished = w.key in wished,
                open = openKey == w.key,
                vm = vm,
                onToggleOpen = { openKey = if (openKey == w.key) "" else w.key },
            )
        }
    }
}

@Composable
private fun GuideCard(
    w: GuideWine,
    status: GuideStatus,
    wished: Boolean,
    open: Boolean,
    vm: AppViewModel,
    onToggleOpen: () -> Unit,
) {
    SoftCard(Modifier.fillMaxWidth()) {
        Column(Modifier.clickable(onClick = onToggleOpen).padding(start = 14.dp, top = 12.dp, end = 4.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(
                        w.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = if (open) 6 else 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        w.winery + if (w.vintage.isNotEmpty()) " · ${w.vintage}" else "",
                        style = MaterialTheme.typography.bodyMedium,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        StyleLabel(w.style)
                        val geo = buildList {
                            add(w.region)
                            if (w.town.isNotEmpty()) add(w.town + if (w.province.isNotEmpty()) " (${w.province})" else "")
                        }.filter { it.isNotBlank() }.joinToString(" · ")
                        Text(
                            (if (w.style.isNotEmpty()) "  ·  " else "") + geo,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                if (w.points > 0) ScoreMedal(w.points.toDouble(), 44.dp)
                StarButton(wished) { vm.toggleWish(w) }
            }
            Row(Modifier.padding(top = 8.dp, end = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                w.awards.forEach { AwardBadge(it) }
            }
            val tags = remember(status, wished) { statusTags(status, wished) }
            if (tags.isNotEmpty()) {
                FlowRow(
                    Modifier.padding(top = 6.dp, end = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    tags.forEach { (text, kind) ->
                        Tag(
                            text,
                            when (kind) {
                                0 -> MaterialTheme.colorScheme.primary
                                1 -> wl.burgundy
                                else -> wl.muted
                            },
                        )
                    }
                }
            }
            if (open) {
                Column(Modifier.padding(top = 10.dp, end = 10.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    if (w.category.isNotEmpty()) {
                        Text(
                            w.category,
                            Modifier.padding(top = 8.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = wl.gold,
                        )
                    }
                    if (w.note.isNotEmpty()) {
                        Text(
                            w.note,
                            Modifier.padding(top = 6.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            fontStyle = FontStyle.Italic,
                        )
                    }
                    Text(
                        "Editor ${w.curator.ifEmpty { "–" }} · " + w.awards.joinToString(" + ") { Awards.name(it) },
                        Modifier.padding(top = 6.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    crossLines(status).forEach {
                        Text(
                            it,
                            Modifier.padding(top = 4.dp),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PrimaryButton("Add to cellar", onClick = { vm.newBottle(vm.bottleDraftFrom(w)) })
                        GhostButton("Record a tasting", onClick = { vm.newTasting(vm.tastingDraftFrom(w)) })
                    }
                }
            }
        }
    }
}

@Composable
private fun StarButton(on: Boolean, onClick: () -> Unit) {
    androidx.compose.material3.IconButton(onClick = onClick, modifier = Modifier.size(MinTouch)) {
        androidx.compose.material3.Icon(
            androidx.compose.material.icons.Icons.Filled.Star,
            contentDescription = if (on) "Remove from wishlist" else "Add to wishlist",
            tint = if (on) wl.gold else MaterialTheme.colorScheme.outlineVariant,
        )
    }
}

/** Short labels with a kind: 0 = cellar, 1 = tasted, 2 = other. */
private fun statusTags(s: GuideStatus, wished: Boolean): List<Pair<String, Int>> {
    val out = ArrayList<Pair<String, Int>>()
    if (s.cellar.isNotEmpty()) {
        out += (if (s.sameWineInCellar) "In the cellar" else "Similar in cellar") to 0
    }
    if (s.tasted.isNotEmpty()) {
        val label = if (s.sameWineTasted) "Tasted" else "Similar tasted"
        out += (label + (s.bestScore?.let { " · ${fmtScore(it)}" } ?: "")) to 1
    }
    if (s.cellar.isEmpty() && s.tasted.isEmpty() && s.knownWinery) out += "Winery you know" to 2
    if (wished) out += "Wishlist" to 2
    return out
}

private fun crossLines(s: GuideStatus): List<String> {
    val lines = ArrayList<String>()
    if (s.tasted.isNotEmpty()) {
        lines += "In your ledger: " + s.tasted.joinToString(" · ") {
            it.wine.name + if (it.level == 2) "" else " (similar name)"
        }
    }
    if (s.cellar.isNotEmpty()) {
        lines += "In the cellar: " + s.cellar.joinToString(" · ") {
            it.wine.name + if (it.level == 2) "" else " (similar name)"
        }
    }
    if (lines.isEmpty()) {
        lines += if (s.knownWinery) "You know this winery, but not this wine yet."
        else "This winery is not in your archive yet."
    }
    return lines
}
