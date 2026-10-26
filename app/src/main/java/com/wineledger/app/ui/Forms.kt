@file:OptIn(ExperimentalMaterial3Api::class)

package com.wineledger.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wineledger.app.AppViewModel
import com.wineledger.app.BottleDraft
import com.wineledger.app.TastingDraft
import com.wineledger.app.activeProfile
import com.wineledger.app.data.AppData
import com.wineledger.app.data.Guide
import com.wineledger.app.logic.FORMATS
import com.wineledger.app.logic.formatLabel
import com.wineledger.app.logic.isoToDateDigits
import com.wineledger.app.logic.norm
import com.wineledger.app.logic.todayIso
import com.wineledger.app.logic.wineKey
import kotlinx.coroutines.launch
import java.io.File

// =====================================================================
// Suggestions: wines already known (ledger, cellar) first, then the Veronelli guide
// =====================================================================

private data class Sugg(
    val producer: String,
    val name: String,
    val style: String,
    val country: String,
    val region: String,
    val grapes: String,
    val fromGuide: Boolean,
)

@Composable
private fun rememberSuggestions(data: AppData, guide: Guide): List<Sugg> = remember(data.wines, guide) {
    val history = data.wines.values
        .sortedBy { it.name.lowercase() }
        .map { Sugg(it.producer, it.name, it.style, it.country, it.region, it.grapes, false) }
    val known = history.map { wineKey(it.producer, it.name) }.toHashSet()
    val fromGuide = guide.wines
        .map { Sugg(it.winery, it.name, it.style, "Italy", it.region, "", true) }
        .filter { known.add(wineKey(it.producer, it.name)) }
    history + fromGuide
}

private fun producerOptions(all: List<Sugg>, typed: String): List<String> {
    val q = norm(typed)
    if (q.isEmpty()) return emptyList()
    return all.map { it.producer }.distinct()
        .filter { norm(it).contains(q) }
        .sortedBy { if (norm(it).startsWith(q)) 0 else 1 }
        .take(5)
}

private fun wineOptions(all: List<Sugg>, producer: String, typed: String): List<Sugg> {
    val p = norm(producer)
    val q = norm(typed)
    if (p.isEmpty() && q.isEmpty()) return emptyList()
    return all.asSequence()
        .filter { p.isEmpty() || norm(it.producer).contains(p) || p.contains(norm(it.producer)) }
        .filter { q.isEmpty() || norm(it.name).contains(q) }
        .sortedBy { if (q.isNotEmpty() && norm(it.name).startsWith(q)) 0 else 1 }
        .take(5)
        .toList()
}

private fun labelOf(s: Sugg, producerKnown: Boolean): String =
    if (producerKnown) s.name else s.name + " (" + s.producer + ")"

// =====================================================================
// Shared pieces
// =====================================================================

@Composable
private fun FormHeader(title: String, subtitle: String?, onClose: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        IconButton(onClick = onClose, modifier = Modifier.padding(0.dp)) {
            Icon(Icons.Filled.Close, contentDescription = "Close")
        }
    }
}

@Composable
private fun PhotoSection(photo: String?, vm: AppViewModel, onPhoto: (String?) -> Unit) {
    val scope = rememberCoroutineScope()
    var shot by remember { mutableStateOf<File?>(null) }
    var failed by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val name = vm.importPhoto(uri)
            if (name != null) { vm.discardPhoto(photo); onPhoto(name); failed = false } else failed = true
        }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f = shot
        if (ok && f != null) scope.launch {
            val name = vm.importCameraShot(f)
            if (name != null) { vm.discardPhoto(photo); onPhoto(name); failed = false } else failed = true
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Photo", style = MaterialTheme.typography.labelLarge)
        Row(verticalAlignment = Alignment.CenterVertically) {
            PhotoThumb(photo?.let { vm.photoFile(it, true) }, 88.dp, full = photo?.let { vm.photoFile(it, false) })
            Spacer(Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GhostButton("Choose", onClick = {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    })
                    GhostButton("Camera", onClick = {
                        val (f, uri) = vm.cameraTarget()
                        shot = f
                        camera.launch(uri)
                    })
                }
                if (photo != null) {
                    QuietButton("Remove photo", onClick = { vm.discardPhoto(photo); onPhoto(null) }, danger = true)
                }
            }
        }
        if (failed) {
            Text("This image could not be read.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun DiscardPhotoOnLeave(key: Any, photo: String?, vm: AppViewModel, saved: Boolean) {
    val latestPhoto by rememberUpdatedState(photo)
    val latestSaved by rememberUpdatedState(saved)
    DisposableEffect(key) {
        onDispose { if (!latestSaved) vm.discardPhoto(latestPhoto) }
    }
}

// =====================================================================
// Bottle form: five fields, then Save
// =====================================================================

@Composable
fun BottleFormScreen(vm: AppViewModel, initial: BottleDraft, key: Any) {
    val data by vm.data.collectAsStateWithLifecycle()
    val guide by vm.guide.collectAsStateWithLifecycle()
    val suggestions = rememberSuggestions(data, guide)

    var d by remember(key) { mutableStateOf(initial) }
    var saved by remember(key) { mutableStateOf(false) }
    var askPrice by remember(key) { mutableStateOf<Boolean?>(null) } // null = closed, value = "add another"
    var priceEntry by remember(key) { mutableStateOf("") }
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    DiscardPhotoOnLeave(key, d.photo, vm, saved)

    val editing = d.editId != 0L
    val ready = d.producer.isNotBlank() && d.name.isNotBlank() && d.style.isNotEmpty() && validDateDigits(d.purchaseDate)

    fun doSave(addAnother: Boolean) {
        if (!addAnother) saved = true
        vm.saveBottle(d, addAnother) {
            if (addAnother) {
                d = BottleDraft(moreOpen = d.moreOpen)
                priceEntry = ""
                scope.launch { scroll.animateScrollTo(0) }
            }
        }
    }

    fun trySave(addAnother: Boolean) {
        if (!ready) return
        if (!editing && d.price.isBlank()) { priceEntry = ""; askPrice = addAnother } else doSave(addAnother)
    }

    Column(
        Modifier.fillMaxSize().imePadding().verticalScroll(scroll).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        FormHeader(
            if (editing) "Edit bottle" else "Add a bottle",
            if (editing) null else "Five fields, a few seconds.",
            onClose = { vm.back() },
        )

        val knownProducer = suggestions.any { norm(it.producer) == norm(d.producer) } && d.producer.isNotBlank()
        SuggestField(
            value = d.producer,
            onValueChange = { d = d.copy(producer = it) },
            label = "Producer",
            suggestions = producerOptions(suggestions, d.producer),
        )
        val wineOpts = wineOptions(suggestions, d.producer, d.name)
        SuggestField(
            value = d.name,
            onValueChange = { d = d.copy(name = it) },
            label = "Wine",
            suggestions = wineOpts.map { labelOf(it, knownProducer) },
            onPick = { label ->
                wineOpts.firstOrNull { labelOf(it, knownProducer) == label }?.let { s ->
                    d = d.copy(
                        producer = s.producer, name = s.name,
                        style = s.style.ifEmpty { d.style },
                        country = d.country.ifEmpty { s.country },
                        region = d.region.ifEmpty { s.region },
                        grapes = d.grapes.ifEmpty { s.grapes },
                    )
                }
            },
        )

        Text("Style", style = MaterialTheme.typography.labelLarge)
        StyleChips(d.style, { d = d.copy(style = it) })

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            AppField(
                d.vintage, { d = d.copy(vintage = it.filter { c -> c.isDigit() }.take(4)) },
                "Vintage (optional)", Modifier.weight(1f), keyboard = KeyboardType.Number,
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Quantity", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Stepper(d.quantity, { d = d.copy(quantity = it) })
            }
        }

        QuietButton(
            if (d.moreOpen) "Fewer details" else "More details",
            onClick = { d = d.copy(moreOpen = !d.moreOpen) },
        )
        if (d.moreOpen) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PhotoSection(d.photo, vm) { d = d.copy(photo = it) }
                DropdownField(
                    "Format",
                    if (d.format == 0.75) "" else formatLabel(d.format),
                    FORMATS.filter { it != 0.75 }.map { formatLabel(it) },
                    { label -> d = d.copy(format = FORMATS.firstOrNull { formatLabel(it) == label } ?: 0.75) },
                    emptyLabel = "Standard 75 cl",
                )
                AppField(d.price, { d = d.copy(price = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(8)) },
                    "Price per bottle (€)", keyboard = KeyboardType.Decimal)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AppField(
                        d.readyFrom, { d = d.copy(readyFrom = it.filter { c -> c.isDigit() }.take(4)) },
                        "Ready from (year)", Modifier.weight(1f), keyboard = KeyboardType.Number,
                    )
                    AppField(
                        d.drinkBy, { d = d.copy(drinkBy = it.filter { c -> c.isDigit() }.take(4)) },
                        "Drink by (year)", Modifier.weight(1f), keyboard = KeyboardType.Number,
                    )
                }
                AppField(d.supplier, { d = d.copy(supplier = it) }, "Bought from")
                Row(verticalAlignment = Alignment.Top) {
                    DateField(
                        d.purchaseDate, { d = d.copy(purchaseDate = it) },
                        "Purchase date", Modifier.weight(1f),
                    )
                    QuietButton(
                        "Today",
                        onClick = { d = d.copy(purchaseDate = isoToDateDigits(todayIso())) },
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AppField(d.country, { d = d.copy(country = it) }, "Country", Modifier.weight(1f))
                    AppField(d.region, { d = d.copy(region = it) }, "Region", Modifier.weight(1f))
                }
                AppField(d.grapes, { d = d.copy(grapes = it) }, "Grapes (comma separated)")
                AppField(d.notes, { d = d.copy(notes = it) }, "Notes", singleLine = false)
            }
        }

        if (!ready) {
            Text(
                "Producer, wine and style are needed.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        PrimaryButton(
            if (editing) "Save changes" else "Save",
            onClick = { trySave(false) },
            modifier = Modifier.fillMaxWidth(),
            enabled = ready,
        )
        if (!editing) {
            GhostButton("Save and add another", onClick = { trySave(true) }, modifier = Modifier.fillMaxWidth(), enabled = ready)
        }
        Spacer(Modifier.height(24.dp))
    }

    askPrice?.let { another ->
        AlertDialog(
            onDismissRequest = { askPrice = null },
            title = { Text("No price yet") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Add the price now? It feeds the estimated value of the cellar.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    AppField(
                        priceEntry, { priceEntry = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(8) },
                        "Price per bottle (€)", keyboard = KeyboardType.Decimal,
                    )
                }
            },
            confirmButton = {
                PrimaryButton("Add price", enabled = priceEntry.isNotBlank(), onClick = {
                    d = d.copy(price = priceEntry)
                    askPrice = null
                    if (!another) saved = true
                    vm.saveBottle(d.copy(price = priceEntry), another) {
                        if (another) {
                            d = BottleDraft(moreOpen = d.moreOpen)
                            priceEntry = ""
                            scope.launch { scroll.animateScrollTo(0) }
                        }
                    }
                })
            },
            dismissButton = {
                QuietButton("Skip", onClick = { askPrice = null; doSave(another) })
            },
        )
    }
}

// =====================================================================
// Tasting form
// =====================================================================

@Composable
fun TastingFormScreen(vm: AppViewModel, initial: TastingDraft, key: Any) {
    val data by vm.data.collectAsStateWithLifecycle()
    val guide by vm.guide.collectAsStateWithLifecycle()
    val activeId by vm.activeId.collectAsStateWithLifecycle()
    val profile = data.activeProfile(activeId)
    val suggestions = rememberSuggestions(data, guide)

    var d by remember(key) { mutableStateOf(initial) }
    var saved by remember(key) { mutableStateOf(false) }
    DiscardPhotoOnLeave(key, d.photo, vm, saved)

    val editing = d.editId != 0L
    val ready = d.producer.isNotBlank() && d.name.isNotBlank() && d.style.isNotEmpty() &&
        d.year.toIntOrNull() != null && validScore(d.score)

    Column(
        Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        FormHeader(
            if (editing) "Edit tasting" else "Record a tasting",
            if (profile != null) "For ${profile.name}" else null,
            onClose = { vm.back() },
        )

        val knownProducer = suggestions.any { norm(it.producer) == norm(d.producer) } && d.producer.isNotBlank()
        SuggestField(
            value = d.producer,
            onValueChange = { d = d.copy(producer = it) },
            label = "Producer",
            suggestions = producerOptions(suggestions, d.producer),
        )
        val wineOpts = wineOptions(suggestions, d.producer, d.name)
        SuggestField(
            value = d.name,
            onValueChange = { d = d.copy(name = it) },
            label = "Wine",
            suggestions = wineOpts.map { labelOf(it, knownProducer) },
            onPick = { label ->
                wineOpts.firstOrNull { labelOf(it, knownProducer) == label }?.let { s ->
                    d = d.copy(
                        producer = s.producer, name = s.name,
                        style = s.style.ifEmpty { d.style },
                        country = d.country.ifEmpty { s.country },
                        region = d.region.ifEmpty { s.region },
                        grapes = d.grapes.ifEmpty { s.grapes },
                    )
                }
            },
        )
        Text("Style", style = MaterialTheme.typography.labelLarge)
        StyleChips(d.style, { d = d.copy(style = it) })

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AppField(d.country, { d = d.copy(country = it) }, "Country", Modifier.weight(1f))
            AppField(d.region, { d = d.copy(region = it) }, "Region", Modifier.weight(1f))
        }
        AppField(d.grapes, { d = d.copy(grapes = it) }, "Grapes (comma separated)")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AppField(
                d.vintage, { d = d.copy(vintage = it.filter { c -> c.isDigit() }.take(4)) },
                "Vintage (optional)", Modifier.weight(1f), keyboard = KeyboardType.Number,
            )
            AppField(
                d.year, { d = d.copy(year = it.filter { c -> c.isDigit() }.take(4)) },
                "Tasting year", Modifier.weight(1f), keyboard = KeyboardType.Number,
            )
        }
        ScoreInput(d.score, { d = d.copy(score = it) })
        AppField(d.notes, { d = d.copy(notes = it) }, "Notes", singleLine = false)
        PhotoSection(d.photo, vm) { d = d.copy(photo = it) }

        if (!ready) {
            Text(
                "Producer, wine, style, year and a score between 0 and 100 are needed.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        PrimaryButton(
            if (editing) "Save changes" else "Save tasting",
            onClick = {
                saved = true
                vm.saveTasting(d) {}
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = ready,
        )
        Spacer(Modifier.height(24.dp))
    }
}
