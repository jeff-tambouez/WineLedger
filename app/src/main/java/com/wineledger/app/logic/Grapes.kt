package com.wineledger.app.logic

import java.util.Locale

// =====================================================================
// Grapes of a wine: names with an optional percentage
//
// A wine keeps its grapes as one readable text, "Merlot 70%, Cabernet Franc 30%, Petit Verdot".
// The percentage is optional for each grape, so older plain lists ("Merlot, Syrah") stay valid.
// =====================================================================

/** One grape of a wine. [pct] is the number as typed ("70", "12.5") or empty when unknown. */
data class GrapeShare(val name: String, val pct: String = "") {
    val value: Double? get() = pct.toDoubleOrNull()
}

private val GRAPE_FIX = mapOf(
    "cabernet souvignon" to "Cabernet Sauvignon",
    "barbera d'asti" to "Barbera",
    "barbera d'alba" to "Barbera",
)

private val PCT_TAIL = Regex("^(.*?)\\s*(\\d+(?:[.,]\\d+)?)\\s*%\\s*$")
/** Grapes are separated by a comma or a semicolon, but not by the comma of "12,5%". */
private val SEPARATOR = Regex("(?<!\\d),(?!\\d)|;")

fun parseGrapeShares(s: String?): List<GrapeShare> {
    val out = ArrayList<GrapeShare>()
    s.orEmpty().split(SEPARATOR).map { it.trim() }.filter { it.isNotEmpty() }.forEach { raw ->
        val m = PCT_TAIL.find(raw)
        val rawName = (m?.groupValues?.get(1) ?: raw).trim()
        if (rawName.isEmpty()) return@forEach
        val name = GRAPE_FIX[rawName.lowercase(Locale.ROOT)] ?: rawName
        val pct = m?.groupValues?.get(2)?.replace(',', '.').orEmpty()
        if (out.none { it.name.equals(name, ignoreCase = true) }) out.add(GrapeShare(name, pct))
    }
    return out
}

private fun cleanPct(pct: String): String = pct.replace(',', '.').trim().trimEnd('.')

fun formatGrapeShare(g: GrapeShare): String {
    val p = cleanPct(g.pct)
    return if (p.isEmpty()) g.name else "${g.name} $p%"
}

fun formatGrapeShares(list: List<GrapeShare>): String = list.joinToString(", ") { formatGrapeShare(it) }

/** Same grapes, written the standard way. Percentages are kept. */
fun normalizeGrapes(s: String?): String = formatGrapeShares(parseGrapeShares(s))

/** Names only, without percentages. Used for statistics and suggestions. */
fun grapesOf(s: String?): List<String> = parseGrapeShares(s).map { it.name }

fun joinGrapes(list: List<String>): String = list.joinToString(", ")

/** Sum of the percentages that are known, or null when there is none. */
fun grapeTotal(list: List<GrapeShare>): Double? {
    val known = list.mapNotNull { it.value }
    return if (known.isEmpty()) null else known.sum()
}

/** "  sauvignon   blanc " to "Sauvignon Blanc". */
fun tidyGrapeName(raw: String): String =
    raw.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        .joinToString(" ") { w -> w.replaceFirstChar { c -> c.titlecase(Locale.ROOT) } }

// =====================================================================
// Built-in grape database
// =====================================================================

enum class GrapeColor { RED, WHITE }

/** A grape the person can pick. [color] is null for a grape added by hand. */
data class GrapeDef(
    val name: String,
    val color: GrapeColor?,
    val popular: Boolean = false,
    val aliases: List<String> = emptyList(),
) {
    private val hay: String = norm(name + " " + aliases.joinToString(" "))

    fun matches(query: String): Boolean {
        val q = norm(query)
        return q.isEmpty() || hay.contains(q)
    }

    /** True when [text] is this grape's name or one of its other names. */
    fun isNamed(text: String): Boolean {
        val q = norm(text)
        return q.isNotEmpty() && (norm(name) == q || aliases.any { norm(it) == q })
    }
}

object GrapeDb {
    private fun red(name: String, vararg aliases: String, popular: Boolean = false) =
        GrapeDef(name, GrapeColor.RED, popular, aliases.toList())

    private fun white(name: String, vararg aliases: String, popular: Boolean = false) =
        GrapeDef(name, GrapeColor.WHITE, popular, aliases.toList())

    val builtIn: List<GrapeDef> = listOf(
        // ---- red: France and international ----
        red("Cabernet Sauvignon", popular = true),
        red("Merlot", popular = true),
        red("Pinot Noir", "Spätburgunder", "Pinot Nero", popular = true),
        red("Syrah", "Shiraz", popular = true),
        red("Grenache", "Garnacha", "Cannonau", popular = true),
        red("Cabernet Franc", popular = true),
        red("Malbec", "Côt", "Auxerrois", popular = true),
        red("Gamay", popular = true),
        red("Mourvèdre", "Monastrell", "Mataro", popular = true),
        red("Petit Verdot"),
        red("Carmenère"),
        red("Carignan", "Cariñena", "Mazuelo"),
        red("Cinsault", "Cinsaut"),
        red("Counoise"),
        red("Tannat"),
        red("Négrette"),
        red("Fer Servadou", "Mansois", "Braucol"),
        red("Duras"),
        red("Mondeuse"),
        red("Poulsard", "Ploussard"),
        red("Trousseau"),
        red("Pineau d'Aunis"),
        red("Grolleau"),
        red("Petite Sirah", "Durif"),
        red("Zinfandel"),
        red("Pinotage"),
        // ---- red: Italy ----
        red("Sangiovese", "Brunello", "Prugnolo Gentile", "Morellino", popular = true),
        red("Nebbiolo", "Spanna", "Chiavennasca", popular = true),
        red("Barbera", popular = true),
        red("Montepulciano", popular = true),
        red("Dolcetto"),
        red("Aglianico"),
        red("Nero d'Avola", "Calabrese"),
        red("Nerello Mascalese"),
        red("Nerello Cappuccio"),
        red("Frappato"),
        red("Primitivo"),
        red("Negroamaro"),
        red("Nero di Troia", "Uva di Troia"),
        red("Corvina"),
        red("Corvinone"),
        red("Rondinella"),
        red("Molinara"),
        red("Lagrein"),
        red("Teroldego"),
        red("Schiava", "Vernatsch"),
        red("Sagrantino"),
        red("Canaiolo"),
        red("Cesanese"),
        red("Refosco", "Refosco dal Peduncolo Rosso"),
        red("Croatina", "Bonarda"),
        red("Freisa"),
        red("Grignolino"),
        red("Ruchè"),
        red("Pelaverga"),
        red("Gaglioppo"),
        red("Lambrusco"),
        // ---- red: Spain, Portugal ----
        red("Tempranillo", "Tinta Roriz", "Aragonez", "Tinto Fino", "Cencibel", popular = true),
        red("Graciano"),
        red("Mencía"),
        red("Bobal"),
        red("Touriga Nacional"),
        red("Touriga Franca"),
        red("Tinta Barroca"),
        red("Baga"),
        red("Castelão"),
        red("Trincadeira"),
        // ---- red: Central Europe and elsewhere ----
        red("Zweigelt"),
        red("Blaufränkisch", "Lemberger", "Kékfrankos"),
        red("St. Laurent", "Sankt Laurent"),
        red("Dornfelder"),
        red("Xinomavro"),
        red("Agiorgitiko"),
        red("Saperavi"),
        // ---- white: France and international ----
        white("Chardonnay", popular = true),
        white("Sauvignon Blanc", popular = true),
        white("Riesling", popular = true),
        white("Chenin Blanc", "Steen", popular = true),
        white("Sémillon", "Semillon", popular = true),
        white("Viognier", popular = true),
        white("Gewürztraminer", "Traminer", popular = true),
        white("Muscat", "Moscato", "Muscat Blanc à Petits Grains", popular = true),
        white("Pinot Gris", "Pinot Grigio", "Grauburgunder", popular = true),
        white("Pinot Blanc", "Weißburgunder", "Weißer Burgunder", "Pinot Bianco", popular = true),
        white("Marsanne", popular = true),
        white("Roussanne", popular = true),
        white("Muscadelle"),
        white("Grenache Blanc", "Garnacha Blanca"),
        white("Clairette"),
        white("Bourboulenc"),
        white("Picpoul", "Piquepoul"),
        white("Ugni Blanc"),
        white("Melon de Bourgogne", "Muscadet"),
        white("Aligoté"),
        white("Savagnin"),
        white("Jacquère"),
        white("Altesse", "Roussette"),
        white("Petit Manseng"),
        white("Gros Manseng"),
        white("Courbu"),
        white("Mauzac"),
        white("Colombard"),
        white("Folle Blanche"),
        white("Sylvaner", "Silvaner"),
        white("Müller-Thurgau"),
        white("Grüner Veltliner", popular = true),
        white("Kerner"),
        white("Scheurebe"),
        white("Furmint"),
        white("Hárslevelű"),
        // ---- white: Italy ----
        white("Glera", "Prosecco", popular = true),
        white("Garganega", popular = true),
        white("Vermentino", "Rolle", "Pigato", "Favorita", popular = true),
        white("Trebbiano", "Trebbiano d'Abruzzo", popular = true),
        white("Cortese"),
        white("Arneis"),
        white("Erbaluce"),
        white("Timorasso"),
        white("Fiano"),
        white("Greco"),
        white("Falanghina"),
        white("Pecorino"),
        white("Passerina"),
        white("Verdicchio"),
        white("Vernaccia"),
        white("Carricante"),
        white("Biancolella"),
        white("Catarratto"),
        white("Grillo"),
        white("Inzolia", "Insolia"),
        white("Zibibbo"),
        white("Malvasia"),
        white("Friulano", "Tocai Friulano"),
        white("Ribolla Gialla"),
        // ---- white: Spain, Portugal, elsewhere ----
        white("Albariño", "Alvarinho", popular = true),
        white("Verdejo", popular = true),
        white("Godello"),
        white("Treixadura"),
        white("Macabeo", "Viura"),
        white("Xarel·lo"),
        white("Parellada"),
        white("Palomino"),
        white("Pedro Ximénez"),
        white("Arinto"),
        white("Encruzado"),
        white("Bical"),
        white("Fernão Pires"),
        white("Loureiro"),
        white("Assyrtiko"),
        white("Moschofilero"),
        white("Roditis"),
        white("Savatiano"),
        white("Malagousia"),
        white("Rkatsiteli"),
    )

    /**
     * The list offered to the person: the built-in grapes, then the ones added by hand
     * ([custom]) or already used on a wine ([used]) that the database does not know.
     */
    fun options(custom: Collection<String>, used: Collection<String>): List<GrapeDef> {
        val extra = ArrayList<GrapeDef>()
        (custom + used).forEach { raw ->
            val name = raw.trim()
            if (name.isEmpty()) return@forEach
            val known = builtIn.any { it.isNamed(name) } || extra.any { it.isNamed(name) }
            if (!known) extra.add(GrapeDef(name, null))
        }
        return (builtIn + extra).sortedWith(
            compareByDescending<GrapeDef> { it.popular }.thenBy { norm(it.name) }
        )
    }

    /** Grapes matching [query]; names starting with it come first. Everything when it is empty. */
    fun search(options: List<GrapeDef>, query: String): List<GrapeDef> {
        val q = norm(query)
        if (q.isEmpty()) return options
        return options.filter { it.matches(query) }
            .sortedBy { if (norm(it.name).startsWith(q)) 0 else 1 }
    }
}
