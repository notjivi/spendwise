package com.spendwise.app.ui.lock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.spendwise.app.security.BiometricHelper
import com.spendwise.app.ui.common.findFragmentActivity

@Composable
fun LockScreen(onUnlocked: () -> Unit) {
    val activity = LocalContext.current.findFragmentActivity()
    var error by remember { mutableStateOf<String?>(null) }
    val prompt = { BiometricHelper.prompt(activity, "Unlock SpendWise", onSuccess = onUnlocked, onError = { error = it }) }

    LaunchedEffect(Unit) { prompt() } // ask immediately, but keep a button for after a cancel

    Column(Modifier.fillMaxSize().padding(32.dp), Arrangement.Center, Alignment.CenterHorizontally) {
        Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        Text("SpendWise is locked", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 16.dp))
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
        Button(onClick = { error = null; prompt() }, modifier = Modifier.padding(top = 24.dp)) { Text("Unlock") }
    }
}
