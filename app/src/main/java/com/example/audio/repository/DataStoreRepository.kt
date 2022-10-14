package com.example.audio.repository

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

const val preferenceName = "recording"

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = preferenceName)

class DataStoreRepository(private val context: Context) {
    private object PreferencesKeys {
        val isRecordingOn = booleanPreferencesKey("isRecordingOn")
    }

    suspend fun saveToDataStore(value: Boolean) {
        context.dataStore.edit { preference ->
            preference[PreferencesKeys.isRecordingOn] = value
        }
    }

    val readFromDataStore: Flow<Boolean> = context.dataStore.data.catch { exception ->
        if (exception is IOException) {
            Log.d("DataStore", exception.message.toString())
            emit(emptyPreferences())
        } else {
            throw  exception
        }
    }.map { preference ->
        preference[PreferencesKeys.isRecordingOn] ?: false
    }
}