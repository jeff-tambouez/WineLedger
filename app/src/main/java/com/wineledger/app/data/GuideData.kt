package com.wineledger.app.data

import android.content.Context
import com.wineledger.app.logic.GuideWine
import com.wineledger.app.logic.guideKey
import org.json.JSONArray
import org.json.JSONObject

/** The embedded Veronelli 2026 guide (Italy only), read once from assets. */
data class Guide(
    val edition: String,
    val source: String,
    val wines: List<GuideWine>,
) {
    val regions: List<String> = wines.map { it.region }.filter { it.isNotEmpty() }.distinct().sorted()
    val vintages: List<String> = wines.map { it.vintage }.filter { it.isNotEmpty() }.distinct().sortedDescending()
    val byKey: Map<String, GuideWine> = wines.associateBy { it.key }

    companion object {
        val EMPTY = Guide("", "", emptyList())
    }
}

object GuideData {
    private fun styleOf(tipo: String): String = when (tipo.trim().lowercase()) {
        "rosso" -> "Red"
        "bianco" -> "White"
        "rosato" -> "Rosé"
        "spumante" -> "Sparkling"
        "dolce" -> "Dessert"
        else -> ""
    }

    fun load(context: Context): Guide {
        val text = context.assets.open("veronelli_2026.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
        val root = JSONObject(text)
        val rows: JSONArray = root.getJSONArray("vini")
        val seen = HashSet<String>()
        val list = ArrayList<GuideWine>(rows.length())
        for (i in 0 until rows.length()) {
            val r = rows.getJSONArray(i)
            fun s(idx: Int): String = if (idx < r.length() && !r.isNull(idx)) r.get(idx).toString() else ""
            val name = s(0)
            val vintage = s(1)
            val winery = s(2)
            var key = guideKey(name, vintage, winery)
            while (!seen.add(key)) key += "~"
            list.add(
                GuideWine(
                    key = key,
                    name = name,
                    vintage = vintage,
                    winery = winery,
                    region = s(3),
                    curator = s(4),
                    points = s(5).toDoubleOrNull()?.toInt() ?: 0,
                    awards = s(6).toList(),
                    style = styleOf(s(7)),
                    town = s(8),
                    province = s(9),
                    category = s(10),
                    note = s(11),
                )
            )
        }
        return Guide(root.optString("edizione"), root.optString("fonte"), list)
    }
}
