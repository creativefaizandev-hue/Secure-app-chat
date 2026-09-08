
package com.example.notifications

import com.example.BuildConfig
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

object NotificationClient {
  private val client = OkHttpClient()
  private val jsonMediaType = "application/json".toMediaType()

  suspend fun send(context: android.content.Context, recipientUid: String, type: String, senderName: String, resourceType: String, resourceId: String) {
    val url = BuildConfig.NOTIFICATION_WORKER_URL.trimEnd('/')
    if (url.isBlank()) return
    try {
      val idToken = FirebaseAuth.getInstance().currentUser?.getIdToken(false)?.await()?.token ?: return
      val body = JSONObject().apply {
        put("recipientUid", recipientUid)
        put("type", type)
        put("senderName", senderName)
        put("resourceType", resourceType)
        put("resourceId", resourceId)
      }.toString().toRequestBody(jsonMediaType)
      val request = Request.Builder()
        .url("$url/send-notification")
        .header("Authorization", "Bearer $idToken")
        .post(body)
        .build()
      withContext(Dispatchers.IO) {
        client.newCall(request).execute().use { response ->
          if (!response.isSuccessful) throw IllegalStateException("Notification worker returned ${response.code}")
        }
      }
    } catch (_: Exception) {
      // Notifications are best-effort. The message/call already exists in Firestore.
    }
  }
}
