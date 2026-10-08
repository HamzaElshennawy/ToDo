package com.hamza.todo.sync

import android.app.PendingIntent
import android.content.Context
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

sealed interface DriveAccess {
    data class Granted(val accessToken: String) : DriveAccess

    /** The person has to approve Drive access once; launch this from an Activity. */
    data class NeedsConsent(val intent: PendingIntent) : DriveAccess
}

/**
 * Gets a Drive token for the Google account already on the device. Only the hidden
 * app-data folder is requested, so the app cannot see any other Drive files.
 */
class DriveAuth(private val context: Context) {
    suspend fun authorize(): DriveAccess {
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(DRIVE_APPDATA_SCOPE)))
            .build()
        val result = Identity.getAuthorizationClient(context).authorize(request).await()
        val pending = result.pendingIntent
        if (result.hasResolution() && pending != null) return DriveAccess.NeedsConsent(pending)
        val token = result.accessToken ?: throw IOException("Google did not return a Drive token")
        return DriveAccess.Granted(token)
    }

    companion object {
        const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"
    }
}

/** The few Drive REST calls sync needs: find, read and write one file in appDataFolder. */
class DriveApi(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build(),
) {
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    suspend fun findFileId(token: String): String? = withContext(Dispatchers.IO) {
        val url = "$API/files".toHttpUrl().newBuilder()
            .addQueryParameter("spaces", "appDataFolder")
            .addQueryParameter("q", "name = '$FILE_NAME' and trashed = false")
            .addQueryParameter("fields", "files(id)")
            .build()
        val body = execute(Request.Builder().url(url).header("Authorization", "Bearer $token").get().build())
        val files = JSONObject(body).optJSONArray("files")
        if (files == null || files.length() == 0) null else files.getJSONObject(0).getString("id")
    }

    suspend fun download(token: String, fileId: String): String = withContext(Dispatchers.IO) {
        val url = "$API/files/$fileId".toHttpUrl().newBuilder().addQueryParameter("alt", "media").build()
        execute(Request.Builder().url(url).header("Authorization", "Bearer $token").get().build())
    }

    suspend fun create(token: String, content: String): String = withContext(Dispatchers.IO) {
        val metadata = JSONObject()
            .put("name", FILE_NAME)
            .put("parents", org.json.JSONArray().put("appDataFolder"))
            .toString()
        val body = MultipartBody.Builder()
            .setType("multipart/related".toMediaType())
            .addPart(metadata.toRequestBody(jsonType))
            .addPart(content.toRequestBody(jsonType))
            .build()
        val request = Request.Builder()
            .url("$UPLOAD/files?uploadType=multipart&fields=id")
            .header("Authorization", "Bearer $token")
            .post(body)
            .build()
        JSONObject(execute(request)).getString("id")
    }

    suspend fun update(token: String, fileId: String, content: String) = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$UPLOAD/files/$fileId?uploadType=media&fields=id")
            .header("Authorization", "Bearer $token")
            .patch(content.toRequestBody(jsonType))
            .build()
        execute(request)
        Unit
    }

    private fun execute(request: Request): String =
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw DriveException(response.code, text.take(300))
            text
        }

    companion object {
        const val FILE_NAME = "todo-sync.json"
        private const val API = "https://www.googleapis.com/drive/v3"
        private const val UPLOAD = "https://www.googleapis.com/upload/drive/v3"
    }
}

class DriveException(val code: Int, message: String) : IOException("Drive error $code: $message")
