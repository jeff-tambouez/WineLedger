package com.wineledger.app.data

import com.wineledger.app.logic.csvLine
import com.wineledger.app.logic.detectDelimiter
import com.wineledger.app.logic.grapesOf
import com.wineledger.app.logic.joinGrapes
import com.wineledger.app.logic.normStyle
import com.wineledger.app.logic.parseCsv
import com.wineledger.app.logic.parseDoubleLoose
import com.wineledger.app.logic.parseIntLoose
import com.wineledger.app.logic.todayIso
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Thrown with a message meant to be shown to the person. */
class ImportException(message: String) : Exception(message)

// =====================================================================
// JSON helpers (org.json is part of Android)
// =====================================================================

private fun JSONObject.text(key: String): String =
    if (!has(key) || isNull(key)) "" else get(key).toString().trim()

private fun JSONObject.intOrNull(key: String): Int? = parseIntLoose(text(key))
private fun JSONObject.doubleOrNull(key: String): Double? = parseDoubleLoose(text(key))
private fun JSONObject.longOr(key: String, default: Long): Long = text(key).toLongOrNull() ?: default

private fun JSONObject.putN(key: String, v: Any?): JSONObject = put(key, v ?: JSONObject.NULL)

/** The bottle details kept on a tasting, as a nested "bottle" object (backups). */
private fun JSONObject.putSnap(s: BottleSnapshot?): JSONObject = putN(
    "bottle",
    s?.let {
        JSONObject().putN("format", it.format).putN("price", it.price).putN("readyFrom", it.readyFrom)
            .putN("drinkBy", it.drinkBy).putN("supplier", it.supplier).putN("purchaseDate", it.purchaseDate)
    },
)

private fun JSONObject.snap(): BottleSnapshot? {
    val o = optJSONObject("bottle") ?: return null
    return BottleSnapshot(
        o.doubleOrNull("format"), o.doubleOrNull("price"), o.intOrNull("readyFrom"), o.intOrNull("drinkBy"),
        o.text("supplier").ifEmpty { null }, o.text("purchaseDate").ifEmpty { null },
    )
}

/** Bottle details written flat in a ledger JSON entry (names of the former HTML page). */
private fun flatSnap(o: JSONObject): BottleSnapshot? = BottleSnapshot(
    o.doubleOrNull("formato"), o.doubleOrNull("prezzo"), o.intOrNull("bereDal"), o.intOrNull("bereEntro"),
    o.text("fornitore").ifEmpty { null }, o.text("dataAcquisto").ifEmpty { null },
).takeIf { it != BottleSnapshot() }

private fun grapesFrom(o: JSONObject, key: String): String {
    if (!o.has(key) || o.isNull(key)) return ""
    val v = o.get(key)
    return if (v is JSONArray) {
        joinGrapes(grapesOf((0 until v.length()).joinToString(",") { v.get(it).toString() }))
    } else {
        joinGrapes(grapesOf(v.toString()))
    }
}

// =====================================================================
// Reading files exported by the former HTML page, or CSV files
// =====================================================================

object LedgerImport {

    /** Read [text] according to the file name extension. */
    fun parse(fileName: String, text: String): ImportedRecords {
        val name = fileName.lowercase()
        val rec = when {
            name.endsWith(".html") || name.endsWith(".htm") -> parseHtml(text)
            name.endsWith(".json") -> parseJson(text)
            name.endsWith(".csv") -> parseCsvText(text)
            else -> {
                val t = text.trimStart('﻿', ' ', '\n', '\r', '\t')
                when {
                    t.startsWith("<") -> parseHtml(text)
                    t.startsWith("[") || t.startsWith("{") -> parseJson(text)
                    else -> parseCsvText(text)
                }
            }
        }
        if (rec.isEmpty) throw ImportException("No valid row found in this file.")
        return rec
    }

    private fun parseHtml(text: String): ImportedRecords {
        val m = Regex("<script[^>]*id=\"datiRegistro\"[^>]*>(.*?)</script>", RegexOption.DOT_MATCHES_ALL).find(text)
            ?: throw ImportException("This HTML file does not contain a ledger archive.")
        return parseJson(m.groupValues[1].trim())
    }

    private fun parseJson(text: String): ImportedRecords {
        val value = try {
            org.json.JSONTokener(text.trimStart('﻿')).nextValue()
        } catch (e: Exception) {
            throw ImportException("This file is not valid JSON.")
        }
        return when (value) {
            is JSONArray -> {
                val objs = (0 until value.length()).mapNotNull { value.optJSONObject(it) }
                val cellar = objs.firstOrNull()?.let { it.has("quantita") || it.has("posizione") } == true
                if (cellar) ImportedRecords(bottles = objs.mapNotNull { bottleFrom(it) })
                else ImportedRecords(tastings = objs.mapNotNull { tastingFrom(it) })
            }
            is JSONObject -> {
                if (value.optString("app") == "WineLedger") {
                    throw ImportException("This is a full backup. Use \"Restore a backup\" with the .zip file.")
                }
                fun list(k: String): List<JSONObject> {
                    val a = value.optJSONArray(k) ?: return emptyList()
                    return (0 until a.length()).mapNotNull { a.optJSONObject(it) }
                }
                val wishes = value.optJSONArray("veronelliWish")
                ImportedRecords(
                    tastings = list("entries").mapNotNull { tastingFrom(it) },
                    bottles = list("cantina").mapNotNull { bottleFrom(it) },
                    openings = list("cantinaLog").mapNotNull { openingFrom(it) },
                    wishKeys = if (wishes == null) emptyList() else (0 until wishes.length()).map { wishes.get(it).toString() },
                )
            }
            else -> throw ImportException("Unexpected JSON content.")
        }
    }

    private fun wineFrom(o: JSONObject): WineInfo? {
        val producer = o.text("produttore")
        val wine = o.text("vino")
        if (producer.isEmpty() || wine.isEmpty()) return null
        return WineInfo(
            producer = producer, name = wine, style = normStyle(o.text("tipologia")),
            country = o.text("paese"), region = o.text("regione"), grapes = grapesFrom(o, "uve"),
        )
    }

    private fun tastingFrom(o: JSONObject): ImportedTasting? {
        val wine = wineFrom(o) ?: return null
        val score = o.doubleOrNull("score") ?: return null
        return ImportedTasting(
            wine, o.intOrNull("anno"), score, o.text("note"), o.intOrNull("annata"), flatSnap(o),
        )
    }

    private fun bottleFrom(o: JSONObject): ImportedBottle? {
        val wine = wineFrom(o) ?: return null
        return ImportedBottle(
            wine = wine,
            vintage = o.intOrNull("annata"),
            format = o.doubleOrNull("formato") ?: 0.75,
            quantity = (o.intOrNull("quantita") ?: 1).coerceAtLeast(1),
            price = o.doubleOrNull("prezzo"),
            readyFrom = o.intOrNull("bereDal"),
            drinkBy = o.intOrNull("bereEntro"),
            supplier = o.text("fornitore"),
            purchaseDate = o.text("dataAcquisto"),
            notes = o.text("note"),
        )
    }

    private fun openingFrom(o: JSONObject): OpeningLog? {
        val wine = o.text("vino")
        if (wine.isEmpty()) return null
        return OpeningLog(
            date = o.text("data").ifEmpty { todayIso() },
            producer = o.text("produttore"), wine = wine,
            vintage = o.intOrNull("annata"), price = o.doubleOrNull("prezzo"), score = o.doubleOrNull("score"),
        )
    }

    // ---- CSV ----

    private val PRODUCER = setOf("producer", "produttore")
    private val WINE = setOf("wine", "vino", "winename", "nomevino")
    private val COUNTRY = setOf("country", "paese")
    private val REGION = setOf("region", "regione")
    private val STYLE = setOf("winestyle", "style", "type", "tipologia")
    private val GRAPES = setOf("grapes", "grape", "uve", "uva")
    private val YEAR = setOf("tastingyear", "year", "anno")
    private val SCORE = setOf("score", "punteggio")
    private val NOTES = setOf("notes", "note", "nota")
    private val VINTAGE = setOf("vintage", "annata", "vendemmia")
    private val FORMAT = setOf("format", "formato")
    private val QUANTITY = setOf("quantity", "qty", "qta", "quantita", "bottles")
    private val PLACE = setOf("place", "posizione", "livello", "spazio")
    private val PRICE = setOf("price", "prezzo")
    private val READY = setOf("drinkfrom", "readyfrom", "beredal")
    private val DRINKBY = setOf("drinkby", "bereentro", "beredentro")
    private val SUPPLIER = setOf("boughtfrom", "supplier", "fornitore")
    private val PURCHASE = setOf("purchasedate", "dataacquisto")

    private fun headerKey(h: String): String = h.lowercase().filter { it.isLetter() }

    private fun parseCsvText(text: String): ImportedRecords {
        val rows = parseCsv(text, detectDelimiter(text))
        if (rows.size < 2) throw ImportException("The CSV file contains no data rows.")
        val keys = rows[0].map { headerKey(it) }
        fun col(names: Set<String>): Int = keys.indexOfFirst { it in names }
        val cProducer = col(PRODUCER)
        val cWine = col(WINE)
        if (cProducer < 0 || cWine < 0) {
            throw ImportException("Unrecognised headings: at least \"Producer\" and \"Wine\" are needed.")
        }
        val cCountry = col(COUNTRY)
        val cRegion = col(REGION)
        val cStyle = col(STYLE)
        val cGrapes = col(GRAPES)
        val cYear = col(YEAR)
        val cScore = col(SCORE)
        val cNotes = col(NOTES)
        val cVintage = col(VINTAGE)
        val cFormat = col(FORMAT)
        val cQty = col(QUANTITY)
        val cPlace = col(PLACE)
        val cPrice = col(PRICE)
        val cReady = col(READY)
        val cBy = col(DRINKBY)
        val cSupplier = col(SUPPLIER)
        val cPurchase = col(PURCHASE)
        // a "Vintage" column alone does not make a cellar file: tasting exports have one too
        // tasting exports carry vintage, format, price, drinking window and so on too: only
        // Quantity or Place, or the lack of any score or year, mark a cellar file
        val isCellar = cQty >= 0 || cPlace >= 0 || (cScore < 0 && cYear < 0)

        fun at(r: List<String>, c: Int): String = if (c in r.indices) r[c].trim() else ""
        fun wineOf(r: List<String>): WineInfo? {
            val p = at(r, cProducer)
            val w = at(r, cWine)
            if (p.isEmpty() || w.isEmpty()) return null
            return WineInfo(
                p, w, normStyle(at(r, cStyle)), at(r, cCountry), at(r, cRegion),
                joinGrapes(grapesOf(at(r, cGrapes))),
            )
        }

        val body = rows.drop(1)
        return if (isCellar) {
            ImportedRecords(
                bottles = body.mapNotNull { r ->
                    val wine = wineOf(r) ?: return@mapNotNull null
                    ImportedBottle(
                        wine = wine,
                        vintage = parseIntLoose(at(r, cVintage)),
                        format = parseDoubleLoose(at(r, cFormat)) ?: 0.75,
                        quantity = (parseIntLoose(at(r, cQty)) ?: 1).coerceAtLeast(1),
                        price = parseDoubleLoose(at(r, cPrice)),
                        readyFrom = parseIntLoose(at(r, cReady)),
                        drinkBy = parseIntLoose(at(r, cBy)),
                        supplier = at(r, cSupplier),
                        purchaseDate = at(r, cPurchase),
                        notes = at(r, cNotes),
                    )
                }
            )
        } else {
            ImportedRecords(
                tastings = body.mapNotNull { r ->
                    val wine = wineOf(r) ?: return@mapNotNull null
                    val score = parseDoubleLoose(at(r, cScore)) ?: return@mapNotNull null
                    val snap = BottleSnapshot(
                        parseDoubleLoose(at(r, cFormat)), parseDoubleLoose(at(r, cPrice)),
                        parseIntLoose(at(r, cReady)), parseIntLoose(at(r, cBy)),
                        at(r, cSupplier).ifEmpty { null }, at(r, cPurchase).ifEmpty { null },
                    ).takeIf { it != BottleSnapshot() }
                    ImportedTasting(
                        wine, parseIntLoose(at(r, cYear)), score, at(r, cNotes), parseIntLoose(at(r, cVintage)), snap,
                    )
                }
            )
        }
    }
}

// =====================================================================
// Exports
// =====================================================================

object LedgerExport {
    private const val BOM = "﻿"
    private const val EOL = "\r\n"

    private fun num(d: Double): String = if (d % 1.0 == 0.0) d.toLong().toString() else d.toString()

    /** Same columns as the former HTML page, plus Notes. */
    fun tastingsCsv(data: AppData, profileId: Long): String {
        val sb = StringBuilder(BOM)
        sb.append(
            "Producer;Wine;Country;Region;Wine style;Grapes;Tasting year;Score;Notes;Vintage;" +
                "Format;Price;Drink from;Drink by;Bought from;Purchase date"
        ).append(EOL)
        data.tastings.filter { it.profileId == profileId }
            .mapNotNull { t -> data.wines[t.wineId]?.let { it to t } }
            .sortedWith(compareBy({ it.first.producer.lowercase() }, { it.first.name.lowercase() }, { it.second.year }))
            .forEach { (w, t) ->
                sb.append(
                    csvLine(
                        w.producer, w.name, w.country, w.region, w.style, w.grapes, t.year, num(t.score), t.notes,
                        t.vintage ?: "", t.bottle?.format?.let { num(it) } ?: "", t.bottle?.price?.let { num(it) } ?: "",
                        t.bottle?.readyFrom ?: "", t.bottle?.drinkBy ?: "", t.bottle?.supplier ?: "",
                        t.bottle?.purchaseDate ?: "",
                    )
                ).append(EOL)
            }
        return sb.toString()
    }

    /** The "Place" column is kept empty so the file still opens in the former HTML page. */
    fun cellarCsv(data: AppData): String {
        val sb = StringBuilder(BOM)
        sb.append(
            "Producer;Wine;Vintage;Country;Region;Style;Grapes;Format;Quantity;Place;Price;" +
                "Drink from;Drink by;Bought from;Purchase date;Notes"
        ).append(EOL)
        data.bottles.mapNotNull { b -> data.wines[b.wineId]?.let { it to b } }
            .sortedWith(compareBy({ it.first.producer.lowercase() }, { it.first.name.lowercase() }))
            .forEach { (w, b) ->
                sb.append(
                    csvLine(
                        w.producer, w.name, b.vintage ?: "", w.country, w.region, w.style, w.grapes,
                        num(b.format), b.quantity, "", b.price?.let { num(it) } ?: "",
                        b.readyFrom ?: "", b.drinkBy ?: "", b.supplier, b.purchaseDate, b.notes,
                    )
                ).append(EOL)
            }
        return sb.toString()
    }

    /** JSON in the layout of the former HTML page ("datiRegistro"), for one profile. */
    fun ledgerJson(data: AppData, profileId: Long): String {
        val entries = JSONArray()
        data.tastings.filter { it.profileId == profileId }.forEach { t ->
            val w = data.wines[t.wineId] ?: return@forEach
            entries.put(
                JSONObject()
                    .put("id", "w${t.id}")
                    .put("produttore", w.producer).put("vino", w.name)
                    .put("paese", w.country).put("regione", w.region).put("tipologia", w.style)
                    .put("uve", JSONArray(grapesOf(w.grapes)))
                    .put("anno", t.year).put("score", t.score).put("annata", t.vintage ?: "")
                    .put("note", t.notes)
                    .put("formato", t.bottle?.format?.let { num(it) } ?: "").put("prezzo", t.bottle?.price ?: "")
                    .put("bereDal", t.bottle?.readyFrom ?: "").put("bereEntro", t.bottle?.drinkBy ?: "")
                    .put("fornitore", t.bottle?.supplier ?: "").put("dataAcquisto", t.bottle?.purchaseDate ?: "")
                    .put("inserito", java.time.Instant.ofEpochMilli(t.createdAt).toString().take(10))
            )
        }
        val cellar = JSONArray()
        data.bottles.forEach { b ->
            val w = data.wines[b.wineId] ?: return@forEach
            cellar.put(
                JSONObject()
                    .put("id", "b${b.id}")
                    .put("produttore", w.producer).put("vino", w.name)
                    .put("annata", b.vintage ?: "")
                    .put("paese", w.country).put("regione", w.region).put("tipologia", w.style)
                    .put("uve", JSONArray(grapesOf(w.grapes)))
                    .put("formato", num(b.format)).put("quantita", b.quantity)
                    .put("posizione", "Storage")
                    .put("prezzo", b.price ?: "")
                    .put("bereDal", b.readyFrom ?: "").put("bereEntro", b.drinkBy ?: "")
                    .put("fornitore", b.supplier).put("dataAcquisto", b.purchaseDate).put("note", b.notes)
                    .put("inserito", java.time.Instant.ofEpochMilli(b.addedAt).toString().take(10))
            )
        }
        val log = JSONArray()
        data.openings.forEach { o ->
            log.put(
                JSONObject()
                    .put("data", o.date).put("vino", o.wine).put("produttore", o.producer)
                    .put("annata", o.vintage ?: "").put("prezzo", o.price ?: JSONObject.NULL)
                    .put("score", o.score ?: JSONObject.NULL)
            )
        }
        val wishes = JSONArray()
        data.wishlist.filter { it.profileId == profileId }.forEach { wishes.put(it.guideKey) }
        return JSONObject()
            .put("savedAt", java.time.Instant.now().toString())
            .put("entries", entries).put("cantina", cellar)
            .put("cantinaMeta", JSONObject()).put("cantinaLog", log)
            .put("veronelliWish", wishes)
            .toString(2)
    }
}

// =====================================================================
// Full backup: one ZIP with backup.json and the photos
// =====================================================================

object Backup {
    private const val JSON_ENTRY = "backup.json"
    private const val PHOTO_DIR = "photos/"
    private const val VERSION = 1

    fun toJson(s: Snapshot): String {
        val root = JSONObject().put("app", "WineLedger").put("version", VERSION)
            .put("exportedAt", java.time.Instant.now().toString())
        root.put("profiles", JSONArray().also { a ->
            s.profiles.forEach { a.put(JSONObject().put("id", it.id).put("name", it.name).put("accent", it.accent)) }
        })
        root.put("wines", JSONArray().also { a ->
            s.wines.forEach {
                a.put(
                    JSONObject().put("id", it.id).put("wineKey", it.wineKey).put("producer", it.producer)
                        .put("name", it.name).put("country", it.country).put("region", it.region)
                        .put("style", it.style).put("grapes", it.grapes).putN("photo", it.photo)
                )
            }
        })
        root.put("bottles", JSONArray().also { a ->
            s.bottles.forEach {
                a.put(
                    JSONObject().put("id", it.id).put("wineId", it.wineId).putN("vintage", it.vintage)
                        .put("format", it.format).put("quantity", it.quantity).putN("price", it.price)
                        .putN("readyFrom", it.readyFrom).putN("drinkBy", it.drinkBy)
                        .put("supplier", it.supplier).put("purchaseDate", it.purchaseDate)
                        .put("notes", it.notes).putN("photo", it.photo).put("addedAt", it.addedAt)
                )
            }
        })
        root.put("tastings", JSONArray().also { a ->
            s.tastings.forEach {
                a.put(
                    JSONObject().put("id", it.id).put("wineId", it.wineId).put("profileId", it.profileId)
                        .put("year", it.year).put("score", it.score).put("notes", it.notes)
                        .putN("photo", it.photo).put("createdAt", it.createdAt).putN("vintage", it.vintage)
                        .putSnap(it.bottle)
                )
            }
        })
        root.put("openings", JSONArray().also { a ->
            s.openings.forEach {
                a.put(
                    JSONObject().put("id", it.id).put("date", it.date).put("producer", it.producer)
                        .put("wine", it.wine).putN("vintage", it.vintage).putN("price", it.price)
                        .putN("score", it.score).putN("notes", it.notes).putSnap(it.bottle)
                )
            }
        })
        root.put("wishlist", JSONArray().also { a ->
            s.wishlist.forEach { a.put(JSONObject().put("profileId", it.profileId).put("guideKey", it.guideKey)) }
        })
        root.put("invitations", JSONArray().also { a ->
            s.invitations.forEach {
                a.put(
                    JSONObject().put("id", it.id).put("wineId", it.wineId).put("profileId", it.profileId)
                        .put("fromProfileId", it.fromProfileId).put("year", it.year)
                        .putN("vintage", it.vintage).put("stage", it.stage).put("createdAt", it.createdAt)
                        .putN("notes", it.notes).putSnap(it.bottle)
                )
            }
        })
        return root.toString()
    }

    fun fromJson(text: String): Snapshot {
        val root = try {
            JSONObject(text)
        } catch (e: Exception) {
            throw ImportException("The backup file is damaged.")
        }
        if (root.optString("app") != "WineLedger") throw ImportException("This is not a Wine Ledger backup.")
        fun list(k: String): List<JSONObject> {
            val a = root.optJSONArray(k) ?: return emptyList()
            return (0 until a.length()).mapNotNull { a.optJSONObject(it) }
        }
        fun JSONObject.photo(): String? = text("photo").ifEmpty { null }
        return Snapshot(
            profiles = list("profiles").map { Profile(it.longOr("id", 0), it.text("name"), it.intOrNull("accent") ?: 0) },
            wines = list("wines").map {
                Wine(
                    it.longOr("id", 0), it.text("wineKey"), it.text("producer"), it.text("name"),
                    it.text("country"), it.text("region"), it.text("style"), it.text("grapes"), it.photo(),
                )
            },
            bottles = list("bottles").map {
                Bottle(
                    it.longOr("id", 0), it.longOr("wineId", 0), it.intOrNull("vintage"),
                    it.doubleOrNull("format") ?: 0.75, it.intOrNull("quantity") ?: 1, it.doubleOrNull("price"),
                    it.intOrNull("readyFrom"), it.intOrNull("drinkBy"), it.text("supplier"),
                    it.text("purchaseDate"), it.text("notes"), it.photo(), it.longOr("addedAt", 0),
                )
            },
            tastings = list("tastings").map {
                Tasting(
                    it.longOr("id", 0), it.longOr("wineId", 0), it.longOr("profileId", 0),
                    it.intOrNull("year") ?: 0, it.doubleOrNull("score") ?: 0.0, it.text("notes"),
                    it.photo(), it.longOr("createdAt", 0), it.intOrNull("vintage"), it.snap(),
                )
            },
            openings = list("openings").map {
                OpeningLog(
                    it.longOr("id", 0), it.text("date"), it.text("producer"), it.text("wine"),
                    it.intOrNull("vintage"), it.doubleOrNull("price"), it.doubleOrNull("score"),
                    it.text("notes").ifEmpty { null }, it.snap(),
                )
            },
            wishlist = list("wishlist").map { WishlistItem(it.longOr("profileId", 0), it.text("guideKey")) },
            invitations = list("invitations").map {
                Invitation(
                    it.longOr("id", 0), it.longOr("wineId", 0), it.longOr("profileId", 0),
                    it.longOr("fromProfileId", 0), it.intOrNull("year") ?: 0, it.intOrNull("vintage"),
                    it.intOrNull("stage") ?: 0, it.longOr("createdAt", 0),
                    it.text("notes").ifEmpty { null }, it.snap(),
                )
            },
        )
    }

    /** Writes backup.json and every referenced photo (full size, thumbnails are rebuilt on restore). */
    fun write(out: OutputStream, snapshot: Snapshot, photos: PhotoStore) {
        ZipOutputStream(out.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(JSON_ENTRY))
            zip.write(toJson(snapshot).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            val names = LinkedHashSet<String>()
            snapshot.wines.mapNotNullTo(names) { it.photo }
            snapshot.bottles.mapNotNullTo(names) { it.photo }
            snapshot.tastings.mapNotNullTo(names) { it.photo }
            for (n in names) {
                val f = photos.file(n)
                if (!f.exists()) continue
                zip.putNextEntry(ZipEntry(PHOTO_DIR + n))
                f.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    /** First pass: find and parse backup.json. */
    fun readSnapshot(input: InputStream): Snapshot {
        ZipInputStream(input.buffered()).use { zip ->
            var e = zip.nextEntry
            while (e != null) {
                if (e.name == JSON_ENTRY) {
                    return fromJson(zip.readBytes().toString(Charsets.UTF_8))
                }
                e = zip.nextEntry
            }
        }
        throw ImportException("This ZIP file is not a Wine Ledger backup.")
    }

    /** Second pass: extract photos into the private photo store. */
    fun restorePhotos(input: InputStream, photos: PhotoStore) {
        ZipInputStream(input.buffered()).use { zip ->
            var e = zip.nextEntry
            while (e != null) {
                if (!e.isDirectory && e.name.startsWith(PHOTO_DIR)) {
                    val name = e.name.removePrefix(PHOTO_DIR)
                    if (name.isNotEmpty() && !name.contains('/') && !name.contains("..")) {
                        photos.restore(name, zip.readBytes())
                    }
                }
                e = zip.nextEntry
            }
        }
    }
}
