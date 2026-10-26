package com.wineledger.app.logic

import com.wineledger.app.data.Bottle
import com.wineledger.app.data.BottleSnapshot
import com.wineledger.app.data.OpeningLog
import com.wineledger.app.data.Tasting
import com.wineledger.app.data.Wine
import java.text.Normalizer
import java.time.LocalDate
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.random.Random

// =====================================================================
// Constants
// =====================================================================

val STYLES = listOf("Red", "White", "Rosé", "Sparkling", "Dessert")
val FORMATS = listOf(0.375, 0.5, 0.75, 1.5, 3.0)

fun formatLabel(f: Double): String = when (f) {
    0.375 -> "37.5 cl"
    0.5 -> "50 cl"
    0.75 -> "75 cl"
    1.5 -> "Magnum"
    3.0 -> "Double magnum"
    else -> "${fmtNumber(f)} l"
}

fun fmtNumber(d: Double): String =
    if (d % 1.0 == 0.0) d.toLong().toString() else String.format(Locale.ROOT, "%.1f", d)

fun fmtScore(d: Double): String = fmtNumber(Math.round(d * 10) / 10.0)

fun fmtMoney(d: Double): String =
    "€ " + String.format(Locale.ROOT, "%,.0f", d)

fun todayIso(): String = LocalDate.now().toString()

fun currentYear(): Int = LocalDate.now().year

/** "2026-10-23" to "23102026", the digits a person types. Anything that is not an ISO date gives "". */
fun isoToDateDigits(iso: String): String {
    val d = runCatching { LocalDate.parse(iso) }.getOrNull() ?: return ""
    return String.format(Locale.ROOT, "%02d%02d%04d", d.dayOfMonth, d.monthValue, d.year)
}

/** "2026-10-23" to "23/10/2026". Anything else is returned as it is. */
fun displayDate(iso: String): String {
    val d = isoToDateDigits(iso)
    return if (d.length == 8) "${d.substring(0, 2)}/${d.substring(2, 4)}/${d.substring(4)}" else iso
}

/** Number as typed back into a field: no rounding, no trailing ".0". */
fun fmtExact(d: Double): String = if (d % 1.0 == 0.0) d.toLong().toString() else d.toString()

/** "23102026" to "2026-10-23". Null when the digits are incomplete or not a real date. */
fun dateDigitsToIso(digits: String): String? {
    if (digits.length != 8 || !digits.all { it in '0'..'9' }) return null
    val year = digits.substring(4).toInt()
    if (year !in 1900..2100) return null
    return runCatching {
        LocalDate.of(year, digits.substring(2, 4).toInt(), digits.substring(0, 2).toInt()).toString()
    }.getOrNull()
}

// =====================================================================
// Text normalisation
// =====================================================================

private val DIACRITICS = Regex("\\p{InCombiningDiacriticalMarks}+")
private val NON_ALNUM = Regex("[^a-z0-9]+")

fun stripAccents(s: String): String =
    DIACRITICS.replace(Normalizer.normalize(s, Normalizer.Form.NFD), "")

/** Lowercase letters and digits only. */
fun norm(s: String?): String =
    NON_ALNUM.replace(stripAccents(s.orEmpty()).lowercase(Locale.ROOT), "")

/** Lowercase words (letters and digits), accents removed. */
fun tokens(s: String?): List<String> =
    stripAccents(s.orEmpty()).lowercase(Locale.ROOT).split(NON_ALNUM).filter { it.isNotEmpty() }

/** Identity of a wine: producer and name, accents and punctuation ignored. */
fun wineKey(producer: String, name: String): String =
    tokens(producer).joinToString(" ") + "||" + tokens(name).joinToString(" ")

/** Same key as the original HTML page, so wishlists can be imported. */
fun guideKey(name: String, vintage: String, winery: String): String =
    norm(name) + "|" + vintage + "|" + norm(winery)

fun matchesQuery(query: String, vararg fields: String?): Boolean {
    val q = norm(query)
    if (q.isEmpty()) return true
    return fields.any { norm(it).contains(q) }
}

fun normStyle(s: String?): String {
    val raw = s.orEmpty().trim()
    return when (raw.lowercase(Locale.ROOT)) {
        "red", "rosso" -> "Red"
        "white", "bianco" -> "White"
        "rose", "rosé", "rosato" -> "Rosé"
        "sparkling", "spumante", "bubbles", "bollicine" -> "Sparkling"
        "dessert", "sweet", "dolce" -> "Dessert"
        else -> raw
    }
}

fun styleColorHex(style: String): Long = when (style) {
    "Red" -> 0xFF990044
    "White" -> 0xFFB8935A
    "Rosé" -> 0xFFD98090
    "Sparkling" -> 0xFF076967
    "Dessert" -> 0xFFC77B3A
    else -> 0xFF8A8478
}

private val COUNTRY_CODES = mapOf(
    "italy" to "IT", "italia" to "IT", "france" to "FR", "francia" to "FR",
    "germany" to "DE", "germania" to "DE", "deutschland" to "DE",
    "spain" to "ES", "spagna" to "ES", "portugal" to "PT", "portogallo" to "PT",
    "usa" to "US", "united states" to "US", "stati uniti" to "US",
    "chile" to "CL", "cile" to "CL", "argentina" to "AR",
    "south africa" to "ZA", "sudafrica" to "ZA", "sud africa" to "ZA",
    "australia" to "AU", "new zealand" to "NZ", "nuova zelanda" to "NZ",
    "austria" to "AT", "greece" to "GR", "grecia" to "GR",
    "hungary" to "HU", "ungheria" to "HU", "slovenia" to "SI",
    "croatia" to "HR", "croazia" to "HR", "switzerland" to "CH", "svizzera" to "CH",
    "georgia" to "GE", "lebanon" to "LB", "libano" to "LB",
    "england" to "GB", "uk" to "GB", "united kingdom" to "GB", "regno unito" to "GB",
    "canada" to "CA", "uruguay" to "UY", "moldova" to "MD", "moldavia" to "MD",
    "romania" to "RO", "bulgaria" to "BG", "turkey" to "TR", "turchia" to "TR",
    "japan" to "JP", "giappone" to "JP", "china" to "CN", "cina" to "CN",
    "israel" to "IL", "israele" to "IL", "mexico" to "MX", "messico" to "MX",
    "brazil" to "BR", "brasile" to "BR",
)

fun flagFor(country: String): String {
    val code = COUNTRY_CODES[country.trim().lowercase(Locale.ROOT)] ?: return "🍷"
    val sb = StringBuilder()
    code.forEach { c -> sb.appendCodePoint(0x1F1E6 + (c - 'A')) }
    return sb.toString()
}

// =====================================================================
// Drinking window
// =====================================================================

enum class Window { LATE, NOW, WAIT, READY, NONE }

fun readiness(b: Bottle, year: Int): Window {
    val from = b.readyFrom
    val by = b.drinkBy
    if (by != null && by < year) return Window.LATE
    if (by != null && by == year) return Window.NOW
    if (from != null && from > year) return Window.WAIT
    if (from != null || by != null) return Window.READY
    return Window.NONE
}

fun windowLabel(b: Bottle, w: Window): String = when (w) {
    Window.LATE -> "Past window"
    Window.NOW -> "Drink this year"
    Window.WAIT -> "Waiting" + (b.readyFrom?.let { " until $it" } ?: "")
    Window.READY -> "Ready" + (b.drinkBy?.let { " until $it" } ?: "")
    Window.NONE -> ""
}

// =====================================================================
// Ledger
// =====================================================================

/** One line with what the cellar knew about a tasted bottle: format, price, window, where and when bought. */
fun snapshotLine(b: BottleSnapshot?): String {
    if (b == null) return ""
    val parts = ArrayList<String>()
    b.format?.takeIf { it != 0.75 }?.let { parts += formatLabel(it) }
    b.price?.let { parts += fmtMoney(it) }
    val from = b.readyFrom
    val by = b.drinkBy
    when {
        from != null && by != null -> parts += "Drink $from–$by"
        from != null -> parts += "Drink from $from"
        by != null -> parts += "Drink by $by"
    }
    val supplier = b.supplier?.takeIf { it.isNotBlank() }
    val date = b.purchaseDate?.takeIf { it.isNotBlank() }?.let { displayDate(it) }
    when {
        supplier != null && date != null -> parts += "Bought from $supplier, $date"
        supplier != null -> parts += "Bought from $supplier"
        date != null -> parts += "Bought $date"
    }
    return parts.joinToString(" · ")
}

data class WineGroup(val wine: Wine, val tastings: List<Tasting>) {
    val count: Int get() = tastings.size
    val avg: Double get() = Math.round(tastings.map { it.score }.average() * 10) / 10.0
    val years: List<Int> get() = tastings.map { it.year }.sorted()
    val vintages: List<Int> get() = tastings.mapNotNull { it.vintage }.distinct().sorted()
    val lastYear: Int get() = tastings.maxOfOrNull { it.year } ?: 0
}

fun groupTastings(tastings: List<Tasting>, wines: Map<Long, Wine>): List<WineGroup> =
    tastings.groupBy { it.wineId }.mapNotNull { (id, list) ->
        wines[id]?.let { WineGroup(it, list.sortedBy { t -> t.year }) }
    }

data class LedgerFilter(
    val query: String = "",
    val style: String = "",
    val country: String = "",
    val region: String = "",
    val year: Int? = null,
    val minScore: Double? = null,
) {
    val active: Boolean
        get() = query.isNotBlank() || style.isNotEmpty() || country.isNotEmpty() ||
            region.isNotEmpty() || year != null || minScore != null
}

fun filterTastings(tastings: List<Tasting>, wines: Map<Long, Wine>, f: LedgerFilter): List<Tasting> =
    tastings.filter { t ->
        val w = wines[t.wineId] ?: return@filter false
        if (f.style.isNotEmpty() && w.style != f.style) return@filter false
        if (f.country.isNotEmpty() && w.country != f.country) return@filter false
        if (f.region.isNotEmpty() && w.region != f.region) return@filter false
        if (f.year != null && t.year != f.year) return@filter false
        if (f.minScore != null && t.score < f.minScore) return@filter false
        matchesQuery(f.query, w.name, w.producer, w.country, w.region, w.style, w.grapes, t.vintage?.toString())
    }

enum class LedgerSort(val label: String) {
    SCORE("Score"), YEAR("Year"), WINE("Wine"), PRODUCER("Producer"), COUNTRY("Country"), STYLE("Style")
}

fun sortGroups(list: List<WineGroup>, sort: LedgerSort): List<WineGroup> = when (sort) {
    LedgerSort.SCORE -> list.sortedWith(compareByDescending<WineGroup> { it.avg }.thenBy { it.wine.name.lowercase() })
    LedgerSort.YEAR -> list.sortedWith(compareByDescending<WineGroup> { it.lastYear }.thenByDescending { it.avg })
    LedgerSort.WINE -> list.sortedBy { it.wine.name.lowercase() }
    LedgerSort.PRODUCER -> list.sortedWith(compareBy<WineGroup> { it.wine.producer.lowercase() }.thenBy { it.wine.name.lowercase() })
    LedgerSort.COUNTRY -> list.sortedWith(compareBy<WineGroup> { it.wine.country.lowercase() }.thenBy { it.wine.region.lowercase() })
    LedgerSort.STYLE -> list.sortedWith(compareBy<WineGroup> { it.wine.style }.thenByDescending { it.avg })
}

/** Ledger entry for the "Both of us" view: one wine, one average per profile. */
data class PairRow(val wine: Wine, val scores: Map<Long, Double>, val counts: Map<Long, Int>)

fun pairRows(tastings: List<Tasting>, wines: Map<Long, Wine>, filter: LedgerFilter): List<PairRow> {
    val kept = filterTastings(tastings, wines, filter)
    return kept.groupBy { it.wineId }.mapNotNull { (id, list) ->
        val w = wines[id] ?: return@mapNotNull null
        val perProfile = list.groupBy { it.profileId }
        PairRow(
            w,
            perProfile.mapValues { (_, l) -> Math.round(l.map { it.score }.average() * 10) / 10.0 },
            perProfile.mapValues { (_, l) -> l.size },
        )
    }.sortedBy { it.wine.name.lowercase() }
}

// =====================================================================
// Cellar
// =====================================================================

data class BottleRow(val bottle: Bottle, val wine: Wine, val window: Window, val score: Double?)

enum class CellarSort(val label: String) {
    PRODUCER("Producer"), WINE("Wine"), VINTAGE("Vintage"), SCORE("My score"), PRICE("Price"), ADDED("Recently added")
}

/** Window filter values: "", "now" (this year or past), "late", "wait", "ready". */
fun cellarRows(
    bottles: List<Bottle>,
    wines: Map<Long, Wine>,
    myAverages: Map<Long, Double>,
    query: String,
    style: String,
    windowFilter: String,
    sort: CellarSort,
    year: Int,
): List<BottleRow> {
    val rows = bottles.mapNotNull { b ->
        val w = wines[b.wineId] ?: return@mapNotNull null
        BottleRow(b, w, readiness(b, year), myAverages[w.id])
    }.filter { r ->
        if (style.isNotEmpty() && r.wine.style != style) return@filter false
        when (windowFilter) {
            "now" -> if (r.window != Window.NOW && r.window != Window.LATE) return@filter false
            "late" -> if (r.window != Window.LATE) return@filter false
            "wait" -> if (r.window != Window.WAIT) return@filter false
            "ready" -> if (r.window != Window.READY && r.window != Window.NOW) return@filter false
        }
        matchesQuery(
            query, r.wine.name, r.wine.producer, r.wine.country, r.wine.region, r.wine.style,
            r.wine.grapes, r.bottle.vintage?.toString(), r.bottle.notes, r.bottle.supplier,
        )
    }
    return when (sort) {
        CellarSort.PRODUCER -> rows.sortedWith(compareBy<BottleRow> { it.wine.producer.lowercase() }.thenBy { it.wine.name.lowercase() })
        CellarSort.WINE -> rows.sortedBy { it.wine.name.lowercase() }
        CellarSort.VINTAGE -> rows.sortedByDescending { it.bottle.vintage ?: 0 }
        CellarSort.SCORE -> rows.sortedByDescending { it.score ?: -1.0 }
        CellarSort.PRICE -> rows.sortedByDescending { it.bottle.price ?: 0.0 }
        CellarSort.ADDED -> rows.sortedByDescending { it.bottle.addedAt }
    }
}

data class CellarKpi(
    val bottles: Int,
    val labels: Int,
    val countries: Int,
    val value: Double,
    val pricedBottles: Int,
    val drinkNow: Int,
    val pastWindow: Int,
    val thisYear: Int,
    val openedLast12: Int,
    val lastOpened: String?,
)

fun cellarKpi(bottles: List<Bottle>, wines: Map<Long, Wine>, openings: List<OpeningLog>, year: Int): CellarKpi {
    val qty = { b: Bottle -> if (b.quantity < 1) 1 else b.quantity }
    val total = bottles.sumOf(qty)
    val priced = bottles.filter { it.price != null }
    val value = priced.sumOf { (it.price ?: 0.0) * qty(it) }
    val now = bottles.filter { readiness(it, year) == Window.NOW }.sumOf(qty)
    val late = bottles.filter { readiness(it, year) == Window.LATE }.sumOf(qty)
    val limit = LocalDate.now().minusYears(1).toString()
    val recent = openings.filter { it.date >= limit }
    val last = openings.maxByOrNull { it.id }
    val countries = bottles.mapNotNull { wines[it.wineId]?.country }.filter { it.isNotEmpty() }.toSet().size
    return CellarKpi(
        bottles = total,
        labels = bottles.size,
        countries = countries,
        value = value,
        pricedBottles = priced.sumOf(qty),
        drinkNow = now + late,
        pastWindow = late,
        thisYear = now,
        openedLast12 = recent.size,
        lastOpened = last?.wine,
    )
}

/** "What shall I drink tonight?": prefer bottles at or past the end of their window. */
fun pickTonight(rows: List<BottleRow>, style: String, random: Random = Random.Default): BottleRow? {
    val pool = rows.filter { style.isEmpty() || it.wine.style == style }
    if (pool.isEmpty()) return null
    val urgent = pool.filter { it.window == Window.LATE || it.window == Window.NOW }
    val ready = pool.filter { it.window != Window.WAIT }
    val from = if (urgent.isNotEmpty()) urgent else if (ready.isNotEmpty()) ready else pool
    return from[random.nextInt(from.size)]
}

// =====================================================================
// Veronelli guide
// =====================================================================

data class GuideWine(
    val key: String,
    val name: String,
    val vintage: String,
    val winery: String,
    val region: String,
    val curator: String,
    val points: Int,
    val awards: List<Char>,
    /** One of STYLES, or empty when the guide does not say. */
    val style: String,
    val town: String,
    val province: String,
    val category: String,
    val note: String,
) {
    val vintageInt: Int? get() = vintage.toIntOrNull()
    val hay: String = norm(name + winery + region + town + vintage)
}

object Awards {
    const val POLE = 'P'
    const val BEST = 'A'
    const val SUN = 'S'
    const val GOLD = 'O'
    const val DEBUT = 'E'

    val order = listOf(POLE, BEST, SUN, GOLD, DEBUT)

    fun name(c: Char): String = when (c) {
        POLE -> "Stella Polare"
        BEST -> "Migliori Assaggi"
        SUN -> "Sole"
        GOLD -> "Tre Stelle Oro"
        DEBUT -> "Grandi Esordi"
        else -> c.toString()
    }

    fun blurb(c: Char): String = when (c) {
        POLE -> "The absolute peaks, 97/100 and above"
        BEST -> "The highest-rated wine in each style"
        SUN -> "The editors' prize for the story behind the wine"
        GOLD -> "94 to 96, already at the top in the past"
        DEBUT -> "Debuting in the guide, 94 and above"
        else -> ""
    }

    fun glyph(c: Char): String = when (c) {
        POLE -> "✦"
        BEST -> "✺"
        SUN -> "☀"
        GOLD -> "★★★"
        DEBUT -> "✿"
        else -> ""
    }
}

private val STOP_PROD = setOf(
    "di", "de", "del", "della", "dei", "delle", "da", "dal", "dalla", "e", "ed", "il", "lo", "la", "i",
    "gli", "le", "figli", "fratelli", "flli", "az", "agricola", "azienda", "cantine", "societa", "srl",
    "spa", "sas", "snc",
)
private val STOP_VINO = setOf(
    "riserva", "vigna", "vigne", "vigneto", "vigneti", "cru", "tenuta", "di", "de", "del", "della", "dei",
    "delle", "da", "d", "e", "il", "lo", "la", "i", "gli", "le", "classico", "superiore", "etichetta",
    "doc", "docg", "igt", "dop", "millesimato", "s", "a",
)

private fun significant(s: String?, stop: Set<String>): List<String> =
    tokens(s).filter { it !in stop && !it.all { c -> c.isDigit() } }

private fun isSubset(a: List<String>, b: List<String>) = a.isNotEmpty() && a.all { it in b }

/** Wineries: the same words in any order, one side may have extra words. */
fun producerMatch(a: List<String>, b: List<String>): Boolean = isSubset(a, b) || isSubset(b, a)

/** Wines: 2 = same words (ignoring "Riserva", "Vigna"...), 1 = very likely, 0 = no. */
fun wineMatch(a: List<String>, b: List<String>): Int {
    if (a.isEmpty() || b.isEmpty()) return 0
    val shared = a.count { it in b }
    if (shared == a.size && shared == b.size) return 2
    val min = minOf(a.size, b.size)
    if (min >= 2 && (isSubset(a, b) || isSubset(b, a))) return 1
    if (min >= 2 && shared >= 2 && shared.toDouble() / min >= 0.6) return 1
    return 0
}

data class GuideMatch(val wine: Wine, val level: Int)

data class GuideStatus(
    val knownWinery: Boolean,
    val tasted: List<GuideMatch>,
    val cellar: List<GuideMatch>,
    val bestScore: Double?,
) {
    val sameWineTasted: Boolean get() = tasted.any { it.level == 2 }
    val sameWineInCellar: Boolean get() = cellar.any { it.level == 2 }
}

/**
 * Cross-reference between the guide and what the active profile has tasted or what sits in the cellar.
 * [tastedBest] maps wineId to the profile's best score, [cellarQty] maps wineId to bottles in the cellar.
 */
class ArchiveIndex(
    wines: Collection<Wine>,
    private val tastedBest: Map<Long, Double>,
    private val cellarQty: Map<Long, Int>,
) {
    private class Prod(val tok: List<String>) {
        val wines = ArrayList<Wine>()
    }

    private val prods = HashMap<String, Prod>()
    private val cache = HashMap<String, GuideStatus>()

    init {
        for (w in wines) {
            if (w.id !in tastedBest && w.id !in cellarQty) continue
            val k = tokens(w.producer).sorted().joinToString("")
            if (k.isEmpty()) continue
            prods.getOrPut(k) { Prod(significant(w.producer, STOP_PROD)) }.wines.add(w)
        }
    }

    fun status(g: GuideWine): GuideStatus = cache.getOrPut(g.key) {
        val ctok = significant(g.winery, STOP_PROD)
        val wtok = significant(g.name, STOP_VINO)
        val tasted = ArrayList<GuideMatch>()
        val cellar = ArrayList<GuideMatch>()
        var known = false
        for (p in prods.values) {
            if (!producerMatch(ctok, p.tok)) continue
            known = true
            for (w in p.wines) {
                val m = wineMatch(wtok, significant(w.name, STOP_VINO))
                if (m == 0) continue
                if (w.id in tastedBest) tasted.add(GuideMatch(w, m))
                if (w.id in cellarQty) cellar.add(GuideMatch(w, m))
            }
        }
        val best = tasted.mapNotNull { tastedBest[it.wine.id] }.maxOrNull()
        GuideStatus(known, tasted, cellar, best)
    }
}

data class GuideFilter(
    val query: String = "",
    val region: String = "",
    val style: String = "",
    val vintage: String = "",
    val minPoints: Int? = null,
    val awards: Set<Char> = emptySet(),
    /** "", "cellar", "tasted", "known", "wish", "never" */
    val state: String = "",
)

enum class GuideSort(val label: String) { POINTS("Points"), WINE("Wine"), WINERY("Winery"), REGION("Region") }

fun filterGuide(
    all: List<GuideWine>,
    f: GuideFilter,
    index: ArchiveIndex,
    wished: Set<String>,
    sort: GuideSort,
): List<GuideWine> {
    val q = norm(f.query)
    val list = all.filter { w ->
        if (q.isNotEmpty() && !w.hay.contains(q)) return@filter false
        if (f.region.isNotEmpty() && w.region != f.region) return@filter false
        if (f.style.isNotEmpty() && w.style != f.style) return@filter false
        if (f.vintage.isNotEmpty() && w.vintage != f.vintage) return@filter false
        if (f.minPoints != null && w.points < f.minPoints) return@filter false
        if (f.awards.isNotEmpty() && w.awards.none { it in f.awards }) return@filter false
        if (f.state.isNotEmpty()) {
            val s = index.status(w)
            when (f.state) {
                "cellar" -> if (s.cellar.isEmpty()) return@filter false
                "tasted" -> if (s.tasted.isEmpty()) return@filter false
                "known" -> if (!s.knownWinery) return@filter false
                "wish" -> if (w.key !in wished) return@filter false
                "never" -> if (s.knownWinery) return@filter false
            }
        }
        true
    }
    return when (sort) {
        GuideSort.POINTS -> list.sortedWith(compareByDescending<GuideWine> { it.points }.thenBy { norm(it.name) })
        GuideSort.WINE -> list.sortedBy { norm(it.name) }
        GuideSort.WINERY -> list.sortedWith(compareBy<GuideWine> { norm(it.winery) }.thenByDescending { it.points })
        GuideSort.REGION -> list.sortedWith(compareBy<GuideWine> { it.region }.thenByDescending { it.points })
    }
}

// =====================================================================
// Statistics (active profile)
// =====================================================================

data class Counted(val label: String, val count: Int, val avg: Double)

data class Stats(
    val total: Int,
    val distinctWines: Int,
    val avg: Double,
    val over90: Int,
    val countries: Int,
    val regions: Int,
    val firstYear: Int?,
    val lastYear: Int?,
    val best: Tasting?,
    val bestWine: Wine?,
    val byStyle: List<Counted>,
    val byCountry: List<Counted>,
    val byRegion: List<Counted>,
    val byGrape: List<Counted>,
    val byYear: List<Counted>,
    val top10: List<WineGroup>,
)

private fun counted(label: String, list: List<Tasting>) =
    Counted(label, list.size, Math.round(list.map { it.score }.average() * 10) / 10.0)

fun computeStats(tastings: List<Tasting>, wines: Map<Long, Wine>): Stats? {
    val valid = tastings.filter { wines.containsKey(it.wineId) }
    if (valid.isEmpty()) return null
    fun by(key: (Tasting) -> String?): List<Counted> =
        valid.groupBy { key(it).orEmpty() }.filterKeys { it.isNotEmpty() }
            .map { (k, v) -> counted(k, v) }
            .sortedWith(compareByDescending<Counted> { it.count }.thenByDescending { it.avg })

    val byGrape = HashMap<String, MutableList<Tasting>>()
    valid.forEach { t ->
        grapesOf(wines[t.wineId]?.grapes).forEach { g ->
            val label = g.replaceFirstChar { c -> c.uppercase() }
            byGrape.getOrPut(label) { ArrayList() }.add(t)
        }
    }
    val best = valid.maxByOrNull { it.score }
    val groups = groupTastings(valid, wines)
    val years = valid.map { it.year }
    return Stats(
        total = valid.size,
        distinctWines = groups.size,
        avg = Math.round(valid.map { it.score }.average() * 10) / 10.0,
        over90 = valid.count { it.score >= 90 },
        countries = valid.mapNotNull { wines[it.wineId]?.country }.filter { it.isNotEmpty() }.toSet().size,
        regions = valid.mapNotNull { wines[it.wineId]?.region }.filter { it.isNotEmpty() }.toSet().size,
        firstYear = years.minOrNull(),
        lastYear = years.maxOrNull(),
        best = best,
        bestWine = best?.let { wines[it.wineId] },
        byStyle = STYLES.mapNotNull { s ->
            val l = valid.filter { wines[it.wineId]?.style == s }
            if (l.isEmpty()) null else counted(s, l)
        },
        byCountry = by { wines[it.wineId]?.country }.take(8),
        byRegion = by { wines[it.wineId]?.region }.take(8),
        byGrape = byGrape.map { (k, v) -> counted(k, v) }
            .sortedWith(compareByDescending<Counted> { it.count }.thenByDescending { it.avg }).take(8),
        byYear = valid.groupBy { it.year }.toSortedMap().map { (y, v) -> counted(y.toString(), v) },
        top10 = groups.sortedWith(compareByDescending<WineGroup> { it.avg }.thenBy { it.wine.name }).take(10),
    )
}

// =====================================================================
// CSV
// =====================================================================

fun csvField(v: Any?): String = "\"" + (v?.toString() ?: "").replace("\"", "\"\"") + "\""

fun csvLine(vararg fields: Any?): String = fields.joinToString(";") { csvField(it) }

fun parseCsv(input: String, delimiter: Char): List<List<String>> {
    val text = input.removePrefix("﻿")
    val rows = ArrayList<List<String>>()
    var row = ArrayList<String>()
    val cur = StringBuilder()
    var inQuotes = false
    var i = 0
    while (i < text.length) {
        val c = text[i]
        if (inQuotes) {
            if (c == '"') {
                if (i + 1 < text.length && text[i + 1] == '"') {
                    cur.append('"')
                    i++
                } else {
                    inQuotes = false
                }
            } else {
                cur.append(c)
            }
        } else if (c == '"') {
            inQuotes = true
        } else if (c == delimiter) {
            row.add(cur.toString())
            cur.setLength(0)
        } else if (c == '\n' || c == '\r') {
            if (c == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
            row.add(cur.toString())
            cur.setLength(0)
            if (row.any { it.isNotBlank() }) rows.add(row)
            row = ArrayList()
        } else {
            cur.append(c)
        }
        i++
    }
    row.add(cur.toString())
    if (row.any { it.isNotBlank() }) rows.add(row)
    return rows
}

fun detectDelimiter(text: String): Char {
    val nl = text.indexOf('\n')
    val first = if (nl < 0) text else text.substring(0, nl)
    return if (first.count { it == ';' } >= first.count { it == ',' }) ';' else ','
}

fun parseDoubleLoose(s: String?): Double? = s?.trim()?.replace(',', '.')?.toDoubleOrNull()

fun parseIntLoose(s: String?): Int? {
    val t = s?.trim().orEmpty()
    return t.toIntOrNull() ?: t.toDoubleOrNull()?.roundToInt()
}
