package eu.kanade.tachiyomi.ui.discover

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

private const val ANILIST_URL = "https://graphql.anilist.co"

private const val TRENDING_QUERY =
    "query { Page(page: 1, perPage: 20) { media(type: ANIME, sort: TRENDING_DESC) { id title { romaji english } coverImage { large } averageScore } } }"

private val discoverJson = Json { ignoreUnknownKeys = true }

private val discoverClient = OkHttpClient()

class DiscoverScreenModel : StateScreenModel<DiscoverScreenModel.State>(State.Loading) {

    sealed interface State {
        data object Loading : State

        data class Error(val message: String) : State

        data class Success(val items: List<Item>) : State
    }

    data class Item(
        val id: Int,
        val title: String,
        val coverUrl: String?,
        val score: Int?,
    )

    init {
        load()
    }

    fun load() {
        mutableState.value = State.Loading
        screenModelScope.launch {
            mutableState.value = try {
                State.Success(fetchTrending())
            } catch (e: Exception) {
                State.Error(e.message ?: "Network error")
            }
        }
    }

    private suspend fun fetchTrending(): List<Item> = withContext(Dispatchers.IO) {
        val requestBody = "{\"query\":\"$TRENDING_QUERY\"}"
            .toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(ANILIST_URL)
            .header("Accept", "application/json")
            .post(requestBody)
            .build()

        val text = discoverClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("AniList error ${response.code}")
            response.peekBody(Long.MAX_VALUE).string()
        }

        val parsed = discoverJson.decodeFromString(AniListResponse.serializer(), text)
        parsed.data?.page?.media.orEmpty().map { media ->
            Item(
                id = media.id,
                title = media.title?.english ?: media.title?.romaji ?: "Unknown",
                coverUrl = media.coverImage?.large,
                score = media.averageScore,
            )
        }
    }
}

@Serializable
private data class AniListResponse(
    val data: AniListData? = null,
)

@Serializable
private data class AniListData(
    @SerialName("Page") val page: AniListPage? = null,
)

@Serializable
private data class AniListPage(
    val media: List<AniListMedia> = emptyList(),
)

@Serializable
private data class AniListMedia(
    val id: Int,
    val title: AniListTitle? = null,
    val coverImage: AniListCover? = null,
    val averageScore: Int? = null,
)

@Serializable
private data class AniListTitle(
    val romaji: String? = null,
    val english: String? = null,
)

@Serializable
private data class AniListCover(
    val large: String? = null,
)
