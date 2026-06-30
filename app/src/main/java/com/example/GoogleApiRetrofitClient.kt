package com.example

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

// Google Calendar API Models
data class CalendarEventRequest(
    val summary: String,
    val description: String?,
    val start: EventDateTime,
    val end: EventDateTime,
    val attendees: List<EventAttendee>?
)

data class EventDateTime(
    val dateTime: String, // RFC3339 format
    val timeZone: String? = "UTC"
)

data class EventAttendee(
    val email: String
)

data class CalendarEventResponse(
    val id: String,
    val htmlLink: String,
    val status: String
)

// Gmail API Models
data class GmailSendRequest(
    val raw: String // Base64url encoded email string
)

data class GmailSendResponse(
    val id: String,
    val threadId: String,
    val labelIds: List<String>?
)

data class GmailDraftRequest(
    val message: GmailSendRequest
)

data class GmailDraftResponse(
    val id: String,
    val message: GmailSendResponse?
)

interface GoogleApiService {
    @POST("calendar/v3/calendars/primary/events")
    suspend fun createEvent(
        @Query("conferenceDataVersion") conferenceDataVersion: Int = 1,
        @Body event: CalendarEventRequest
    ): CalendarEventResponse

    @POST("gmail/v1/users/me/drafts")
    suspend fun createDraft(
        @Body request: GmailDraftRequest
    ): GmailDraftResponse
}

object GoogleApiRetrofitClient {
    private const val BASE_URL = "https://www.googleapis.com/"
    var oauthToken: String? = null

    private val authInterceptor = Interceptor { chain ->
        val requestBuilder = chain.request().newBuilder()
        oauthToken?.let { token ->
            requestBuilder.addHeader("Authorization", "Bearer $token")
        }
        chain.proceed(requestBuilder.build())
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val retryInterceptor = Interceptor { chain ->
        val request = chain.request()
        var response: okhttp3.Response? = null
        var tryCount = 0
        var backoff = 2000L

        while (true) {
            try {
                response = chain.proceed(request)
                if (response.isSuccessful || (response.code != 429 && response.code != 503) || tryCount >= 3) {
                    return@Interceptor response
                }
                response.close()
            } catch (e: Exception) {
                if (tryCount >= 3) {
                    throw e
                }
            }
            tryCount++
            try {
                Thread.sleep(backoff)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            }
            backoff *= 2
        }
        // Should not reach here
        chain.proceed(request)
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(loggingInterceptor)
        .addInterceptor(retryInterceptor)
        .build()

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    val service: GoogleApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GoogleApiService::class.java)
    }
}
