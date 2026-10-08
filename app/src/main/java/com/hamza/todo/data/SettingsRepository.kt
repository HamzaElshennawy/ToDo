package com.hamza.todo.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.hamza.todo.core.model.DeviceInfo
import com.hamza.todo.core.model.TaskList
import com.hamza.todo.core.views.SortMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.util.UUID

enum class ThemeMode(val label: String) { SYSTEM("System"), LIGHT("Light"), DARK("Dark") }

data class AppSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val defaultListId: String = TaskList.INBOX_ID,
    val showCompleted: Boolean = true,
    val overdueFirst: Boolean = true,
    val remindersOn: Boolean = true,
    val dailySummary: Boolean = false,
    val syncEnabled: Boolean = false,
    val wifiOnly: Boolean = false,
    val lastSyncAt: Long? = null,
    val devices: List<DeviceInfo> = emptyList(),
    val deviceId: String = "",
    val listSort: Map<String, SortMode> = emptyMap(),
)

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    private object Keys {
        val theme = stringPreferencesKey("theme")
        val defaultList = stringPreferencesKey("default_list")
        val showCompleted = booleanPreferencesKey("show_completed")
        val overdueFirst = booleanPreferencesKey("overdue_first")
        val reminders = booleanPreferencesKey("reminders")
        val dailySummary = booleanPreferencesKey("daily_summary")
        val syncEnabled = booleanPreferencesKey("sync_enabled")
        val wifiOnly = booleanPreferencesKey("wifi_only")
        val lastSync = longPreferencesKey("last_sync")
        val devices = stringPreferencesKey("devices")
        val deviceId = stringPreferencesKey("device_id")
        val listSort = stringPreferencesKey("list_sort")
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val devicesSerializer = ListSerializer(DeviceInfo.serializer())
    private val sortSerializer = MapSerializer(String.serializer(), String.serializer())

    val settings: Flow<AppSettings> = context.dataStore.data.map(::read)

    suspend fun current(): AppSettings = settings.first()

    private fun read(p: Preferences) = AppSettings(
        theme = p[Keys.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
        defaultListId = p[Keys.defaultList] ?: TaskList.INBOX_ID,
        showCompleted = p[Keys.showCompleted] ?: true,
        overdueFirst = p[Keys.overdueFirst] ?: true,
        remindersOn = p[Keys.reminders] ?: true,
        dailySummary = p[Keys.dailySummary] ?: false,
        syncEnabled = p[Keys.syncEnabled] ?: false,
        wifiOnly = p[Keys.wifiOnly] ?: false,
        lastSyncAt = p[Keys.lastSync],
        devices = p[Keys.devices]?.let { runCatching { json.decodeFromString(devicesSerializer, it) }.getOrNull() } ?: emptyList(),
        deviceId = p[Keys.deviceId] ?: "",
        listSort = p[Keys.listSort]?.let { raw ->
            runCatching { json.decodeFromString(sortSerializer, raw) }.getOrNull()
                ?.mapNotNull { (k, v) -> runCatching { SortMode.valueOf(v) }.getOrNull()?.let { k to it } }?.toMap()
        } ?: emptyMap(),
    )

    /** A random id for this install, created on first use. */
    suspend fun deviceId(): String {
        current().deviceId.takeIf { it.isNotEmpty() }?.let { return it }
        val id = UUID.randomUUID().toString()
        context.dataStore.edit { if (it[Keys.deviceId].isNullOrEmpty()) it[Keys.deviceId] = id }
        return current().deviceId
    }

    suspend fun setTheme(value: ThemeMode) = context.dataStore.edit { it[Keys.theme] = value.name }
    suspend fun setDefaultList(id: String) = context.dataStore.edit { it[Keys.defaultList] = id }
    suspend fun setShowCompleted(value: Boolean) = context.dataStore.edit { it[Keys.showCompleted] = value }
    suspend fun setOverdueFirst(value: Boolean) = context.dataStore.edit { it[Keys.overdueFirst] = value }
    suspend fun setReminders(value: Boolean) = context.dataStore.edit { it[Keys.reminders] = value }
    suspend fun setDailySummary(value: Boolean) = context.dataStore.edit { it[Keys.dailySummary] = value }
    suspend fun setSyncEnabled(value: Boolean) = context.dataStore.edit { it[Keys.syncEnabled] = value }
    suspend fun setWifiOnly(value: Boolean) = context.dataStore.edit { it[Keys.wifiOnly] = value }

    suspend fun setSynced(at: Long, devices: List<DeviceInfo>) = context.dataStore.edit {
        it[Keys.lastSync] = at
        it[Keys.devices] = json.encodeToString(devicesSerializer, devices)
    }

    suspend fun setListSort(listId: String, mode: SortMode) = context.dataStore.edit { prefs ->
        val current = prefs[Keys.listSort]?.let { runCatching { json.decodeFromString(sortSerializer, it) }.getOrNull() } ?: emptyMap()
        prefs[Keys.listSort] = json.encodeToString(sortSerializer, current + (listId to mode.name))
    }
}
