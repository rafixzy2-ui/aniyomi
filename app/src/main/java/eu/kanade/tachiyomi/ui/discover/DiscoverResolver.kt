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
import java.util.concurrent.atomic.AtomicBoolean

object DiscoverResolver {
    private val sourceManager: AnimeSourceManager = Injekt.get()
    private val sourcePreferences: SourcePreferences = Injekt.get()
    private val networkToLocalAnime: NetworkToLocalAnime = Injekt.get()
    private val coroutineDispatcher = Executors.newFixedThreadPool(5).asCoroutineDispatcher()

    private val cache = ConcurrentHashMap<String, Long>()
    private val failedCache = ConcurrentHashMap<String, Long>()
    private val lastSuccessfulSourceId = java.util.concurrent.atomic.AtomicLong(-1L)

    private fun generateQueries(title: String): List<String> {
        val queries = mutableListOf<String>()
        if (title.isBlank()) return queries

        // (a) full title
        queries.add(title)

        // (b) part of the title before the first ":" or " - " when the title contains one
        val colonIdx = title.indexOf(':')
        val dashIdx = title.indexOf(" - ")
        val splitIdx = when {
            colonIdx != -1 && dashIdx != -1 -> minOf(colonIdx, dashIdx)
            colonIdx != -1 -> colonIdx
            dashIdx != -1 -> dashIdx
            else -> -1
        }
        if (splitIdx != -1) {
            val part = title.substring(0, splitIdx).trim()
            if (part.isNotBlank() && part != title) {
                queries.add(part)
            }
        }

        // (c) title with trailing words removed and trailing digits
        var cleaned = title
        val trailingRegex = Regex("(?i)\\s+(?:season\\s*\\d+|\\d+(?:st|nd|rd|th)\\s+season|part\\s*\\d+|the\\s+final\\s+season|final\\s+season|cour\\s*\\d+|\\d+)$")
        while (true) {
            val match = trailingRegex.find(cleaned)
            if (match != null) {
                cleaned = cleaned.substring(0, match.range.first).trim()
            } else {
                break
            }
        }
        if (cleaned.isNotBlank() && cleaned != title) {
            queries.add(cleaned)
        }

        val validQueries = mutableListOf<String>()
        for (q in queries) {
            val trimmedQ = q.trim()
            if (trimmedQ.length >= 3 && trimmedQ !in validQueries) {
                validQueries.add(trimmedQ)
            }
        }
        return validQueries.take(3)
    }

    suspend fun resolveAnime(titles: List<String>): Long? = coroutineScope {
        if (titles.isEmpty()) return@coroutineScope null
        val query = titles.first()
        val normalizedKey = query.lowercase().trim()

        cache[normalizedKey]?.let { return@coroutineScope it }

        val failedTimestamp = failedCache[normalizedKey]
        if (failedTimestamp != null && System.currentTimeMillis() - failedTimestamp < 3 * 60 * 1000) {
            return@coroutineScope null
        }

        val enabledLanguages = sourcePreferences.enabledLanguages().get()
        val disabledSources = sourcePreferences.disabledAnimeSources().get()
        val sources = sourceManager.getAll()
            .filter { it.lang in enabledLanguages && "${it.id}" !in disabledSources }

        if (sources.isEmpty()) {
            return@coroutineScope null
        }

        val lastId = lastSuccessfulSourceId.get()
        val sortedSources = sources.sortedByDescending { if (it.id == lastId) 1 else 0 }

        val searchedCount = AtomicInteger(0)
        val normalCount = AtomicInteger(0)
        val hasErrorOrTimeout = AtomicBoolean(false)
        val anySourceSearched = AtomicBoolean(false)

        val result = try {
            withTimeoutOrNull(9000) {
                coroutineScope {
                    val semaphore = Semaphore(8)
                    val resultDeferred = CompletableDeferred<Long?>()
                    val totalSources = sortedSources.size
                    val finishedSourcesCount = AtomicInteger(0)

                    sortedSources.forEachIndexed { index, source ->
                        async {
                            if (resultDeferred.isCompleted) return@async
                            semaphore.withPermit {
                                if (resultDeferred.isCompleted) return@async
                                anySourceSearched.set(true)
                                searchedCount.incrementAndGet()

                                var sourceNormal = true
                                try {
                                    val sourceResult = withTimeoutOrNull(6000) {
                                        withContext(coroutineDispatcher) {
                                            var foundId: Long? = null
                                            for (q in generateQueries(query)) {
                                                if (resultDeferred.isCompleted) break
                                                try {
                                                    val page = source.getSearchAnime(1, q, source.getFilterList())
                                                    val candidateAnimes = page.animes.take(5)
                                                    for (sAnime in candidateAnimes) {
                                                        val score = TitleMatcher.score(titles, sAnime.title)
                                                        if (score >= TitleMatcher.ACCEPT_THRESHOLD) {
                                                            val domainAnime = networkToLocalAnime.await(sAnime.toDomainAnime(source.id))
                                                            foundId = domainAnime.id
                                                            break
                                                        }
                                                    }
                                                } catch (_: Exception) {}
                                                if (foundId != null) break
                                            }
                                            foundId
                                        }
                                    }
                                    if (sourceResult == null) {
                                        // Could be timeout or null result. If timeout of 6000ms triggered, withTimeoutOrNull returns null.
                                        // Wait, did it timeout or return null results? To be robust against timeout, let's track if it exceeded or threw.
                                        // Actually withTimeoutOrNull returns null on timeout. Let's consider timeout as non-normal.
                                        sourceNormal = false
                                        hasErrorOrTimeout.set(true)
                                    } else {
                                        val domainAnimeId = sourceResult
                                        if (resultDeferred.complete(domainAnimeId)) {
                                            cache[normalizedKey] = domainAnimeId
                                            lastSuccessfulSourceId.set(source.id)
                                        }
                                        normalCount.incrementAndGet()
                                        return@withPermit
                                    }
                                } catch (_: Exception) {
                                    sourceNormal = false
                                    hasErrorOrTimeout.set(true)
                                } finally {
                                    if (sourceNormal) {
                                        normalCount.incrementAndGet()
                                    }
                                    if (finishedSourcesCount.incrementAndGet() == totalSources) {
                                        if (!resultDeferred.isCompleted) {
                                            resultDeferred.complete(null)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    resultDeferred.await()
                }
            }
        } catch (_: Exception) {
            hasErrorOrTimeout.set(true)
            null
        }

        if (result == null) {
            val searched = searchedCount.get()
            val normals = normalCount.get()
            val hadError = hasErrorOrTimeout.get()
            // "only store a "not found" in the miss cache when EVERY searched source finished normally (no timeout, no exception) AND at least one source was searched. If any source timed out or failed, do NOT cache a miss."
            if (anySourceSearched.get() && searched > 0 && normals == searched && !hadError) {
                failedCache[normalizedKey] = System.currentTimeMillis()
            }
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
