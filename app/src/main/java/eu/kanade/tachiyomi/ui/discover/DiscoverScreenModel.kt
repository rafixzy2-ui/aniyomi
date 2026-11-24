package eu.kanade.tachiyomi.ui.discover

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
	import uy.kohesive.injekt.Injekt
	import uy.kohesive.injekt.api.get
	import tachiyomi.data.core.network.NetworkHelper

sealed interface DiscoverState {
    data object Loading : DiscoverState
    data class Error(val message: String) : DiscoverState
    data class Success(val items: List<DiscoverItem>) : DiscoverState
}

@Serializable
data class DiscoverItem(
    val id: Int,
    val title: String,
    val coverUrl: String?,
    val score: Int?,
)

@Serializable
data class AniListResponse(
    val data: AniListData?,
)

@Serializable
data class AniListData(
    val Page: AniListPage?,
)

@Serializable
data class AniListPage(
    val media: List<AniListMedia>?,
)

@Serializable
data class AniListMedia(
    val id: Int,
    val title: AniListTitle?,
    val coverImage: AniListCoverImage?,
    val averageScore: Int?,
)

@Serializable
data class AniListTitle(
    val romaji: String?,
    val english: String?,
)

@Serializable
data class AniListCoverImage(
    val large: String?,
)

class DiscoverScreenModel : StateScreenModel<DiscoverState>(DiscoverState.Loading) {

    private val networkHelper: NetworkHelper = Injekt.get()
    private val json: Json = Injekt.get()

    init {
        load()
    }

    fun load() {
        screenModelScope.launch(Dispatchers.IO) {
            mutableState.update { DiscoverState.Loading }
            try {
                val query = "query { Page(page:1, perPage:20) { media(type: ANIME, sort: TRENDING_DESC) { id title { romaji english } coverImage { large } averageScore } } }"
                val requestBody = json.encodeToString(
                    kotlinx.serialization.json.JsonObject.serializer(),
                    kotlinx.serialization.json.JsonObject(mapOf("query" to kotlinx.serialization.json.JsonPrimitive(query)))
                ).toRequestBody("application/json".toMediaType())

                val request = Request.Builder()
                    .url("https://graphql.anilist.co")
                    .post(requestBody)
                    .build()

                networkHelper.client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        mutableState.update { DiscoverState.Error("Network error: ${response.code}") }
                        return@launch
                    }
                    val bodyString = response.body?.string() ?: throw Exception("Empty response")
                    val parsed = json.decodeFromString(AniListResponse.serializer(), bodyString)
                    val mediaList = parsed.data?.Page?.media.orEmpty()
                    val items = mediaList.map { media ->
                        DiscoverItem(
                            id = media.id,
                            title = media.title?.english ?: media.title?.romaji ?: "Unknown",
                            coverUrl = media.coverImage?.large,
                            score = media.averageScore,
                        )
                    }
                    mutableState.update { DiscoverState.Success(items) }
                }
            } catch (e: Exception) {
                mutableState.update { DiscoverState.Error(e.message ?: "Unknown error") }
            }
        }
    }
}
