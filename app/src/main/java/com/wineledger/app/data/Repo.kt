package com.wineledger.app.data

import androidx.room.withTransaction
import com.wineledger.app.logic.currentYear
import com.wineledger.app.logic.todayIso
import com.wineledger.app.logic.wineKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext

/** Everything the screens need, rebuilt whenever a table changes. */
data class AppData(
    val profiles: List<Profile> = emptyList(),
    val wines: Map<Long, Wine> = emptyMap(),
    val bottles: List<Bottle> = emptyList(),
    val tastings: List<Tasting> = emptyList(),
    val openings: List<OpeningLog> = emptyList(),
    val wishlist: List<WishlistItem> = emptyList(),
    val invitations: List<Invitation> = emptyList(),
    val loaded: Boolean = false,
)

/** Reverts an operation. Returned by operations that the UI offers to undo. */
typealias Undo = suspend () -> Unit

/** Wine identity and descriptive fields as typed in a form. */
data class WineInfo(
    val producer: String,
    val name: String,
    val style: String,
    val country: String = "",
    val region: String = "",
    val grapes: String = "",
)

data class BottleInput(
    val id: Long = 0,
    val wine: WineInfo,
    val vintage: Int? = null,
    val quantity: Int = 1,
    val format: Double = 0.75,
    val price: Double? = null,
    val readyFrom: Int? = null,
    val drinkBy: Int? = null,
    val supplier: String = "",
    val purchaseDate: String = "",
    val notes: String = "",
    val photo: String? = null,
)

data class TastingInput(
    val id: Long = 0,
    val profileId: Long,
    val wine: WineInfo,
    val year: Int,
    val score: Double,
    val notes: String = "",
    val photo: String? = null,
)

/** One person who drank an opened bottle; a null score means "rate it later". */
data class Taster(val profileId: Long, val score: Double?)

data class Saved(val id: Long, val undo: Undo?)

/** Whole database content, used by backups. */
data class Snapshot(
    val profiles: List<Profile>,
    val wines: List<Wine>,
    val bottles: List<Bottle>,
    val tastings: List<Tasting>,
    val openings: List<OpeningLog>,
    val wishlist: List<WishlistItem>,
    val invitations: List<Invitation>,
)

class Repo(private val db: AppDb, val photos: PhotoStore) {
    private val dao = db.dao()

    /** Photos imported by a form that has not been saved yet. They must survive cleanup. */
    private val pending = HashSet<String>()

    private val part1 = combine(dao.observeProfiles(), dao.observeWines(), dao.observeBottles()) { p, w, b ->
        Triple(p, w, b)
    }
    private val part2 = combine(
        dao.observeTastings(), dao.observeOpenings(), dao.observeWishlist(), dao.observeInvitations(),
    ) { t, o, w, i -> arrayOf<Any>(t, o, w, i) }

    @Suppress("UNCHECKED_CAST")
    val data: Flow<AppData> = combine(part1, part2) { a, b ->
        AppData(
            profiles = a.first,
            wines = a.second.associateBy { it.id },
            bottles = a.third,
            tastings = b[0] as List<Tasting>,
            openings = b[1] as List<OpeningLog>,
            wishlist = b[2] as List<WishlistItem>,
            invitations = b[3] as List<Invitation>,
            loaded = true,
        )
    }

    // ------------------------------------------------------------------
    // Start-up
    // ------------------------------------------------------------------

    suspend fun ensureDefaults() {
        if (dao.profileCount() == 0) {
            dao.insertProfile(Profile(name = "Jeff", accent = ACCENT_GREEN))
            dao.insertProfile(Profile(name = "Vanessa", accent = ACCENT_BURGUNDY))
        }
    }

    /** Remove wines nobody refers to any more and photos no row uses. */
    suspend fun cleanup() = withContext(Dispatchers.IO) {
        dao.deleteOrphanWines()
        val used = HashSet<String>()
        used.addAll(dao.winePhotos())
        used.addAll(dao.bottlePhotos())
        used.addAll(dao.tastingPhotos())
        synchronized(pending) { used.addAll(pending) }
        photos.prune(used)
    }

    // ------------------------------------------------------------------
    // Photos
    // ------------------------------------------------------------------

    suspend fun importPhotoUri(uri: android.net.Uri): String? = withContext(Dispatchers.IO) {
        photos.importUri(uri)?.also { synchronized(pending) { pending.add(it) } }
    }

    suspend fun importPhotoFile(file: java.io.File): String? = withContext(Dispatchers.IO) {
        photos.importFile(file)?.also { synchronized(pending) { pending.add(it) } }
    }

    /** A form was closed without saving: forget (and delete) its unsaved photo. */
    suspend fun discardPhoto(name: String?) {
        if (name == null) return
        val wasPending = synchronized(pending) { pending.remove(name) }
        if (!wasPending) return
        withContext(Dispatchers.IO) {
            val inUse = dao.winePhotos().contains(name) || dao.bottlePhotos().contains(name) ||
                dao.tastingPhotos().contains(name)
            if (!inUse) photos.delete(name)
        }
    }

    private fun released(name: String?) {
        if (name != null) synchronized(pending) { pending.remove(name) }
    }

    // ------------------------------------------------------------------
    // Wines
    // ------------------------------------------------------------------

    /**
     * Finds the wine with this producer and name or creates it.
     * With [overwrite] non-blank values replace the stored ones, otherwise they only fill blanks.
     */
    private suspend fun resolveWine(info: WineInfo, photo: String?, overwrite: Boolean): Wine {
        val producer = info.producer.trim()
        val name = info.name.trim()
        val key = wineKey(producer, name)
        val existing = dao.wineByKey(key)
        if (existing == null) {
            val w = Wine(
                wineKey = key, producer = producer, name = name,
                country = info.country.trim(), region = info.region.trim(),
                style = info.style.trim(), grapes = info.grapes.trim(), photo = photo,
            )
            return w.copy(id = dao.insertWine(w))
        }
        fun pick(old: String, new: String): String {
            val n = new.trim()
            return if (n.isEmpty()) old else if (overwrite || old.isEmpty()) n else old
        }
        val merged = existing.copy(
            country = pick(existing.country, info.country),
            region = pick(existing.region, info.region),
            style = pick(existing.style, info.style),
            grapes = pick(existing.grapes, info.grapes),
            photo = photo ?: existing.photo,
        )
        if (merged != existing) dao.updateWine(merged)
        return merged
    }

    /**
     * Applies an edit of the wine's identity. A simple rename updates the wine in place
     * (so typo fixes reach every tasting and bottle); renaming into another existing wine
     * moves the edited row there.
     */
    private suspend fun applyWineEdit(oldWineId: Long, info: WineInfo, photo: String?): Long {
        val producer = info.producer.trim()
        val name = info.name.trim()
        val key = wineKey(producer, name)
        val old = dao.wine(oldWineId)
        val target = dao.wineByKey(key)
        if (old != null && (target == null || target.id == old.id)) {
            dao.updateWine(
                old.copy(
                    wineKey = key, producer = producer, name = name,
                    country = info.country.trim(), region = info.region.trim(),
                    style = info.style.trim().ifEmpty { old.style },
                    grapes = info.grapes.trim(), photo = photo ?: old.photo,
                )
            )
            return old.id
        }
        return resolveWine(info, photo, overwrite = true).id
    }

    // ------------------------------------------------------------------
    // Bottles
    // ------------------------------------------------------------------

    suspend fun saveBottle(input: BottleInput): Saved = db.withTransaction {
        val now = System.currentTimeMillis()
        if (input.id != 0L) {
            val old = dao.bottle(input.id)
            if (old != null) {
                val wineId = applyWineEdit(old.wineId, input.wine, input.photo)
                dao.updateBottle(
                    old.copy(
                        wineId = wineId, vintage = input.vintage, quantity = input.quantity.coerceAtLeast(1),
                        format = input.format, price = input.price, readyFrom = input.readyFrom,
                        drinkBy = input.drinkBy, supplier = input.supplier.trim(),
                        purchaseDate = input.purchaseDate, notes = input.notes.trim(),
                        photo = input.photo,
                    )
                )
                dao.deleteOrphanWines()
                released(input.photo)
                return@withTransaction Saved(old.id, null)
            }
        }
        val wine = resolveWine(input.wine, input.photo, overwrite = true)
        val qty = input.quantity.coerceAtLeast(1)
        val same = dao.allBottles().firstOrNull {
            it.wineId == wine.id && it.vintage == input.vintage && it.format == input.format
        }
        released(input.photo)
        if (same != null) {
            val merged = same.copy(
                quantity = same.quantity + qty,
                price = same.price ?: input.price,
                readyFrom = same.readyFrom ?: input.readyFrom,
                drinkBy = same.drinkBy ?: input.drinkBy,
                supplier = same.supplier.ifEmpty { input.supplier.trim() },
                purchaseDate = same.purchaseDate.ifEmpty { input.purchaseDate },
                notes = if (same.notes.isEmpty()) input.notes.trim() else same.notes,
                photo = same.photo ?: input.photo,
            )
            dao.updateBottle(merged)
            val undo: Undo = { db.withTransaction { dao.updateBottle(same); dao.deleteOrphanWines() } }
            return@withTransaction Saved(same.id, undo)
        }
        val id = dao.insertBottle(
            Bottle(
                wineId = wine.id, vintage = input.vintage, format = input.format, quantity = qty,
                price = input.price, readyFrom = input.readyFrom, drinkBy = input.drinkBy,
                supplier = input.supplier.trim(), purchaseDate = input.purchaseDate,
                notes = input.notes.trim(), photo = input.photo, addedAt = now,
            )
        )
        val undo: Undo = { db.withTransaction { dao.deleteBottle(id); dao.deleteOrphanWines() } }
        Saved(id, undo)
    }

    suspend fun deleteBottle(id: Long): Undo? = db.withTransaction {
        val b = dao.bottle(id) ?: return@withTransaction null
        val w = dao.wine(b.wineId)
        dao.deleteBottle(id)
        val undo: Undo = {
            db.withTransaction {
                if (w != null && dao.wine(w.id) == null) dao.insertWine(w)
                dao.insertBottle(b)
            }
        }
        undo
    }

    /**
     * Opens one bottle: removes it from the cellar, logs the opening, records a tasting for every
     * taster who gave a score and prepares the invitation cards for the others.
     */
    suspend fun openBottle(bottleId: Long, tasters: List<Taster>, profiles: List<Profile>): Undo? =
        db.withTransaction {
            val b = dao.bottle(bottleId) ?: return@withTransaction null
            val wine = dao.wine(b.wineId) ?: return@withTransaction null
            val now = System.currentTimeMillis()
            val year = currentYear()

            if (b.quantity > 1) dao.updateBottle(b.copy(quantity = b.quantity - 1)) else dao.deleteBottle(b.id)

            val scores = tasters.mapNotNull { it.score }
            val openingId = dao.insertOpening(
                OpeningLog(
                    date = todayIso(), producer = wine.producer, wine = wine.name, vintage = b.vintage,
                    price = b.price,
                    score = if (scores.isEmpty()) null else Math.round(scores.average() * 10) / 10.0,
                )
            )

            val tastingIds = ArrayList<Long>()
            val invitationIds = ArrayList<Long>()
            var wineBefore: Wine? = null
            for (t in tasters) {
                if (t.score != null) {
                    tastingIds += dao.insertTasting(
                        Tasting(
                            wineId = wine.id, profileId = t.profileId, year = year,
                            score = t.score, photo = b.photo, createdAt = now,
                        )
                    )
                    dao.findInvitation(wine.id, t.profileId, year)?.let { dao.deleteInvitation(it.id) }
                } else if (dao.findInvitation(wine.id, t.profileId, year) == null) {
                    invitationIds += dao.insertInvitation(
                        Invitation(
                            wineId = wine.id, profileId = t.profileId, fromProfileId = t.profileId,
                            year = year, vintage = b.vintage, stage = 1, createdAt = now,
                        )
                    )
                }
            }
            if (b.photo != null && wine.photo == null && tasters.isNotEmpty()) {
                wineBefore = wine
                dao.updateWine(wine.copy(photo = b.photo))
            }
            if (tasters.isNotEmpty()) {
                val first = tasters.first().profileId
                val tasterIds = tasters.map { it.profileId }.toSet()
                for (p in profiles) {
                    if (p.id in tasterIds) continue
                    if (dao.findInvitation(wine.id, p.id, year) != null) continue
                    invitationIds += dao.insertInvitation(
                        Invitation(
                            wineId = wine.id, profileId = p.id, fromProfileId = first,
                            year = year, vintage = b.vintage, stage = 0, createdAt = now,
                        )
                    )
                }
            }

            val undo: Undo = {
                db.withTransaction {
                    tastingIds.forEach { dao.deleteTasting(it) }
                    invitationIds.forEach { dao.deleteInvitation(it) }
                    dao.deleteOpening(openingId)
                    if (dao.bottle(b.id) != null) dao.updateBottle(b) else {
                        if (dao.wine(wine.id) == null) dao.insertWine(wine)
                        dao.insertBottle(b)
                    }
                    wineBefore?.let { dao.updateWine(it) }
                }
            }
            undo
        }

    // ------------------------------------------------------------------
    // Tastings
    // ------------------------------------------------------------------

    suspend fun saveTasting(input: TastingInput): Saved = db.withTransaction {
        val now = System.currentTimeMillis()
        if (input.id != 0L) {
            val old = dao.tasting(input.id)
            if (old != null) {
                val wineId = applyWineEdit(old.wineId, input.wine, input.photo)
                dao.updateTasting(
                    old.copy(
                        wineId = wineId, year = input.year, score = input.score,
                        notes = input.notes.trim(), photo = input.photo,
                    )
                )
                dao.deleteOrphanWines()
                released(input.photo)
                return@withTransaction Saved(old.id, null)
            }
        }
        val wine = resolveWine(input.wine, input.photo, overwrite = true)
        val id = dao.insertTasting(
            Tasting(
                wineId = wine.id, profileId = input.profileId, year = input.year, score = input.score,
                notes = input.notes.trim(), photo = input.photo, createdAt = now,
            )
        )
        released(input.photo)
        // a pending "did you drink this?" card for the same wine and year is now answered
        val cleared = dao.findInvitation(wine.id, input.profileId, input.year)
        if (cleared != null) dao.deleteInvitation(cleared.id)
        val undo: Undo = {
            db.withTransaction {
                dao.deleteTasting(id)
                if (cleared != null && dao.wine(cleared.wineId) != null) dao.insertInvitation(cleared)
                dao.deleteOrphanWines()
            }
        }
        Saved(id, undo)
    }

    suspend fun deleteTasting(id: Long): Undo? = db.withTransaction {
        val t = dao.tasting(id) ?: return@withTransaction null
        val w = dao.wine(t.wineId)
        dao.deleteTasting(id)
        val undo: Undo = {
            db.withTransaction {
                if (w != null && dao.wine(w.id) == null) dao.insertWine(w)
                dao.insertTasting(t)
            }
        }
        undo
    }

    // ------------------------------------------------------------------
    // Invitations ("Did you also drink this bottle?")
    // ------------------------------------------------------------------

    suspend fun invitationNo(id: Long) = db.withTransaction {
        dao.deleteInvitation(id)
        dao.deleteOrphanWines()
    }

    suspend fun invitationYes(id: Long) = db.withTransaction {
        dao.invitation(id)?.let { dao.updateInvitation(it.copy(stage = 1)) }
    }

    suspend fun invitationRate(id: Long, score: Double): Undo? = db.withTransaction {
        val inv = dao.invitation(id) ?: return@withTransaction null
        val tastingId = dao.insertTasting(
            Tasting(
                wineId = inv.wineId, profileId = inv.profileId, year = inv.year,
                score = score, createdAt = System.currentTimeMillis(),
            )
        )
        dao.deleteInvitation(id)
        val undo: Undo = {
            db.withTransaction {
                dao.deleteTasting(tastingId)
                dao.insertInvitation(inv)
            }
        }
        undo
    }

    // ------------------------------------------------------------------
    // Wishlist and profiles
    // ------------------------------------------------------------------

    suspend fun setWish(profileId: Long, guideKey: String, on: Boolean) {
        if (on) dao.insertWish(WishlistItem(profileId, guideKey)) else dao.deleteWish(profileId, guideKey)
    }

    suspend fun updateProfile(p: Profile) {
        dao.updateProfile(p.copy(name = p.name.trim().ifEmpty { "Profile" }))
    }

    // ------------------------------------------------------------------
    // Bulk access for import, export and backup
    // ------------------------------------------------------------------

    suspend fun snapshot(): Snapshot = Snapshot(
        dao.allProfiles(), dao.allWines(), dao.allBottles(), dao.allTastings(),
        dao.allOpenings(), dao.allWishlist(), dao.allInvitations(),
    )

    suspend fun replaceAll(s: Snapshot) = db.withTransaction {
        dao.clearInvitations()
        dao.clearWishlist()
        dao.clearOpenings()
        dao.clearTastings()
        dao.clearBottles()
        dao.clearWines()
        dao.clearProfiles()
        dao.insertProfiles(s.profiles)
        dao.insertWines(s.wines)
        dao.insertBottles(s.bottles)
        dao.insertTastings(s.tastings)
        dao.insertOpenings(s.openings)
        dao.insertWishes(s.wishlist)
        dao.insertInvitations(s.invitations)
    }

    /** Append imported records (from the former HTML page or a CSV), skipping exact duplicates. */
    suspend fun importRecords(profileId: Long, rec: ImportedRecords): ImportReport = db.withTransaction {
        val now = System.currentTimeMillis()
        var tastings = 0
        var bottles = 0
        var openings = 0
        var wishes = 0
        var skipped = 0
        val year = currentYear()

        val existingTastings = dao.allTastings().filter { it.profileId == profileId }
        val seen = HashSet<String>()
        for (t in rec.tastings) {
            val wine = resolveWine(t.wine, null, overwrite = false)
            val y = t.year ?: year
            val dup = existingTastings.any { it.wineId == wine.id && it.year == y && it.score == t.score }
            val dupNew = !seen.add("${wine.id}|$y|${t.score}")
            if (dup || dupNew) { skipped++; continue }
            dao.insertTasting(
                Tasting(
                    wineId = wine.id, profileId = profileId, year = y, score = t.score,
                    notes = t.notes, createdAt = now,
                )
            )
            tastings++
        }

        for (b in rec.bottles) {
            val wine = resolveWine(b.wine, null, overwrite = false)
            val exists = dao.allBottles().any {
                it.wineId == wine.id && it.vintage == b.vintage && it.format == b.format
            }
            if (exists) { skipped++; continue }
            dao.insertBottle(
                Bottle(
                    wineId = wine.id, vintage = b.vintage, format = b.format, quantity = b.quantity,
                    price = b.price, readyFrom = b.readyFrom, drinkBy = b.drinkBy, supplier = b.supplier,
                    purchaseDate = b.purchaseDate, notes = b.notes, addedAt = now + bottles,
                )
            )
            bottles++
        }

        val existingLog = dao.allOpenings()
        for (o in rec.openings) {
            val dup = existingLog.any {
                it.date == o.date && it.producer == o.producer && it.wine == o.wine && it.vintage == o.vintage
            }
            if (dup) { skipped++; continue }
            dao.insertOpening(o)
            openings++
        }

        val have = dao.allWishlist().filter { it.profileId == profileId }.map { it.guideKey }.toSet()
        for (k in rec.wishKeys) {
            if (k in have) continue
            dao.insertWish(WishlistItem(profileId, k))
            wishes++
        }
        ImportReport(tastings, bottles, openings, wishes, skipped)
    }

    companion object {
        const val ACCENT_GREEN: Int = 0xFF1F5C3A.toInt()
        const val ACCENT_BURGUNDY: Int = 0xFF7A1F3D.toInt()
    }
}

data class ImportedTasting(val wine: WineInfo, val year: Int?, val score: Double, val notes: String = "")

data class ImportedBottle(
    val wine: WineInfo,
    val vintage: Int?,
    val format: Double,
    val quantity: Int,
    val price: Double?,
    val readyFrom: Int?,
    val drinkBy: Int?,
    val supplier: String,
    val purchaseDate: String,
    val notes: String,
)

data class ImportedRecords(
    val tastings: List<ImportedTasting> = emptyList(),
    val bottles: List<ImportedBottle> = emptyList(),
    val openings: List<OpeningLog> = emptyList(),
    val wishKeys: List<String> = emptyList(),
) {
    val isEmpty: Boolean get() = tastings.isEmpty() && bottles.isEmpty() && openings.isEmpty() && wishKeys.isEmpty()
}

data class ImportReport(val tastings: Int, val bottles: Int, val openings: Int, val wishes: Int, val skipped: Int)
