package com.esper.android.data.remote

import android.content.Context
import android.content.SharedPreferences
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.gson.annotations.SerializedName
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

data class HealthResponse(
    val service: String?,
    val engine: String?,
    val status: String?
)

data class MeResponse(
    val sub: String,
    val name: String?,
    val avatar: String?,
    @SerializedName("member_type") val memberType: String?
)

data class RemotePage(
    val id: String,
    val parentId: String?,
    val title: String,
    val notes: String?,
    val position: Int?
)

data class RemoteCanvas(
    val id: String,
    val type: String,
    val title: String,
    val assignedPages: List<String>?,
    val data: Any?
)

data class RemoteQuestion(
    val id: String,
    val title: String,
    val answer: String,
    val position: Int?
)

data class RemoteNotebook(
    val id: String,
    val title: String,
    val description: String?,
    val visibility: String?,
    val tags: List<String>?,
    val coverPreset: String?,
    val coverImage: String?,
    val isPinned: Boolean?,
    @SerializedName("isEncrypted") val isEncrypted: Boolean?,
    val wrappedDek: String?,
    val cryptoSalt: String?,
    val pages: List<RemotePage>?,
    val canvases: List<RemoteCanvas>?,
    val questions: List<RemoteQuestion>?
)

data class RemoteTabItem(
    val docId: String,
    val pageId: String,
    val pageNumber: Int? = 1,
    val updatedAt: Long? = null
)

data class RemoteShelf(
    val id: String,
    val title: String,
    val description: String?,
    val color: String?,
    val coverPreset: String?,
    val position: Int?,
    val notebookIds: List<String>?
)

data class SyncResult(
    val success: Boolean,
    val id: String?
)

interface EsperApiService {
    @GET("health")
    suspend fun getHealth(): HealthResponse

    @GET("me")
    suspend fun getMe(): MeResponse

    @GET("user/notes")
    suspend fun getUserNotes(): List<RemoteNotebook>

    @GET("user/tabs")
    suspend fun getUserTabs(): List<RemoteTabItem>

    @PUT("user/tabs")
    suspend fun updateUserTabs(@Body body: Map<String, List<RemoteTabItem>>): SyncResult

    @GET("notes/{id}")
    suspend fun getNote(
        @Path("id") id: String,
        @Header("x-guest-token") guestToken: String? = null
    ): RemoteNotebook

    @POST("notes")
    suspend fun createNote(@Body body: Map<String, Any?>): SyncResult

    @PUT("notes/{id}")
    suspend fun updateNote(@Path("id") id: String, @Body body: Map<String, Any?>): SyncResult

    @DELETE("notes/{id}")
    suspend fun deleteNote(@Path("id") id: String): SyncResult

    @POST("notes/{id}/pages")
    suspend fun createPage(@Path("id") id: String, @Body body: Map<String, Any?>): SyncResult

    @PATCH("notes/{id}/pages/{pageId}")
    suspend fun patchPage(
        @Path("id") id: String,
        @Path("pageId") pageId: String,
        @Body body: Map<String, Any?>
    ): SyncResult

    @DELETE("notes/{id}/pages/{pageId}")
    suspend fun deletePage(
        @Path("id") id: String,
        @Path("pageId") pageId: String
    ): SyncResult

    @GET("shelves")
    suspend fun getShelves(): List<RemoteShelf>

    @POST("shelves")
    suspend fun createShelf(@Body body: Map<String, Any?>): SyncResult

    @PUT("shelves/{id}")
    suspend fun updateShelf(@Path("id") id: String, @Body body: Map<String, Any?>): SyncResult

    @DELETE("shelves/{id}")
    suspend fun deleteShelf(@Path("id") id: String): SyncResult
}

object RemoteClient {
    const val BASE_URL = "https://esper-api.johnesleyer.com/api/"

    private var cachedService: EsperApiService? = null

    fun getService(context: Context): EsperApiService {
        if (cachedService != null) return cachedService!!

        val prefs: SharedPreferences = context.getSharedPreferences("esper_auth_prefs", Context.MODE_PRIVATE)

        val authInterceptor = Interceptor { chain ->
            val original = chain.request()

            // Firebase ID tokens expire after 1 hour: fetch a live/refreshed
            // token directly from Firebase on this background worker thread,
            // falling back to the cached token.
            val user = FirebaseAuth.getInstance().currentUser
            val token = if (user != null) {
                try {
                    Tasks.await(user.getIdToken(false), 5, TimeUnit.SECONDS)?.token
                        ?: prefs.getString("firebase_jwt_token", null)
                } catch (_: Exception) {
                    prefs.getString("firebase_jwt_token", null)
                }
            } else {
                prefs.getString("firebase_jwt_token", null)
            }

            val builder = original.newBuilder()
            if (!token.isNullOrBlank()) {
                builder.header("Authorization", "Bearer $token")
            }
            chain.proceed(builder.build())
        }

        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        return retrofit.create(EsperApiService::class.java).also { cachedService = it }
    }
}
