package eu.kanade.tachiyomi.ui.discover

import eu.kanade.domain.entries.anime.model.toDomainAnime
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.Dispatchers
import tachiyomi.domain.entries.anime.interactor.NetworkToLocalAnime
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.atomic.AtomicInteger

object DiscoverResolver {
    private val sourceManager: AnimeSourceManager = Injekt.get()
    private val sourcePreferences: SourcePreferences = Injekt.get()
    private val networkToLocalAnime: NetworkToLocalAnime = Injekt.get()
    private val coroutineDispatcher = Executors.newFixedThreadPool(5).asCoroutineDispatcher()

    private val cache = ConcurrentHashMap<String, Long>()
    private val failedCache = ConcurrentHashMap<String, Long>()
    private val lastSuccessfulSourceId = java.util.concurrent.atomic.AtomicLong(-1L)

    suspend fun resolveAnime(titles: List<String>): Long? = coroutineScope {
        if (titles.isEmpty()) return@coroutineScope null
        val query = titles.first()
        val normalizedKey = query.lowercase().trim()

        cache[normalizedKey]?.let { return@coroutineScope it }

        val failedTimestamp = failedCache[normalizedKey]
        if (failedTimestamp != null && System.currentTimeMillis() - failedTimestamp < 10 * 60 * 1000) {
            return@coroutineScope null
        }

        val enabledLanguages = sourcePreferences.enabledLanguages().get()
        val disabledSources = sourcePreferences.disabledAnimeSources().get()
        val sources = sourceManager.getAll()
            .filter { it.lang in enabledLanguages && "${it.id}" !in disabledSources }

        if (sources.isEmpty()) {
            failedCache[normalizedKey] = System.currentTimeMillis()
            return@coroutineScope null
        }

        val lastId = lastSuccessfulSourceId.get()
        val sortedSources = sources.sortedByDescending { if (it.id == lastId) 1 else 0 }

        val result = withTimeoutOrNull(4000) {
            coroutineScope {
                val semaphore = Semaphore(8)
                val resultDeferred = CompletableDeferred<Long?>()
                val finishedCount = AtomicInteger(0)
                val totalSources = sortedSources.size

                sortedSources.forEachIndexed { index, source ->
                    async {
                        if (resultDeferred.isCompleted) return@async
                        if (index > 0) {
                            delay(300)
                        }
                        if (resultDeferred.isCompleted) return@async

                        semaphore.withPermit {
                            if (resultDeferred.isCompleted) return@async
                            try {
                                val page = withTimeoutOrNull(3000) {
                                    withContext(coroutineDispatcher) {
                                        source.getSearchAnime(1, query, source.getFilterList())
                                    }
                                } ?: return@withPermit

                                val candidateAnimes = page.animes.take(5)
                                for (sAnime in candidateAnimes) {
                                    val score = TitleMatcher.score(titles, sAnime.title)
                                    if (score >= TitleMatcher.ACCEPT_THRESHOLD) {
                                        val domainAnime = networkToLocalAnime.await(sAnime.toDomainAnime(source.id))
                                        if (resultDeferred.complete(domainAnime.id)) {
                                            cache[normalizedKey] = domainAnime.id
                                            lastSuccessfulSourceId.set(source.id)
                                        }
                                        return@withPermit
                                    }
                                }
                            } catch (_: Exception) {}

                            if (finishedCount.incrementAndGet() == totalSources) {
                                resultDeferred.complete(null)
                            }
                        }
                    }
                }
                resultDeferred.await()
            }
        }

        if (result == null) {
            failedCache[normalizedKey] = System.currentTimeMillis()
        }
        result
    }

    suspend fun prefetchHero(titles: List<String>) {
        withContext(Dispatchers.IO) {
            for (title in titles) {
                try {
                    resolveAnime(listOf(title))
                    delay(500)
                } catch (_: Exception) {}
            }
        }
    }
}
