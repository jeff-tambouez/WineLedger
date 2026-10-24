package com.wineledger.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wineledger.app.AppViewModel
import com.wineledger.app.ExportKind
import com.wineledger.app.activeProfile
import com.wineledger.app.data.Profile
import com.wineledger.app.logic.todayIso

private val SWATCHES = listOf(
    0xFF1F5C3A, 0xFF7A1F3D, 0xFFB8935A, 0xFF076967, 0xFF2F4B7C, 0xFF6B3F7A,
).map { it.toInt() }

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val data by vm.data.collectAsStateWithLifecycle()
    val dark by vm.dark.collectAsStateWithLifecycle()
    val activeId by vm.activeId.collectAsStateWithLifecycle()
    val profile = data.activeProfile(activeId)
    var confirmRestore by remember { mutableStateOf(false) }

    fun stamp(kind: String, ext: String) = "wine-ledger-$kind-${todayIso()}.$ext"

    val exportTastings = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) vm.export(ExportKind.TASTINGS_CSV, uri)
    }
    val exportCellar = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) vm.export(ExportKind.CELLAR_CSV, uri)
    }
    val exportJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.export(ExportKind.LEDGER_JSON, uri)
    }
    val exportZip = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) vm.export(ExportKind.BACKUP_ZIP, uri)
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importFile(uri)
    }
    val restorer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.restoreBackup(uri)
    }

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Eyebrow("Preferences")
                Text("Settings", style = MaterialTheme.typography.headlineSmall)
            }
            QuietButton("Done", onClick = { vm.back() })
        }

        Section("Profiles") {
            data.profiles.forEach { p -> ProfileEditor(p, vm) }
            Text(
                "The cellar and the Veronelli guide are shared. Tastings, rankings, statistics and wishlist belong to each profile.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Section("Appearance") {
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Dark theme", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Switch(checked = dark, onCheckedChange = { vm.setDark(it) })
            }
        }

        Section("Export") {
            Text(
                "Tastings are exported for the active profile" +
                    (profile?.let { " (${it.name})" } ?: "") + ". The cellar is shared.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            GhostButton("Tastings as CSV", { exportTastings.launch(stamp("tastings", "csv")) }, Modifier.fillMaxWidth())
            GhostButton("Cellar as CSV", { exportCellar.launch(stamp("cellar", "csv")) }, Modifier.fillMaxWidth())
            GhostButton("Ledger as JSON", { exportJson.launch(stamp("ledger", "json")) }, Modifier.fillMaxWidth())
        }

        Section("Import") {
            Text(
                "CSV, JSON, or the HTML page you used before. Rows are added to " +
                    (profile?.name ?: "the active profile") +
                    "'s ledger; exact duplicates are skipped. Cellar lines and the wishlist come along when the file has them.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            GhostButton("Import a file", { importer.launch(arrayOf("*/*")) }, Modifier.fillMaxWidth())
        }

        Section("Backup") {
            Text(
                "A single ZIP with every profile, tasting, bottle, wishlist item and photo. Restoring replaces everything on this phone.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PrimaryButton("Save a full backup", { exportZip.launch(stamp("backup", "zip")) }, Modifier.fillMaxWidth())
            GhostButton("Restore a backup", { confirmRestore = true }, Modifier.fillMaxWidth())
        }

        Section("About") {
            Text(
                "Wine Ledger 1.0. Everything stays on this phone: the app has no network permission and no account.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(24.dp))
    }

    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            title = { Text("Restore a backup?") },
            text = { Text("All data on this phone will be replaced by the content of the backup file.") },
            confirmButton = {
                PrimaryButton("Choose the file", onClick = {
                    confirmRestore = false
                    restorer.launch(arrayOf("*/*"))
                })
            },
            dismissButton = { QuietButton("Cancel", onClick = { confirmRestore = false }) },
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    SoftCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun ProfileEditor(p: Profile, vm: AppViewModel) {
    var name by remember(p.id, p.name) { mutableStateOf(p.name) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProfileDot(p.copy(name = name.ifBlank { p.name }), 40.dp)
            Spacer(Modifier.width(12.dp))
            AppField(name, { name = it.take(24) }, "Name", Modifier.weight(1f))
            if (name.trim() != p.name && name.isNotBlank()) {
                QuietButton("Save", onClick = { vm.updateProfile(p.copy(name = name)) })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            SWATCHES.forEach { c ->
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(c))
                        .border(
                            if (c == p.accent) 3.dp else 0.dp,
                            MaterialTheme.colorScheme.onSurface,
                            CircleShape,
                        )
                        .clickable { vm.updateProfile(p.copy(accent = c)) },
                )
            }
        }
    }
}
