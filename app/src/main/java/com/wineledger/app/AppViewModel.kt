package com.wineledger.app

import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wineledger.app.data.AppData
import com.wineledger.app.data.AppDb
import com.wineledger.app.data.BottleInput
import com.wineledger.app.data.BottleSnapshot
import com.wineledger.app.data.Backup
import com.wineledger.app.data.Guide
import com.wineledger.app.data.GuideData
import com.wineledger.app.data.ImportException
import com.wineledger.app.data.Invitation
import com.wineledger.app.data.LedgerExport
import com.wineledger.app.data.LedgerImport
import com.wineledger.app.data.PhotoStore
import com.wineledger.app.data.Profile
import com.wineledger.app.data.Repo
import com.wineledger.app.data.Taster
import com.wineledger.app.data.TastingInput
import com.wineledger.app.data.Undo
import com.wineledger.app.data.WineInfo
import com.wineledger.app.logic.currentYear
import com.wineledger.app.logic.dateDigitsToIso
import com.wineledger.app.logic.fmtExact
import com.wineledger.app.logic.fmtNumber
import com.wineledger.app.logic.guideKey
import com.wineledger.app.logic.isoToDateDigits
import com.wineledger.app.logic.GuideWine
import com.wineledger.app.logic.parseDoubleLoose
import com.wineledger.app.logic.parseIntLoose
import com.wineledger.app.logic.todayIso
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class Tab(val label: String) {
    LEDGER("Ledger"), CELLAR("Cellar"), VERONELLI("Veronelli"), STATS("Stats")
}

/** Form content, kept as text so the fields can be edited freely. */
data class BottleDraft(
    val editId: Long = 0,
    val producer: String = "",
    val name: String = "",
    val style: String = "",
    val vintage: String = "",
    val quantity: Int = 1,
    val country: String = "",
    val region: String = "",
    val grapes: String = "",
    val format: Double = 0.75,
    val price: String = "",
    val readyFrom: String = "",
    val drinkBy: String = "",
    val supplier: String = "",
    /** Digits typed by the person, DDMMYYYY. Converted from and to an ISO date by the view model. */
    val purchaseDate: String = "",
    val notes: String = "",
    val photo: String? = null,
    val moreOpen: Boolean = false,
)

data class TastingDraft(
    val editId: Long = 0,
    val producer: String = "",
    val name: String = "",
    val style: String = "",
    val country: String = "",
    val region: String = "",
    val grapes: String = "",
    val vintage: String = "",
    // what the bottle knew in the cellar, kept with the tasting
    val format: Double? = null,
    val price: String = "",
    val readyFrom: String = "",
    val drinkBy: String = "",
    val supplier: String = "",
    /** Digits typed by the person, DDMMYYYY. */
    val purchaseDate: String = "",
    val bottleOpen: Boolean = false,
    val year: String = currentYear().toString(),
    val score: String = "",
    val notes: String = "",
    val photo: String? = null,
)

/** Fills the bottle details of a tasting form from what the bottle knew. */
private fun TastingDraft.withBottle(b: BottleSnapshot?): TastingDraft =
    if (b == null) this else copy(
        format = b.format, price = b.price?.let { fmtExact(it) }.orEmpty(),
        readyFrom = b.readyFrom?.toString().orEmpty(), drinkBy = b.drinkBy?.toString().orEmpty(),
        supplier = b.supplier.orEmpty(), purchaseDate = isoToDateDigits(b.purchaseDate.orEmpty()),
        bottleOpen = true,
    )

sealed interface Screen {
    data object Main : Screen
    data object Settings : Screen
    data class BottleForm(val draft: BottleDraft, val token: Long = System.nanoTime()) : Screen
    data class TastingForm(val draft: TastingDraft, val token: Long = System.nanoTime()) : Screen
}

/** A message with an optional Undo action. */
data class Toast(val message: String, val undo: Undo? = null)

enum class ExportKind(val fileName: String, val mime: String) {
    TASTINGS_CSV("tastings.csv", "text/csv"),
    CELLAR_CSV("cellar.csv", "text/csv"),
    LEDGER_JSON("ledger.json", "application/json"),
    BACKUP_ZIP("backup.zip", "application/zip"),
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("wineledger", Context.MODE_PRIVATE)
    private val repo = Repo(AppDb.get(app), PhotoStore(app))

    val data: StateFlow<AppData> = repo.data.stateIn(viewModelScope, SharingStarted.Eagerly, AppData())

    private val _guide = MutableStateFlow(Guide.EMPTY)
    val guide: StateFlow<Guide> = _guide.asStateFlow()

    private val _activeId = MutableStateFlow(prefs.getLong(KEY_PROFILE, -1L))
    val activeId: StateFlow<Long> = _activeId.asStateFlow()

    /** Ledger only: show both profiles side by side. */
    private val _both = MutableStateFlow(false)
    val both: StateFlow<Boolean> = _both.asStateFlow()

    private val _dark = MutableStateFlow(prefs.getBoolean(KEY_DARK, false))
    val dark: StateFlow<Boolean> = _dark.asStateFlow()

    private val _tab = MutableStateFlow(Tab.LEDGER)
    val tab: StateFlow<Tab> = _tab.asStateFlow()

    private val _screen = MutableStateFlow<Screen>(Screen.Main)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    private val _busy = MutableStateFlow<String?>(null)
    val busy: StateFlow<String?> = _busy.asStateFlow()

    private val _toasts = MutableSharedFlow<Toast>(extraBufferCapacity = 8)
    val toasts: SharedFlow<Toast> = _toasts.asSharedFlow()

    init {
        viewModelScope.launch {
            repo.ensureDefaults()
            repo.cleanup()
        }
        viewModelScope.launch(Dispatchers.IO) {
            val g = runCatching { GuideData.load(getApplication()) }.getOrDefault(Guide.EMPTY)
            _guide.value = g
        }
    }

    private fun toast(message: String, undo: Undo? = null) {
        _toasts.tryEmit(Toast(message, undo))
    }

    fun runUndo(undo: Undo) {
        viewModelScope.launch { undo() }
    }

    // ------------------------------------------------------------------
    // Navigation and profile
    // ------------------------------------------------------------------

    fun selectTab(t: Tab) {
        _tab.value = t
        _screen.value = Screen.Main
    }

    fun back() {
        _screen.value = Screen.Main
    }

    fun openSettings() {
        _screen.value = Screen.Settings
    }

    fun selectProfile(id: Long) {
        _activeId.value = id
        _both.value = false
        prefs.edit().putLong(KEY_PROFILE, id).apply()
    }

    fun selectBoth() {
        _both.value = true
    }

    fun setDark(on: Boolean) {
        _dark.value = on
        prefs.edit().putBoolean(KEY_DARK, on).apply()
    }

    fun newBottle(draft: BottleDraft = BottleDraft()) {
        _screen.value = Screen.BottleForm(draft)
    }

    fun newTasting(draft: TastingDraft = TastingDraft()) {
        _screen.value = Screen.TastingForm(draft)
    }

    fun editBottle(id: Long) {
        val d = data.value
        val b = d.bottles.firstOrNull { it.id == id } ?: return
        val w = d.wines[b.wineId] ?: return
        newBottle(
            BottleDraft(
                editId = b.id, producer = w.producer, name = w.name, style = w.style,
                vintage = b.vintage?.toString().orEmpty(), quantity = b.quantity,
                country = w.country, region = w.region, grapes = w.grapes, format = b.format,
                price = b.price?.let { fmtExact(it) }.orEmpty(),
                readyFrom = b.readyFrom?.toString().orEmpty(), drinkBy = b.drinkBy?.toString().orEmpty(),
                supplier = b.supplier, purchaseDate = isoToDateDigits(b.purchaseDate), notes = b.notes,
                photo = b.photo ?: w.photo, moreOpen = true,
            )
        )
    }

    fun editTasting(id: Long) {
        val d = data.value
        val t = d.tastings.firstOrNull { it.id == id } ?: return
        val w = d.wines[t.wineId] ?: return
        newTasting(
            TastingDraft(
                editId = t.id, producer = w.producer, name = w.name, style = w.style, country = w.country,
                region = w.region, grapes = w.grapes, vintage = t.vintage?.toString().orEmpty(),
                year = t.year.toString(), score = fmtNumber(t.score), notes = t.notes, photo = t.photo ?: w.photo,
            ).withBottle(t.bottle)
        )
    }

    /** Form pre-filled from a Veronelli card. */
    fun bottleDraftFrom(g: GuideWine) = BottleDraft(
        producer = g.winery, name = g.name, style = g.style, vintage = g.vintage.takeIf { it.toIntOrNull() != null }.orEmpty(),
        country = "Italy", region = g.region,
        notes = "Veronelli 2026" + if (g.points > 0) ", ${g.points}/100" else "",
    )

    fun tastingDraftFrom(g: GuideWine) = TastingDraft(
        producer = g.winery, name = g.name, style = g.style, country = "Italy", region = g.region,
        vintage = g.vintage.takeIf { it.toIntOrNull() != null }.orEmpty(),
    )

    /** Tasting form for a bottle of the cellar that was just opened elsewhere. */
    fun isGuideWished(profileId: Long, key: String): Boolean =
        data.value.wishlist.any { it.profileId == profileId && it.guideKey == key }

    // ------------------------------------------------------------------
    // Photos
    // ------------------------------------------------------------------

    fun photoFile(name: String, small: Boolean): File = repo.photos.displayFile(name, small)

    fun cameraTarget(): Pair<File, Uri> = repo.photos.newCameraTarget()

    suspend fun importPhoto(uri: Uri): String? = repo.importPhotoUri(uri)

    suspend fun importCameraShot(file: File): String? = repo.importPhotoFile(file)

    fun discardPhoto(name: String?) {
        viewModelScope.launch { repo.discardPhoto(name) }
    }

    // ------------------------------------------------------------------
    // Bottles
    // ------------------------------------------------------------------

    /** Returns true when the draft is valid and was saved. */
    fun saveBottle(draft: BottleDraft, addAnother: Boolean, onSaved: () -> Unit) {
        val vintage = parseIntLoose(draft.vintage)
        val input = BottleInput(
            id = draft.editId,
            wine = WineInfo(draft.producer, draft.name, draft.style, draft.country, draft.region, draft.grapes),
            vintage = vintage, quantity = draft.quantity.coerceAtLeast(1), format = draft.format,
            price = parseDoubleLoose(draft.price), readyFrom = parseIntLoose(draft.readyFrom),
            drinkBy = parseIntLoose(draft.drinkBy), supplier = draft.supplier,
            purchaseDate = dateDigitsToIso(draft.purchaseDate).orEmpty(), notes = draft.notes,
            photo = draft.photo, addedBy = resolveProfileId(),
        )
        viewModelScope.launch {
            val saved = repo.saveBottle(input)
            val label = draft.name.trim().ifEmpty { "Bottle" }
            toast(
                if (draft.editId != 0L) "$label updated" else
                    "${draft.quantity} × $label added to the cellar",
                saved.undo,
            )
            if (!addAnother) _screen.value = Screen.Main
            onSaved()
        }
    }

    fun deleteBottle(id: Long) {
        viewModelScope.launch {
            val undo = repo.deleteBottle(id)
            toast("Bottle removed from the cellar", undo)
        }
    }

    fun openBottle(bottleId: Long, tasters: List<Taster>) {
        viewModelScope.launch {
            val profiles = data.value.profiles
            val undo = repo.openBottle(bottleId, tasters, profiles)
            val rated = tasters.count { it.score != null }
            toast(
                when {
                    tasters.isEmpty() -> "Bottle opened"
                    rated == 0 -> "Bottle opened, rating left for later"
                    else -> "Bottle opened, $rated tasting${if (rated > 1) "s" else ""} recorded"
                },
                undo,
            )
        }
    }

    // ------------------------------------------------------------------
    // Tastings
    // ------------------------------------------------------------------

    fun saveTasting(draft: TastingDraft, onSaved: () -> Unit) {
        val score = parseDoubleLoose(draft.score) ?: return
        val input = TastingInput(
            id = draft.editId, profileId = resolveProfileId(),
            wine = WineInfo(draft.producer, draft.name, draft.style, draft.country, draft.region, draft.grapes),
            year = parseIntLoose(draft.year) ?: currentYear(), score = score,
            notes = draft.notes, photo = draft.photo, vintage = parseIntLoose(draft.vintage),
            bottle = BottleSnapshot(
                format = draft.format, price = parseDoubleLoose(draft.price),
                readyFrom = parseIntLoose(draft.readyFrom), drinkBy = parseIntLoose(draft.drinkBy),
                supplier = draft.supplier.trim().ifEmpty { null },
                purchaseDate = dateDigitsToIso(draft.purchaseDate),
            ).takeIf { it != BottleSnapshot() },
        )
        viewModelScope.launch {
            val saved = repo.saveTasting(input)
            toast(
                if (draft.editId != 0L) "Tasting updated" else "${draft.name.trim()} added to the ledger",
                saved.undo,
            )
            _screen.value = Screen.Main
            onSaved()
        }
    }

    fun deleteTasting(id: Long) {
        viewModelScope.launch {
            val undo = repo.deleteTasting(id)
            toast("Tasting deleted", undo)
        }
    }

    fun resolveProfileId(): Long {
        val profiles = data.value.profiles
        return profiles.firstOrNull { it.id == _activeId.value }?.id ?: profiles.firstOrNull()?.id ?: 0L
    }

    // ------------------------------------------------------------------
    // Invitations, wishlist, profiles
    // ------------------------------------------------------------------

    /** "Tasting to complete": opens the tasting form with the wine already filled in. */
    fun completeBacklog(inv: Invitation) {
        val w = data.value.wines[inv.wineId] ?: return
        newTasting(
            TastingDraft(
                producer = w.producer, name = w.name, style = w.style, country = w.country,
                region = w.region, grapes = w.grapes, vintage = inv.vintage?.toString().orEmpty(),
                notes = inv.notes.orEmpty(), photo = w.photo,
            ).withBottle(inv.bottle)
        )
    }

    fun dismissBacklog(id: Long) {
        viewModelScope.launch {
            val undo = repo.backlogDismiss(id)
            toast("Removed from the backlog", undo)
        }
    }

    fun invitationNo(id: Long) {
        viewModelScope.launch { repo.invitationNo(id) }
    }

    fun invitationYes(id: Long) {
        viewModelScope.launch { repo.invitationYes(id) }
    }

    fun invitationRate(id: Long, score: Double) {
        viewModelScope.launch {
            val undo = repo.invitationRate(id, score)
            toast("Tasting recorded", undo)
        }
    }

    fun toggleWish(g: GuideWine) {
        val pid = resolveProfileId()
        viewModelScope.launch {
            repo.setWish(pid, g.key, !isGuideWished(pid, g.key))
        }
    }

    fun updateProfile(p: Profile) {
        viewModelScope.launch { repo.updateProfile(p) }
    }

    // ------------------------------------------------------------------
    // Import, export, backup
    // ------------------------------------------------------------------

    private fun displayName(uri: Uri): String {
        val cr = getApplication<Application>().contentResolver
        return runCatching {
            cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment.orEmpty()
    }

    fun export(kind: ExportKind, uri: Uri) {
        val cr = getApplication<Application>().contentResolver
        viewModelScope.launch {
            _busy.value = "Exporting…"
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val d = data.value
                    val pid = resolveProfileId()
                    cr.openOutputStream(uri, "wt")!!.use { out ->
                        when (kind) {
                            ExportKind.TASTINGS_CSV -> out.write(LedgerExport.tastingsCsv(d, pid).toByteArray(Charsets.UTF_8))
                            ExportKind.CELLAR_CSV -> out.write(LedgerExport.cellarCsv(d).toByteArray(Charsets.UTF_8))
                            ExportKind.LEDGER_JSON -> out.write(LedgerExport.ledgerJson(d, pid).toByteArray(Charsets.UTF_8))
                            ExportKind.BACKUP_ZIP -> Backup.write(out, repo.snapshot(), repo.photos)
                        }
                    }
                }
            }
            _busy.value = null
            toast(if (result.isSuccess) "Export saved" else "Export failed: ${result.exceptionOrNull()?.message}")
        }
    }

    fun importFile(uri: Uri) {
        val cr = getApplication<Application>().contentResolver
        val name = displayName(uri)
        val pid = resolveProfileId()
        viewModelScope.launch {
            _busy.value = "Importing…"
            val message = try {
                val text = withContext(Dispatchers.IO) {
                    cr.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) }
                }
                val rec = LedgerImport.parse(name, text)
                val r = repo.importRecords(pid, rec)
                buildString {
                    append("Imported ")
                    val parts = ArrayList<String>()
                    if (r.tastings > 0) parts += "${r.tastings} tasting${if (r.tastings > 1) "s" else ""}"
                    if (r.bottles > 0) parts += "${r.bottles} cellar line${if (r.bottles > 1) "s" else ""}"
                    if (r.openings > 0) parts += "${r.openings} opening${if (r.openings > 1) "s" else ""}"
                    if (r.wishes > 0) parts += "${r.wishes} wishlist item${if (r.wishes > 1) "s" else ""}"
                    append(if (parts.isEmpty()) "nothing new" else parts.joinToString(", "))
                    if (r.skipped > 0) append(" (${r.skipped} duplicates skipped)")
                }
            } catch (e: ImportException) {
                e.message ?: "Import failed"
            } catch (e: Exception) {
                "Import failed: ${e.message}"
            }
            _busy.value = null
            toast(message)
        }
    }

    fun restoreBackup(uri: Uri) {
        val cr = getApplication<Application>().contentResolver
        viewModelScope.launch {
            _busy.value = "Restoring…"
            val message = try {
                val snapshot = withContext(Dispatchers.IO) {
                    cr.openInputStream(uri)!!.use { Backup.readSnapshot(it) }
                }
                if (snapshot.profiles.isEmpty()) throw ImportException("This backup contains no profile.")
                withContext(Dispatchers.IO) { repo.photos.clearAll() }
                repo.replaceAll(snapshot)
                withContext(Dispatchers.IO) {
                    cr.openInputStream(uri)!!.use { Backup.restorePhotos(it, repo.photos) }
                }
                val first = snapshot.profiles.first().id
                if (snapshot.profiles.none { it.id == _activeId.value }) selectProfile(first)
                "Backup restored: ${snapshot.tastings.size} tastings, ${snapshot.bottles.size} cellar lines"
            } catch (e: ImportException) {
                e.message ?: "Restore failed"
            } catch (e: Exception) {
                "Restore failed: ${e.message}"
            }
            _busy.value = null
            toast(message)
        }
    }

    companion object {
        private const val KEY_PROFILE = "last_profile"
        private const val KEY_DARK = "dark"

        fun keyOf(g: GuideWine): String = guideKey(g.name, g.vintage, g.winery)
        fun today(): String = todayIso()
    }
}

/** The profile currently selected, falling back to the first one. */
fun AppData.activeProfile(id: Long): Profile? = profiles.firstOrNull { it.id == id } ?: profiles.firstOrNull()
