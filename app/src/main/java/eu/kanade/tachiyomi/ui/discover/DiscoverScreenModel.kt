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

private const val DISCOVER_QUERY =
    "query {" +
        " trending: Page(page: 1, perPage: 20) { media(type: ANIME, sort: TRENDING_DESC, isAdult: false) { id title { romaji english } coverImage { extraLarge large } genres seasonYear format averageScore } }" +
        " popular: Page(page: 1, perPage: 20) { media(type: ANIME, sort: POPULARITY_DESC, isAdult: false) { id title { romaji english } coverImage { extraLarge large } genres seasonYear format averageScore } }" +
        " action: Page(page: 1, perPage: 20) { media(type: ANIME, sort: POPULARITY_DESC, genre_in: [\"Action\"], isAdult: false) { id title { romaji english } coverImage { extraLarge large } genres seasonYear format averageScore } }" +
        " romance: Page(page: 1, perPage: 20) { media(type: ANIME, sort: POPULARITY_DESC, genre_in: [\"Romance\"], isAdult: false) { id title { romaji english } coverImage { extraLarge large } genres seasonYear format averageScore } }" +
        " fantasy: Page(page: 1, perPage: 20) { media(type: ANIME, sort: POPULARITY_DESC, genre_in: [\"Fantasy\"], isAdult: false) { id title { romaji english } coverImage { extraLarge large } genres seasonYear format averageScore } }" +
    " }"

private val discoverJson = Json { ignoreUnknownKeys = true }

private val discoverClient = OkHttpClient()

class DiscoverScreenModel : StateScreenModel<DiscoverScreenModel.State>(State.Loading) {

    sealed interface State {
        data object Loading : State

        data class Error(val message: String) : State

        data class Success(
            val items: List<Item>,
            val rows: List<Pair<String, List<Item>>>,
        ) : State
    }

    data class Item(
        val id: Int,
        val title: String,
        val coverUrl: String?,
        val heroImageUrl: String?,
        val genres: List<String>,
        val year: Int?,
        val format: String?,
        val score: Int?,
    )

    init {
        load()
    }

    fun load() {
        mutableState.value = State.Loading
        screenModelScope.launch {
            mutableState.value = try {
                fetchDiscoverData()
            } catch (e: Exception) {
                State.Error(e.message ?: "Network error")
            }
        }
    }

    private suspend fun fetchDiscoverData(): State.Success = withContext(Dispatchers.IO) {
        val requestBody = "{\"query\":\"$DISCOVER_QUERY\"}"
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
        val data = parsed.data ?: error("No data")

        fun mapMediaList(mediaList: List<AniListMedia>?): List<Item> {
            return mediaList.orEmpty().map { media ->
                Item(
                    id = media.id,
                    title = media.title?.english ?: media.title?.romaji ?: "Unknown",
                    coverUrl = media.coverImage?.large,
                    heroImageUrl = media.coverImage?.extraLarge ?: media.coverImage?.large,
                    genres = media.genres.orEmpty(),
                    year = media.seasonYear,
                    format = media.format,
                    score = media.averageScore,
                )
            }
        }

        val trendingItems = mapMediaList(data.trending?.media)
        val popularItems = mapMediaList(data.popular?.media)
        val actionItems = mapMediaList(data.action?.media)
        val romanceItems = mapMediaList(data.romance?.media)
        val fantasyItems = mapMediaList(data.fantasy?.media)
        val topRatedItems = trendingItems.sortedByDescending { it.score ?: 0 }

        val rows = listOf(
            "Trending" to trendingItems,
            "Top Rated" to topRatedItems,
            "Popular Now" to popularItems,
            "Action" to actionItems,
            "Romance" to romanceItems,
            "Fantasy" to fantasyItems,
        )

        State.Success(
            items = trendingItems,
            rows = rows,
        )
    }
}

@Serializable
private data class AniListResponse(
    val data: AniListData? = null,
)

@Serializable
private data class AniListData(
    @SerialName("trending") val trending: AniListPage? = null,
    @SerialName("popular") val popular: AniListPage? = null,
    @SerialName("action") val action: AniListPage? = null,
    @SerialName("romance") val romance: AniListPage? = null,
    @SerialName("fantasy") val fantasy: AniListPage? = null,
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
    val genres: List<String>? = null,
    val seasonYear: Int? = null,
    val format: String? = null,
    val averageScore: Int? = null,
)

@Serializable
private data class AniListTitle(
    val romaji: String? = null,
    val english: String? = null,
)

@Serializable
private data class AniListCover(
    val extraLarge: String? = null,
    val large: String? = null,
)
