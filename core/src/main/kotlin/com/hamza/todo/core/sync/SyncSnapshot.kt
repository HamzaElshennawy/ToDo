package com.hamza.todo.core.sync

import com.hamza.todo.core.model.DeviceInfo
import com.hamza.todo.core.model.Subtask
import com.hamza.todo.core.model.Syncable
import com.hamza.todo.core.model.Task
import com.hamza.todo.core.model.TaskList
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Everything that syncs, as stored in the one file in the Drive app-data folder. */
@Serializable
data class SyncSnapshot(
    val version: Int = CURRENT_VERSION,
    val lists: List<TaskList> = emptyList(),
    val tasks: List<Task> = emptyList(),
    val subtasks: List<Subtask> = emptyList(),
    val devices: List<DeviceInfo> = emptyList(),
) {
    /** Same content regardless of order, so two snapshots can be compared. */
    fun normalized() = copy(
        lists = lists.sortedBy { it.id },
        tasks = tasks.sortedBy { it.id },
        subtasks = subtasks.sortedBy { it.id },
        devices = devices.sortedBy { it.id },
    )

    fun toJson(pretty: Boolean = false): String = (if (pretty) prettyJson else json).encodeToString(serializer(), this)

    companion object {
        const val CURRENT_VERSION = 1

        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        private val prettyJson = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }

        fun fromJson(text: String): SyncSnapshot = json.decodeFromString(serializer(), text)
    }
}

data class MergeResult(
    val merged: SyncSnapshot,
    /** True when the local copy is missing something from the remote one. */
    val localChanged: Boolean,
    /** True when the remote copy is missing something from the local one. */
    val remoteChanged: Boolean,
)

/**
 * Merges two copies of the data. For every id the copy with the newest `updatedAt` wins;
 * on a tie a delete wins, so a deleted task never comes back. Deletes are kept as
 * tombstones so a device that was offline still learns about them.
 */
object SyncMerger {

    fun merge(local: SyncSnapshot, remote: SyncSnapshot): MergeResult {
        val merged = SyncSnapshot(
            lists = mergeById(local.lists, remote.lists),
            tasks = mergeById(local.tasks, remote.tasks),
            subtasks = mergeById(local.subtasks, remote.subtasks),
            devices = (local.devices + remote.devices)
                .groupBy { it.id }
                .map { (_, copies) -> copies.maxBy { it.lastSyncAt } },
        ).normalized()
        val l = local.normalized()
        val r = remote.normalized()
        return MergeResult(
            merged = merged,
            localChanged = merged.lists != l.lists || merged.tasks != l.tasks || merged.subtasks != l.subtasks,
            remoteChanged = merged != r,
        )
    }

    fun <T : Syncable> mergeById(a: List<T>, b: List<T>): List<T> {
        val out = LinkedHashMap<String, T>()
        for (item in a + b) {
            val current = out[item.id]
            out[item.id] = if (current == null) item else newer(current, item)
        }
        return out.values.sortedBy { it.id }
    }

    private fun <T : Syncable> newer(x: T, y: T): T = when {
        x.updatedAt != y.updatedAt -> if (x.updatedAt > y.updatedAt) x else y
        x.deleted != y.deleted -> if (x.deleted) x else y
        else -> x
    }
}
