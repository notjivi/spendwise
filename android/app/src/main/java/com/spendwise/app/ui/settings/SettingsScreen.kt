package com.spendwise.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spendwise.app.BuildConfig
import com.spendwise.app.security.BiometricHelper
import com.spendwise.app.ui.common.findFragmentActivity

@Composable
fun SettingsScreen(vm: SettingsViewModel = hiltViewModel()) {
    val lockEnabled by vm.lockEnabled.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var confirmSignOut by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.messageFlow.collect { snackbar.showSnackbar(it) } }

    // Storage Access Framework: the system file picker chooses WHERE to save. We need no storage permission.
    val createDoc = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) vm.export(uri)
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(Modifier.padding(padding)) {
            ListItem(
                headlineContent = { Text("Biometric lock") },
                supportingContent = { Text("Require fingerprint, face or device PIN to open the app") },
                trailingContent = {
                    Switch(checked = lockEnabled, onCheckedChange = { wantEnabled ->
                        if (!BiometricHelper.canAuthenticate(context)) {
                            // Can't enable a lock the device can't provide (no screen lock / no enrolled biometrics).
                            return@Switch
                        }
                        // Re-authenticate before changing the setting either way.
                        BiometricHelper.prompt(
                            context.findFragmentActivity(), "Confirm it's you",
                            onSuccess = { vm.setLock(wantEnabled) }, onError = { },
                        )
                    })
                },
            )
            if (!BiometricHelper.canAuthenticate(context)) {
                Text("Set up a screen lock or fingerprint in system settings to use this.", modifier = Modifier.padding(horizontal = 16.dp))
            }
            HorizontalDivider()
            ListItem(modifier = Modifier.clickable { createDoc.launch(vm.suggestedFileName()) }, headlineContent = { Text("Export to CSV") }, supportingContent = { Text("Save all expenses to a file") })
            HorizontalDivider()
            ListItem(modifier = Modifier.clickable { confirmSignOut = true }, headlineContent = { Text("Sign out") })
            HorizontalDivider()
            ListItem(headlineContent = { Text("About") }, supportingContent = { Text("SpendWise ${BuildConfig.VERSION_NAME}") })
        }
    }

    if (confirmSignOut) AlertDialog(
        onDismissRequest = { confirmSignOut = false },
        title = { Text("Sign out?") },
        text = { Text("Unsynced changes stay on this device and sync when you sign in again.") },
        confirmButton = { TextButton(onClick = { confirmSignOut = false; vm.signOut() }) { Text("Sign out") } },
        dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text("Cancel") } },
    )
}
