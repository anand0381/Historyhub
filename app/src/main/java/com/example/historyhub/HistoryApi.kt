package com.example.historyhub

import com.google.gson.annotations.SerializedName
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path

data class OnThisDayResponse(
    @SerializedName("events") val events: List<HistoryItem>?,
    @SerializedName("births") val births: List<HistoryItem>?,
    @SerializedName("deaths") val deaths: List<HistoryItem>?
)

data class HistoryItem(
    @SerializedName("text") val text: String?,
    @SerializedName("year") val year: Int?,
    @SerializedName("pages") val pages: List<WikiPage>?
) {
    fun getThumbnailUrl(): String? {
        return pages?.firstOrNull()?.thumbnail?.source
    }

    fun getWikiUrl(): String? {
        return pages?.firstOrNull()?.contentUrls?.desktop?.page
    }
}

data class WikiPage(
    @SerializedName("title") val title: String?,
    @SerializedName("thumbnail") val thumbnail: WikiThumbnail?,
    @SerializedName("content_urls") val contentUrls: WikiContentUrls?
)

data class WikiThumbnail(
    @SerializedName("source") val source: String?
)

data class WikiContentUrls(
    @SerializedName("desktop") val desktop: WikiUrl?
)

data class WikiUrl(
    @SerializedName("page") val page: String?
)

interface WikipediaApiService {
    @GET("api/rest_v1/feed/onthisday/{type}/{mm}/{dd}")
    suspend fun getOnThisDay(
        @Path("type") type: String,
        @Path("mm") mm: String,
        @Path("dd") dd: String
    ): OnThisDayResponse
}

object HistoryRetrofitInstance {
    private const val BASE_URL = "https://en.wikipedia.org/"

    val api: WikipediaApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(WikipediaApiService::class.java)
    }
}