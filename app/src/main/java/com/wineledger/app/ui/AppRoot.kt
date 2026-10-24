@file:OptIn(ExperimentalMaterial3Api::class)

package com.wineledger.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wineledger.app.AppViewModel
import com.wineledger.app.Screen
import com.wineledger.app.Tab
import com.wineledger.app.activeProfile
import kotlinx.coroutines.launch

@Composable
fun AppRoot(vm: AppViewModel) {
    val data by vm.data.collectAsStateWithLifecycle()
    val screen by vm.screen.collectAsStateWithLifecycle()
    val tab by vm.tab.collectAsStateWithLifecycle()
    val activeId by vm.activeId.collectAsStateWithLifecycle()
    val both by vm.both.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var chooser by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.toasts.collect { t ->
            snackbar.currentSnackbarData?.dismiss()
            scope.launch {
                val result = snackbar.showSnackbar(
                    message = t.message,
                    actionLabel = if (t.undo != null) "Undo" else null,
                    duration = SnackbarDuration.Short,
                )
                if (result == SnackbarResult.ActionPerformed) t.undo?.let { vm.runUndo(it) }
            }
        }
    }

    BackHandler(enabled = screen !is Screen.Main) { vm.back() }

    val profile = data.activeProfile(activeId)
    val onMain = screen is Screen.Main

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Box(Modifier.statusBarsPadding()) {
                ProfileBar(
                    profiles = data.profiles,
                    activeId = profile?.id ?: -1L,
                    both = both && onMain && tab == Tab.LEDGER,
                    showBoth = onMain && tab == Tab.LEDGER,
                    onSelect = { vm.selectProfile(it) },
                    onBoth = { vm.selectBoth() },
                    onSettings = { vm.openSettings() },
                )
            }
        },
        bottomBar = {
            if (onMain) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    Tab.entries.forEach { t ->
                        NavigationBarItem(
                            selected = tab == t,
                            onClick = { vm.selectTab(t) },
                            icon = {
                                TabIcon(
                                    when (t) {
                                        Tab.LEDGER -> TabGlyph.LEDGER
                                        Tab.CELLAR -> TabGlyph.CELLAR
                                        Tab.VERONELLI -> TabGlyph.STAR
                                        Tab.STATS -> TabGlyph.STATS
                                    },
                                    LocalContentColor.current,
                                )
                            },
                            label = { Text(t.label) },
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (onMain) {
                FloatingActionButton(
                    onClick = {
                        when (tab) {
                            Tab.LEDGER -> vm.newTasting()
                            Tab.CELLAR -> vm.newBottle()
                            else -> chooser = true
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) { Icon(Icons.Filled.Add, contentDescription = "Add") }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (val s = screen) {
                Screen.Main -> when (tab) {
                    Tab.LEDGER -> LedgerScreen(vm)
                    Tab.CELLAR -> CellarScreen(vm)
                    Tab.VERONELLI -> VeronelliScreen(vm)
                    Tab.STATS -> StatsScreen(vm)
                }
                Screen.Settings -> SettingsScreen(vm)
                is Screen.BottleForm -> BottleFormScreen(vm, s.draft, key = s.token)
                is Screen.TastingForm -> TastingFormScreen(vm, s.draft, key = s.token)
            }
        }
    }

    if (chooser) {
        AlertDialog(
            onDismissRequest = { chooser = false },
            title = { Text("Add") },
            text = {
                Column {
                    Text(
                        "Put a bottle in the cellar",
                        Modifier.fillMaxWidth().clickable { chooser = false; vm.newBottle() }
                            .padding(vertical = 14.dp),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        "Record a tasting",
                        Modifier.fillMaxWidth().clickable { chooser = false; vm.newTasting() }
                            .padding(vertical = 14.dp),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            },
            confirmButton = {},
            dismissButton = { QuietButton("Cancel", onClick = { chooser = false }) },
        )
    }

    busy?.let { message ->
        Box(
            Modifier.fillMaxSize().background(Color(0x99000000)).clickable(enabled = true) {},
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Color.White)
                Spacer(Modifier.height(12.dp))
                Text(message, color = Color.White)
            }
        }
    }
}
