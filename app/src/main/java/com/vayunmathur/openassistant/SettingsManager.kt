package com.vayunmathur.openassistant

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    val apiKey = prefs.getStringFlow("api_key").filterNotNull().stateIn(CoroutineScope(Dispatchers.Main), SharingStarted.Eagerly, "")

    fun setApiKey(apiKey: String) {
        prefs.edit {
            putString("api_key", apiKey)
        }
    }
}

fun SharedPreferences.getStringFlow(keyForFloat: String) = callbackFlow<String?> {
    val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (keyForFloat == key) {
            trySend(getString(key, null))
        }
    }
    registerOnSharedPreferenceChangeListener(listener)
    if (contains(keyForFloat)) {
        send(getString(keyForFloat, null)) // if you want to emit an initial pre-existing value
    }
    awaitClose { unregisterOnSharedPreferenceChangeListener(listener) }
}.buffer(Channel.UNLIMITED) // so trySend never fails
