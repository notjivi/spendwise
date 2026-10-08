package com.spendwise.app.ui.common

import android.content.Context
import android.content.ContextWrapper
import androidx.fragment.app.FragmentActivity

/** Compose's LocalContext can be a ContextWrapper; BiometricPrompt needs the real FragmentActivity. */
fun Context.findFragmentActivity(): FragmentActivity {
    var c: Context = this
    while (c is ContextWrapper) {
        if (c is FragmentActivity) return c
        c = c.baseContext
    }
    error("No FragmentActivity found")
}
