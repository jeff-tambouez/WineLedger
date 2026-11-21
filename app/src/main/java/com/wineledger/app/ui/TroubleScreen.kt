package com.wineledger.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.wineledger.app.AppViewModel
import com.wineledger.app.ExportKind
import com.wineledger.app.Trouble
import com.wineledger.app.logic.todayIso

/**
 * Shown instead of the app when the previous run crashed or the database cannot be read.
 * Rescuing the data comes first: the copy is made from the raw files, without Room.
 */
@Composable
fun TroubleScreen(vm: AppViewModel, trouble: Trouble) {
    val saveDatabase = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri -> if (uri != null) vm.export(ExportKind.DATABASE_ZIP, uri) }
    val saveReport = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri -> if (uri != null) vm.export(ExportKind.CRASH_TXT, uri) }

    val files = remember { vm.databaseFiles() }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Eyebrow("Something went wrong")
        Text(trouble.title, style = MaterialTheme.typography.headlineSmall)
        Text(
            "Your wines are still stored on this phone. Do not use “Clear data” in the Android " +
                "settings and do not uninstall the app: that is the only thing that would really delete them. " +
                "Clearing the cache is harmless.",
            style = MaterialTheme.typography.bodyMedium,
        )

        Section("Rescue the data first") {
            Text(
                "This copies the database files straight out of the app, without opening them, " +
                    "so it works even when the app cannot read them.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PrimaryButton(
                "Save a copy of the database",
                { saveDatabase.launch("wine-ledger-database-${todayIso()}.zip") },
                Modifier.fillMaxWidth(),
            )
            Text(files, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Section("What happened") {
            Text(
                "Save this report and send it over, it names the exact cause.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            GhostButton(
                "Save the report",
                { saveReport.launch("wine-ledger-report-${todayIso()}.txt") },
                Modifier.fillMaxWidth(),
            )
            SelectionContainer {
                Text(
                    trouble.detail,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }

        GhostButton("Continue to the app", { vm.dismissTrouble() }, Modifier.fillMaxWidth())
        Spacer(Modifier.height(24.dp))
    }
}
