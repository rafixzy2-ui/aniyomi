package eu.kanade.tachiyomi.ui.discover

import eu.kanade.domain.entries.anime.model.toDomainAnime
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import tachiyomi.domain.entries.anime.interactor.NetworkToLocalAnime
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher

object DiscoverResolver {
    private val sourceManager: AnimeSourceManager = Injekt.get()
    private val sourcePreferences: SourcePreferences = Injekt.get()
    private val networkToLocalAnime: NetworkToLocalAnime = Injekt.get()
    private val coroutineDispatcher = Executors.newFixedThreadPool(5).asCoroutineDispatcher()

    suspend fun resolveAnime(titles: List<String>): Long? = coroutineScope {
        if (titles.isEmpty()) return@coroutineScope null
        val enabledLanguages = sourcePreferences.enabledLanguages().get()
        val disabledSources = sourcePreferences.disabledAnimeSources().get()
        val sources = sourceManager.getAll()
            .filter { it.lang in enabledLanguages && "${it.id}" !in disabledSources }

        val query = titles.first()
        var bestId: Long? = null
        var bestScore = 0.0

        sources.map { source ->
            async {
                try {
                    val page = withTimeoutOrNull(8000) {
                        withContext(coroutineDispatcher) {
                            source.getSearchAnime(1, query, source.getFilterList())
                        }
                    } ?: return@async

                    val candidateAnimes = page.animes.take(5)
                    for (sAnime in candidateAnimes) {
                        val score = TitleMatcher.score(titles, sAnime.title)
                        if (score >= TitleMatcher.ACCEPT_THRESHOLD) {
                            val domainAnime = networkToLocalAnime.await(sAnime.toDomainAnime(source.id))
                            synchronized(this) {
                                if (score > bestScore) {
                                    bestScore = score
                                    bestId = domainAnime.id
                                }
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
        }.awaitAll()

        bestId
    }
}
