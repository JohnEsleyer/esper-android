package com.esper.android.data.remote

import android.content.Context
import android.content.SharedPreferences
import com.esper.android.BuildConfig
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

data class MeUsage(
    val notebook_count: Int = 0,
    val shelf_count: Int = 0
)

data class MeLimits(
    val max_notebooks: Int = 50,
    val max_shelves: Int = 10,
    val max_pages_per_book: Int = 100,
    val max_visual_bytes: Int = 2097152,
    // Null = immune (admin tier); mirrors server TierLimits::inactivity_days.
    val inactivity_days: Long? = 30
)

data class MeResponse(
    val sub: String,
    val name: String?,
    val avatar: String?,
    @SerializedName("member_type") val memberType: String?,
    val usage: MeUsage? = null,
    val limits: MeLimits? = null
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

/** JSON body map for Retrofit. @JvmSuppressWildcards avoids the
 *  'type must not include a wildcard' reflection error for Any? values. */
typealias PayloadMap = Map<String, @JvmSuppressWildcards Any?>

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
    suspend fun createNote(@Body body: PayloadMap): SyncResult

    @PUT("notes/{id}")
    suspend fun updateNote(@Path("id") id: String, @Body body: PayloadMap): SyncResult

    @DELETE("notes/{id}")
    suspend fun deleteNote(@Path("id") id: String): SyncResult

    @POST("notes/{id}/pages")
    suspend fun createPage(@Path("id") id: String, @Body body: PayloadMap): SyncResult

    @PATCH("notes/{id}/pages/{pageId}")
    suspend fun patchPage(
        @Path("id") id: String,
        @Path("pageId") pageId: String,
        @Body body: PayloadMap
    ): SyncResult

    @DELETE("notes/{id}/pages/{pageId}")
    suspend fun deletePage(
        @Path("id") id: String,
        @Path("pageId") pageId: String
    ): SyncResult

    data class GuestTokenResponse(
        val success: Boolean? = null,
        val guestToken: String? = null
    )

    @POST("notes/{id}/verify-password")
    suspend fun verifyPassword(
        @Path("id") id: String,
        @Body body: Map<String, String>
    ): GuestTokenResponse

    @GET("shelves")
    suspend fun getShelves(): List<RemoteShelf>

    @POST("shelves")
    suspend fun createShelf(@Body body: PayloadMap): SyncResult

    @PUT("shelves/{id}")
    suspend fun updateShelf(@Path("id") id: String, @Body body: PayloadMap): SyncResult

    @DELETE("shelves/{id}")
    suspend fun deleteShelf(@Path("id") id: String): SyncResult
}

object RemoteClient {
    const val DEFAULT_BASE_URL = "https://esper-api.johnesleyer.com/api/"
    private const val CONFIG_PREFS = "esper_config"
    private const val BASE_URL_KEY = "api_base_url"

    /** Effective base URL: manual override (Settings) or the default. */
    fun baseUrl(context: Context): String {
        val custom = context.getSharedPreferences(CONFIG_PREFS, Context.MODE_PRIVATE)
            .getString(BASE_URL_KEY, null)?.trim()?.trimEnd('/')
        return if (custom.isNullOrBlank()) DEFAULT_BASE_URL else "$custom/api/"
    }

    fun setBaseUrlOverride(context: Context, url: String?) {
        val clean = url?.trim()?.trimEnd('/')
        context.getSharedPreferences(CONFIG_PREFS, Context.MODE_PRIVATE).edit().apply {
            if (clean.isNullOrBlank()) remove(BASE_URL_KEY) else putString(BASE_URL_KEY, clean)
        }.apply()
        invalidate()
    }

    /** Drop the cached service so the next call rebuilds with current config. */
    fun invalidate() {
        cachedService = null
    }

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

        // Full BODY logging only in debug builds: release logcat must never
        // carry Firebase ID tokens or note contents.
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
            else HttpLoggingInterceptor.Level.BASIC
        }

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl(context))
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        return retrofit.create(EsperApiService::class.java).also { cachedService = it }
    }
}
