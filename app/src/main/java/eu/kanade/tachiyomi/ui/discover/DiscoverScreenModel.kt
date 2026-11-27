package eu.kanade.tachiyomi.ui.discover

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

private const val ANILIST_URL = "https://graphql.anilist.co"

private const val DISCOVER_QUERY =
    "query { trending: Page(page: 1, perPage: 30) { media(type: ANIME, sort: TRENDING_DESC, isAdult: false) { id title { romaji english } coverImage { extraLarge large } genres seasonYear format averageScore } } topRated: Page(page: 1, perPage: 30) { media(type: ANIME, sort: SCORE_DESC, isAdult: false) { id title { romaji english } coverImage { extraLarge large } genres seasonYear format averageScore } } airingNow: Page(page: 1, perPage: 30) { media(type: ANIME, status: RELEASING, sort: POPULARITY_DESC, isAdult: false) { id title { romaji english } coverImage { extraLarge large } genres seasonYear format averageScore } } popularAllTime: Page(page: 1, perPage: 30) { media(type: ANIME, sort: POPULARITY_DESC, isAdult: false) { id title { romaji english } coverImage { extraLarge large } genres seasonYear format averageScore } } animeMovies: Page(page: 1, perPage: 30) { media(type: ANIME, format: MOVIE, sort: POPULARITY_DESC, isAdult: false) { id title { romaji english } coverImage { extraLarge large } genres seasonYear format averageScore } } fanFavorites: Page(page: 1, perPage: 30) { media(type: ANIME, sort: FAVOURITES_DESC, isAdult: false) { id title { romaji english } coverImage { extraLarge large } genres seasonYear format averageScore } } action: Page(page: 1, perPage: 30) { media(type: ANIME, genre_in: [\"Action\"], sort: SCORE_DESC, isAdult: false) { id title { romaji english } coverImage { extraLarge large } genres seasonYear format averageScore } } romance: Page(page: 1, perPage: 30) { media(type: ANIME, genre_in: [\"Romance\"], sort: SCORE_DESC, isAdult: false) { id title { romaji english } coverImage { extraLarge large } genres seasonYear format averageScore } } fantasy: Page(page: 1, perPage: 30) { media(type: ANIME, genre_in: [\"Fantasy\"], sort: SCORE_DESC, isAdult: false) { id title { romaji english } coverImage { extraLarge large } genres seasonYear format averageScore } } comedy: Page(page: 1, perPage: 30) { media(type: ANIME, genre_in: [\"Comedy\"], sort: SCORE_DESC, isAdult: false) { id title { romaji english } coverImage { extraLarge large } genres seasonYear format averageScore } } sciFi: Page(page: 1, perPage: 30) { media(type: ANIME, genre_in: [\"Sci-Fi\"], sort: SCORE_DESC, isAdult: false) { id title { romaji english } coverImage { extraLarge large } genres seasonYear format averageScore } } sliceOfLife: Page(page: 1, perPage: 30) { media(type: ANIME, genre_in: [\"Slice of Life\"], sort: SCORE_DESC, isAdult: false) { id title { romaji english } coverImage { extraLarge large } genres seasonYear format averageScore } } }"

private val discoverJson = Json { ignoreUnknownKeys = true }

private val discoverClient = OkHttpClient()

private var cachedStateData: DiscoverScreenModel.StateData? = null

class DiscoverScreenModel : StateScreenModel<DiscoverScreenModel.State>(
    cachedStateData?.let { State.Success(it) } ?: State.Loading
) {

    data class StateData(
        val trending: List<Item>,
        val rows: List<Pair<String, List<Item>>>,
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
        if (cachedStateData == null) {
            load()
        } else {
            // Background refresh
            screenModelScope.launch {
                try {
                    val trending = fetchTrendingWithRetry()
                    val additional = fetchAdditionalWithRetry()
                    if (trending.isNotEmpty()) {
                        val newData = StateData(trending, additional)
                        cachedStateData = newData
                        mutableState.value = State.Success(newData)
                    }
                } catch (_: Exception) {}
            }
        }
    }

    fun load() {
        mutableState.value = State.Loading
        screenModelScope.launch {
            var lastException: Exception? = null
            var successData: StateData? = null

            val delays = listOf(1000L, 2000L, 4000L)
            for (attempt in 0..3) {
                if (attempt > 0) {
                    kotlinx.coroutines.delay(delays[attempt - 1])
                }
                try {
                    val trending = fetchTrending()
                    if (trending.isEmpty()) {
                        error("Empty trending list received")
                    }
                    val additional = fetchAdditional()
                    val data = StateData(trending, additional)
                    successData = data
                    break
                } catch (e: Exception) {
                    lastException = e
                }
            }

            mutableState.value = if (successData != null) {
                cachedStateData = successData
                State.Success(successData)
            } else {
                val e = lastException
                val msg = if (e != null) "${e.javaClass.simpleName}: ${e.message}" else "Network error"
                State.Error(msg)
            }
        }
    }

    private suspend fun fetchTrendingWithRetry(): List<Item> {
        var lastE: Exception? = null
        val delays = listOf(1000L, 2000L, 4000L)
        for (attempt in 0..3) {
            if (attempt > 0) kotlinx.coroutines.delay(delays[attempt - 1])
            try {
                val res = fetchTrending()
                if (res.isNotEmpty()) return res
            } catch (e: Exception) {
                lastE = e
            }
        }
        throw lastE ?: Exception("Failed to fetch trending")
    }

    private suspend fun fetchAdditionalWithRetry(): List<Pair<String, List<Item>>> {
        try {
            return fetchAdditional()
        } catch (_: Exception) {
            return emptyList()
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

    private suspend fun fetchAdditional(): List<Pair<String, List<Item>>> = withContext(Dispatchers.IO) {
        val requestBody = buildJsonObject { put("query", DISCOVER_QUERY) }.toString()
            .toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(ANILIST_URL)
            .header("Accept", "application/json")
            .post(requestBody)
            .build()

        val text = discoverClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext emptyList()
            response.peekBody(Long.MAX_VALUE).string()
        }

        val parsed = discoverJson.decodeFromString(AdditionalResponse.serializer(), text)
        val data = parsed.data ?: return@withContext emptyList()
        val seenIds = mutableSetOf<Int>()
        val result = mutableListOf<Pair<String, List<Item>>>()

        fun process(title: String, page: AniListPage?) {
            val items = page?.media.orEmpty().map { m ->
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
            }.filter { it.id !in seenIds }
                .take(20)

            if (items.size >= 6) {
                result.add(title to items)
                seenIds.addAll(items.map { it.id })
            }
        }

        process("Top Rated", data.topRated)
        process("Airing Now", data.airingNow)
        process("Popular All Time", data.popularAllTime)
        process("Anime Movies", data.animeMovies)
        process("Fan Favorites", data.fanFavorites)
        process("Action", data.action)
        process("Romance", data.romance)
        process("Fantasy", data.fantasy)
        process("Comedy", data.comedy)
        process("Sci-Fi", data.sciFi)
        process("Slice of Life", data.sliceOfLife)

        result
    }
}

@Serializable
private data class AdditionalResponse(val data: AdditionalData? = null)
@Serializable
private data class AdditionalData(
    val trending: AniListPage? = null,
    val topRated: AniListPage? = null,
    val airingNow: AniListPage? = null,
    val popularAllTime: AniListPage? = null,
    val animeMovies: AniListPage? = null,
    val fanFavorites: AniListPage? = null,
    val action: AniListPage? = null,
    val romance: AniListPage? = null,
    val fantasy: AniListPage? = null,
    val comedy: AniListPage? = null,
    val sciFi: AniListPage? = null,
    val sliceOfLife: AniListPage? = null,
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
