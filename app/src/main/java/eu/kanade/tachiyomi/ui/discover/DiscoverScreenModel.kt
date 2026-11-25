package eu.kanade.tachiyomi.ui.discover

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

private const val ANILIST_URL = "https://graphql.anilist.co"

private const val TRENDING_QUERY =
    "query { Page(page: 1, perPage: 20) { media(type: ANIME, sort: TRENDING_DESC, isAdult: false) { id title { romaji english } coverImage { extraLarge large } genres seasonYear format averageScore } } }"

private const val ADDITIONAL_ROWS_QUERY =
    "query { popular: Page(page:1, perPage:20){ media(type:ANIME, sort:POPULARITY_DESC, isAdult:false){ id title{romaji english} coverImage{large} averageScore } } action: Page(page:1, perPage:20){ media(type:ANIME, genre:\"Action\", sort:POPULARITY_DESC, isAdult:false){ id title{romaji english} coverImage{large} averageScore } } romance: Page(page:1, perPage:20){ media(type:ANIME, genre:\"Romance\", sort:POPULARITY_DESC, isAdult:false){ id title{romaji english} coverImage{large} averageScore } } fantasy: Page(page:1, perPage:20){ media(type:ANIME, genre:\"Fantasy\", sort:POPULARITY_DESC, isAdult:false){ id title{romaji english} coverImage{large} averageScore } } }"

private val discoverJson = Json { ignoreUnknownKeys = true }

private val discoverClient = OkHttpClient()

class DiscoverScreenModel : StateScreenModel<DiscoverScreenModel.State>(State.Loading) {

    data class StateData(
        val trending: List<Item>,
        val popular: List<Item>,
        val action: List<Item>,
        val romance: List<Item>,
        val fantasy: List<Item>,
    )

    sealed interface State {
        data object Loading : State
        data class Error(val message: String) : State
        data class Success(val data: StateData) : State
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
                val trending = fetchTrending()
                val additional = fetchAdditional()
                State.Success(StateData(trending, additional.first, additional.second, additional.third, additional.fourth))
            } catch (e: Exception) {
                State.Error(e.message ?: "Network error")
            }
        }
    }

    private suspend fun fetchTrending(): List<Item> = withContext(Dispatchers.IO) {
        val requestBody = buildJsonObject { put("query", TRENDING_QUERY) }.toString()
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
                heroImageUrl = media.coverImage?.extraLarge ?: media.coverImage?.large,
                genres = media.genres.orEmpty(),
                year = media.seasonYear,
                format = media.format,
                score = media.averageScore,
            )
        }
    }

    private suspend fun fetchAdditional(): FourLists = withContext(Dispatchers.IO) {
        val requestBody = buildJsonObject { put("query", ADDITIONAL_ROWS_QUERY) }.toString()
            .toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(ANILIST_URL)
            .header("Accept", "application/json")
            .post(requestBody)
            .build()

        val text = discoverClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext FourLists(emptyList(), emptyList(), emptyList(), emptyList())
            response.peekBody(Long.MAX_VALUE).string()
        }

        val parsed = discoverJson.decodeFromString(AdditionalResponse.serializer(), text)
        fun mapItems(media: List<AniListMedia>?): List<Item> = media.orEmpty().map { m ->
            Item(
                id = m.id,
                title = m.title?.english ?: m.title?.romaji ?: "Unknown",
                coverUrl = m.coverImage?.large,
                heroImageUrl = null,
                genres = emptyList(),
                year = null,
                format = null,
                score = m.averageScore,
            )
        }
        FourLists(
            mapItems(parsed.data?.popular?.media),
            mapItems(parsed.data?.action?.media),
            mapItems(parsed.data?.romance?.media),
            mapItems(parsed.data?.fantasy?.media),
        )
    }

    private data class FourLists(val first: List<Item>, val second: List<Item>, val third: List<Item>, val fourth: List<Item>)
}

@Serializable
private data class AdditionalResponse(val data: AdditionalData? = null)
@Serializable
private data class AdditionalData(
    val popular: AniListPage? = null,
    val action: AniListPage? = null,
    val romance: AniListPage? = null,
    val fantasy: AniListPage? = null,
)

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
