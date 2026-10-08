package com.hamza.todo.sync

import android.app.PendingIntent
import android.content.Context
import android.os.Build
import android.provider.Settings
import com.hamza.todo.core.model.DeviceInfo
import com.hamza.todo.core.sync.SyncMerger
import com.hamza.todo.core.sync.SyncSnapshot
import com.hamza.todo.data.SettingsRepository
import com.hamza.todo.data.TodoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class SyncState(
    val running: Boolean = false,
    val error: String? = null,
    val needsPermission: Boolean = false,
)

sealed interface SyncResult {
    data object Success : SyncResult
    data object Disabled : SyncResult
    data class NeedsPermission(val intent: PendingIntent) : SyncResult
    data class Failed(val message: String, val retryable: Boolean) : SyncResult
}

/**
 * Two-way sync through one JSON file in the Drive app-data folder:
 * download it, merge with the local data (newest edit wins per item), save the result
 * locally and upload it back.
 */
class SyncManager(
    private val context: Context,
    private val repository: TodoRepository,
    private val settings: SettingsRepository,
    private val auth: DriveAuth = DriveAuth(context),
    private val drive: DriveApi = DriveApi(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow(SyncState())
    val state: StateFlow<SyncState> = _state

    suspend fun sync(): SyncResult {
        if (!settings.current().syncEnabled) return SyncResult.Disabled
        return mutex.withLock {
            _state.update { it.copy(running = true, error = null) }
            val result = runCatching { syncOnce() }.getOrElse { e ->
                val retryable = e !is DriveException || e.code >= 500 || e.code == 429 || e.code == 401
                SyncResult.Failed(friendly(e), retryable)
            }
            _state.update {
                when (result) {
                    is SyncResult.NeedsPermission -> it.copy(running = false, needsPermission = true)
                    is SyncResult.Failed -> it.copy(running = false, error = result.message)
                    else -> it.copy(running = false, error = null, needsPermission = false)
                }
            }
            result
        }
    }

    private suspend fun syncOnce(): SyncResult {
        val token = when (val access = auth.authorize()) {
            is DriveAccess.NeedsConsent -> return SyncResult.NeedsPermission(access.intent)
            is DriveAccess.Granted -> access.accessToken
        }
        val now = clock()
        val fileId = drive.findFileId(token)
        val remote = fileId?.let { SyncSnapshot.fromJson(drive.download(token, it)) } ?: SyncSnapshot()

        val local = repository.snapshot().copy(devices = listOf(thisDevice(now)))
        val result = SyncMerger.merge(local, remote)
        if (result.localChanged) repository.applySynced(result.merged)
        if (fileId == null) {
            drive.create(token, result.merged.toJson())
        } else if (result.remoteChanged) {
            drive.update(token, fileId, result.merged.toJson())
        }
        settings.setSynced(now, result.merged.devices)
        return SyncResult.Success
    }

    private suspend fun thisDevice(now: Long) = DeviceInfo(
        id = settings.deviceId(),
        name = deviceName(),
        isTablet = context.resources.configuration.smallestScreenWidthDp >= 600,
        lastSyncAt = now,
    )

    private fun deviceName(): String {
        val named = runCatching { Settings.Global.getString(context.contentResolver, "device_name") }.getOrNull()
        return named?.takeIf { it.isNotBlank() } ?: "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"
    }

    private fun friendly(e: Throwable): String = when {
        e is DriveException && e.code == 403 -> "Google Drive refused access. Check the app’s Google Cloud setup."
        e is DriveException -> "Google Drive error ${e.code}"
        e is java.net.UnknownHostException -> "No internet connection"
        e is java.io.IOException -> "Couldn’t reach Google Drive"
        e is com.google.android.gms.common.api.ApiException -> "Google sign-in error ${e.statusCode}. Check the app’s Google Cloud setup."
        else -> e.message ?: "Sync failed"
    }
}
