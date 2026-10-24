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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wineledger.app.AppViewModel
import com.wineledger.app.activeProfile
import com.wineledger.app.logic.computeStats
import com.wineledger.app.logic.flagFor
import com.wineledger.app.logic.fmtScore

private val ROMAN = listOf("I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X")

@Composable
fun StatsScreen(vm: AppViewModel) {
    val data by vm.data.collectAsStateWithLifecycle()
    val activeId by vm.activeId.collectAsStateWithLifecycle()
    val profile = data.activeProfile(activeId)
    val stats = remember(data.tastings, data.wines, profile?.id) {
        computeStats(data.tastings.filter { it.profileId == profile?.id }, data.wines)
    }

    LazyColumn(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column {
                Eyebrow("The journey in the glass")
                Text(
                    if (profile != null) "${profile.name}'s statistics" else "Statistics",
                    style = MaterialTheme.typography.headlineSmall,
                )
            }
        }
        if (stats == null) {
            item {
                EmptyState(
                    "No data yet",
                    "The statistics take shape as the ledger fills up. A handful of tastings is enough to see the first trends.",
                )
            }
            return@LazyColumn
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val range = if (stats.firstYear != null && stats.firstYear != stats.lastYear)
                        "${stats.firstYear} to ${stats.lastYear}" else stats.firstYear?.toString().orEmpty()
                    KpiTile(
                        stats.total.toString(), "Tastings",
                        "${stats.distinctWines} distinct wine${if (stats.distinctWines == 1) "" else "s"}" +
                            if (range.isNotEmpty()) " · $range" else "",
                        Modifier.weight(1f),
                    )
                    KpiTile(
                        fmtScore(stats.avg) + "/100", "Average score",
                        "${stats.over90} at 90+", Modifier.weight(1f),
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    KpiTile(
                        stats.countries.toString(), "Countries",
                        "${stats.regions} region${if (stats.regions == 1) "" else "s"}", Modifier.weight(1f),
                    )
                    KpiTile(
                        stats.best?.let { fmtScore(it.score) } ?: "–", "Best tasting",
                        stats.bestWine?.let { it.name } ?: "", Modifier.weight(1f),
                    )
                }
            }
        }

        item {
            ChartBlock("The styles in the glass", "How the tastings split by style") {
                Donut(
                    stats.byStyle.map { it.label to it.count },
                    stats.byStyle.map { styleColor(it.label) },
                )
            }
        }
        item {
            ChartBlock("Geographies of taste", "Tastings · average score, by country") {
                BarList(stats.byCountry, MaterialTheme.colorScheme.primary, labelPrefix = { flagFor(it) + " " + it })
            }
        }
        if (stats.byYear.size > 1) {
            item {
                ChartBlock("How the palate is trending", "Bars: tastings per year. Line: average score") {
                    YearTrend(stats.byYear)
                }
            }
        }
        if (stats.byGrape.isNotEmpty()) {
            item {
                ChartBlock("The grapes closest to the heart", "Most-tasted varieties") {
                    BarList(stats.byGrape, wl.burgundy)
                }
            }
        }
        if (stats.byRegion.isNotEmpty()) {
            item {
                ChartBlock("Favourite regions", "The regions your palate returns to") {
                    BarList(stats.byRegion, wl.gold)
                }
            }
        }
        item {
            ChartBlock("Best of", "The ten highest-scoring wines, averaged when tasted more than once") {
                Column {
                    stats.top10.forEachIndexed { i, g ->
                        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                ROMAN.getOrElse(i) { (i + 1).toString() },
                                Modifier.width(34.dp),
                                fontFamily = Fraunces,
                                fontStyle = FontStyle.Italic,
                                color = wl.gold,
                            )
                            Column(Modifier.weight(1f)) {
                                Text(
                                    g.wine.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    listOf(g.wine.producer, g.wine.region.ifEmpty { g.wine.country }, g.wine.style)
                                        .filter { it.isNotBlank() }.joinToString(" · ") +
                                        " · " + g.years.joinToString(" and "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            ScoreMedal(g.avg, 42.dp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChartBlock(title: String, sub: String, content: @Composable () -> Unit) {
    SoftCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                sub,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}
