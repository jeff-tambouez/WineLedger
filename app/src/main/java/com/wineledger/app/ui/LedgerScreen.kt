package com.wineledger.app.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wineledger.app.AppViewModel
import com.wineledger.app.activeProfile
import com.wineledger.app.data.AppData
import com.wineledger.app.data.Invitation
import com.wineledger.app.data.Profile
import com.wineledger.app.logic.LedgerFilter
import com.wineledger.app.logic.LedgerSort
import com.wineledger.app.logic.PairRow
import com.wineledger.app.logic.WineGroup
import com.wineledger.app.logic.filterTastings
import com.wineledger.app.logic.flagFor
import com.wineledger.app.logic.fmtScore
import com.wineledger.app.logic.groupTastings
import com.wineledger.app.logic.pairRows
import com.wineledger.app.logic.sortGroups

@Composable
fun LedgerScreen(vm: AppViewModel) {
    val data by vm.data.collectAsStateWithLifecycle()
    val activeId by vm.activeId.collectAsStateWithLifecycle()
    val both by vm.both.collectAsStateWithLifecycle()
    val profile = data.activeProfile(activeId)

    var query by rememberSaveable { mutableStateOf("") }
    var style by rememberSaveable { mutableStateOf("") }
    var country by rememberSaveable { mutableStateOf("") }
    var region by rememberSaveable { mutableStateOf("") }
    var year by rememberSaveable { mutableStateOf("") }
    var minScore by rememberSaveable { mutableStateOf("") }
    var sortName by rememberSaveable { mutableStateOf(LedgerSort.SCORE.name) }
    var showFilters by rememberSaveable { mutableStateOf(false) }
    val expanded = remember { mutableStateMapOf<Long, Boolean>() }

    val filter = LedgerFilter(
        query = query, style = style, country = country, region = region,
        year = year.toIntOrNull(), minScore = minScore.replace(',', '.').toDoubleOrNull(),
    )
    val sort = LedgerSort.entries.firstOrNull { it.name == sortName } ?: LedgerSort.SCORE

    val source = remember(data.tastings, profile?.id, both) {
        if (both) data.tastings else data.tastings.filter { it.profileId == profile?.id }
    }
    val wineOf = data.wines
    val countries = remember(source, wineOf) {
        source.mapNotNull { wineOf[it.wineId]?.country }.filter { it.isNotEmpty() }.distinct().sorted()
    }
    val regions = remember(source, wineOf, country) {
        source.mapNotNull { wineOf[it.wineId] }
            .filter { country.isEmpty() || it.country == country }
            .map { it.region }.filter { it.isNotEmpty() }.distinct().sorted()
    }
    val years = remember(source) { source.map { it.year.toString() }.distinct().sortedDescending() }

    val groups = remember(source, wineOf, filter, sort, both) {
        if (both) emptyList() else sortGroups(groupTastings(filterTastings(source, wineOf, filter), wineOf), sort)
    }
    val pairs = remember(source, wineOf, filter, both) {
        if (both) pairRows(source, wineOf, filter) else emptyList()
    }
    val invitations = remember(data.invitations, profile?.id, both) {
        if (both || profile == null) emptyList() else data.invitations.filter { it.profileId == profile.id }
    }

    val activeFilters = listOf(style, country, region, year, minScore).count { it.isNotEmpty() }
    val totalWines = remember(source) { source.map { it.wineId }.distinct().size }
    val shownCount = if (both) pairs.size else groups.size

    LazyColumn(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column {
                Eyebrow(if (both) "Both of us" else "The cellar of memory")
                Text(
                    if (both) "Every wine, two palates" else "Every tasting",
                    style = MaterialTheme.typography.headlineSmall,
                )
                Spacer(Modifier.height(8.dp))
                SearchField(query, { query = it }, "Wine, producer, grape…")
                Row(
                    Modifier.fillMaxWidth().padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    QuietButton(
                        if (showFilters) "Hide filters" else if (activeFilters > 0) "Filters ($activeFilters)" else "Filters",
                        onClick = { showFilters = !showFilters },
                    )
                    if (activeFilters > 0 || query.isNotEmpty()) {
                        QuietButton("Reset", onClick = {
                            query = ""; style = ""; country = ""; region = ""; year = ""; minScore = ""
                        })
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        if (source.isEmpty()) "" else "$shownCount of $totalWines wines",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (showFilters) {
                    FilterPanel(
                        style = style, onStyle = { style = it },
                        country = country, onCountry = { country = it; region = "" },
                        region = region, onRegion = { region = it },
                        year = year, onYear = { year = it },
                        minScore = minScore, onMinScore = { minScore = it },
                        sort = sort, onSort = { sortName = it.name }, showSort = !both,
                        countries = countries, regions = regions, years = years,
                    )
                }
            }
        }

        items(invitations, key = { "inv" + it.id }) { inv ->
            InvitationCard(inv, data, vm)
        }

        if (source.isEmpty() && invitations.isEmpty()) {
            item {
                EmptyState(
                    "The ledger is waiting for its first glass",
                    "Tap + to record a tasting, open a bottle from the cellar, or import an archive in Settings.",
                )
            }
        } else if (shownCount == 0 && source.isNotEmpty()) {
            item {
                EmptyState("Nothing matches", "Widen the search or reset the filters.")
            }
        }

        if (both) {
            items(pairs, key = { "pair" + it.wine.id }) { row ->
                PairCard(row, data.profiles, vm)
            }
        } else {
            items(groups, key = { "wine" + it.wine.id }) { g ->
                WineGroupCard(
                    g, vm,
                    expanded = expanded[g.wine.id] == true,
                    onToggle = { expanded[g.wine.id] = expanded[g.wine.id] != true },
                )
            }
        }
    }
}

@Composable
private fun FilterPanel(
    style: String, onStyle: (String) -> Unit,
    country: String, onCountry: (String) -> Unit,
    region: String, onRegion: (String) -> Unit,
    year: String, onYear: (String) -> Unit,
    minScore: String, onMinScore: (String) -> Unit,
    sort: LedgerSort, onSort: (LedgerSort) -> Unit, showSort: Boolean,
    countries: List<String>, regions: List<String>, years: List<String>,
) {
    Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        StyleChips(style, onStyle)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DropdownField("Country", country, countries, onCountry, Modifier.weight(1f))
            DropdownField("Region", region, regions, onRegion, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DropdownField("Tasting year", year, years, onYear, Modifier.weight(1f))
            AppField(
                minScore, { onMinScore(it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(5)) },
                "Min. score", Modifier.weight(1f), keyboard = KeyboardType.Decimal,
            )
        }
        if (showSort) {
            DropdownField(
                "Sort by", sort.label, LedgerSort.entries.map { it.label },
                { label -> LedgerSort.entries.firstOrNull { it.label == label }?.let(onSort) },
                emptyLabel = LedgerSort.SCORE.label,
            )
        }
    }
}

@Composable
private fun WineGroupCard(g: WineGroup, vm: AppViewModel, expanded: Boolean, onToggle: () -> Unit) {
    val photo = g.tastings.sortedByDescending { it.year }.firstNotNullOfOrNull { it.photo } ?: g.wine.photo
    SoftCard(Modifier.fillMaxWidth()) {
        Column(Modifier.clickable(onClick = onToggle).padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (photo != null) {
                    PhotoThumb(vm.photoFile(photo, true), 56.dp)
                    Spacer(Modifier.width(12.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        g.wine.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        g.wine.producer,
                        style = MaterialTheme.typography.bodyMedium,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        listOf(flagFor(g.wine.country) + " " + g.wine.country, g.wine.region)
                            .filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                        StyleLabel(g.wine.style)
                        Text(
                            "  ·  " + g.years.joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    ScoreMedal(g.avg)
                    if (g.count > 1) {
                        Text(
                            "avg of ${g.count}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (expanded) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                if (g.wine.grapes.isNotBlank()) {
                    Text(
                        g.wine.grapes,
                        Modifier.padding(top = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                g.tastings.forEach { t ->
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(t.year.toString(), Modifier.width(52.dp), style = MaterialTheme.typography.labelLarge)
                        Text(
                            fmtScore(t.score),
                            Modifier.width(52.dp),
                            fontFamily = Fraunces,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            t.notes,
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        QuietButton("Edit", onClick = { vm.editTasting(t.id) })
                        QuietButton("Delete", onClick = { vm.deleteTasting(t.id) }, danger = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun PairCard(row: PairRow, profiles: List<Profile>, vm: AppViewModel) {
    SoftCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text(row.wine.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                row.wine.producer,
                style = MaterialTheme.typography.bodyMedium,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                StyleLabel(row.wine.style)
                val geo = listOf(row.wine.country, row.wine.region).filter { it.isNotBlank() }.joinToString(" · ")
                if (geo.isNotEmpty()) {
                    Text(
                        "  ·  $geo",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                profiles.forEach { p ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        ProfileDot(p, 24.dp)
                        Spacer(Modifier.height(4.dp))
                        val s = row.scores[p.id]
                        if (s != null) {
                            ScoreMedal(s)
                            val n = row.counts[p.id] ?: 0
                            if (n > 1) {
                                Text(
                                    "avg of $n",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else {
                            Text(
                                "not tasted",
                                Modifier.padding(top = 12.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InvitationCard(inv: Invitation, data: AppData, vm: AppViewModel) {
    val wine = data.wines[inv.wineId] ?: return
    val from = data.profiles.firstOrNull { it.id == inv.fromProfileId }
    var rating by remember(inv.id) { mutableStateOf(false) }
    var score by remember(inv.id) { mutableStateOf("") }
    SoftCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Eyebrow(if (inv.stage == 0) "Did you also drink this bottle?" else "Do you want to rate it?")
            Spacer(Modifier.height(4.dp))
            Text(
                wine.name + (inv.vintage?.let { " $it" } ?: ""),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                wine.producer + if (from != null && from.id != inv.profileId) " · opened by ${from.name}" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            if (inv.stage == 0) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PrimaryButton("Yes", onClick = { vm.invitationYes(inv.id); rating = true })
                    GhostButton("No", onClick = { vm.invitationNo(inv.id) })
                }
            } else if (rating) {
                ScoreInput(score, { score = it })
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 6.dp)) {
                    PrimaryButton(
                        "Save score",
                        onClick = { vm.invitationRate(inv.id, score.replace(',', '.').toDouble()) },
                        enabled = validScore(score),
                    )
                    GhostButton("Later", onClick = { rating = false })
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PrimaryButton("Rate now", onClick = { rating = true })
                    GhostButton("Not this time", onClick = { vm.invitationNo(inv.id) })
                }
            }
        }
    }
}
