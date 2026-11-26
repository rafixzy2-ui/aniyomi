package eu.kanade.tachiyomi.ui.discover

import eu.kanade.domain.entries.anime.model.toDomainAnime
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import tachiyomi.domain.entries.anime.interactor.NetworkToLocalAnime
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.atomic.AtomicBoolean

object DiscoverResolver {
    private val sourceManager: AnimeSourceManager = Injekt.get()
    private val sourcePreferences: SourcePreferences = Injekt.get()
    private val networkToLocalAnime: NetworkToLocalAnime = Injekt.get()
    private val coroutineDispatcher = Executors.newFixedThreadPool(5).asCoroutineDispatcher()

    private val cache = ConcurrentHashMap<String, Long>()

    suspend fun resolveAnime(titles: List<String>): Long? = coroutineScope {
        if (titles.isEmpty()) return@coroutineScope null
        val query = titles.first()
        val normalizedKey = query.lowercase().trim()
        cache[normalizedKey]?.let { return@coroutineScope it }

        val enabledLanguages = sourcePreferences.enabledLanguages().get()
        val disabledSources = sourcePreferences.disabledAnimeSources().get()
        val sources = sourceManager.getAll()
            .filter { it.lang in enabledLanguages && "${it.id}" !in disabledSources }

        if (sources.isEmpty()) return@coroutineScope null

        val semaphore = Semaphore(6)
        val resultDeferred = CompletableDeferred<Long?>()
        val finishedCount = java.util.concurrent.atomic.AtomicInteger(0)
        val totalSources = sources.size

        sources.map { source ->
            async {
                if (resultDeferred.isCompleted) return@async
                semaphore.withPermit {
                    if (resultDeferred.isCompleted) return@async
                    try {
                        val page = withTimeoutOrNull(5000) {
                            withContext(coroutineDispatcher) {
                                source.getSearchAnime(1, query, source.getFilterList())
                            }
                        } ?: return@async

                        val candidateAnimes = page.animes.take(5)
                        for (sAnime in candidateAnimes) {
                            val score = TitleMatcher.score(titles, sAnime.title)
                            if (score >= TitleMatcher.ACCEPT_THRESHOLD) {
                                val domainAnime = networkToLocalAnime.await(sAnime.toDomainAnime(source.id))
                                if (resultDeferred.complete(domainAnime.id)) {
                                    cache[normalizedKey] = domainAnime.id
                                }
                                return@async
                            }
                        }
                    } catch (_: Exception) {}

                    if (finishedCount.incrementAndGet() == totalSources) {
                        resultDeferred.complete(null)
                    }
                }
            }
        }

        val result = resultDeferred.await()
        result
    }
}
