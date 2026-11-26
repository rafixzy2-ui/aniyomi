package eu.kanade.tachiyomi.ui.discover

/**
 * Strict title matching. Returns a score from 0.0 to 1.0. Accept only >= ACCEPT_THRESHOLD.
 * Ignores case, punctuation and filler words. The set of numbers must be identical, so
 * "Season 2" never matches the first season. Words are compared with Jaccard similarity.
 */
object TitleMatcher {

    const val ACCEPT_THRESHOLD = 0.8

    private val separators = Regex("[^\\p{L}\\p{N}]+")
    private val ordinal = Regex("^(\\d+)(st|nd|rd|th)$")
    private val noise = setOf(
        "the", "a", "an", "of", "season", "part", "cour", "tv",
        "dub", "sub", "dubbed", "subbed", "hd", "uncensored",
    )
    private val roman = mapOf("ii" to "2", "iii" to "3", "iv" to "4")

    private fun isNumber(token: String): Boolean = token.all { it.isDigit() }

    private fun tokens(title: String): List<String> {
        return title.lowercase()
            .replace("&", " and ")
            .split(separators)
            .filter { it.isNotEmpty() }
            .map { token ->
                val ordinalNumber = ordinal.matchEntire(token)?.groupValues?.get(1)
                ordinalNumber ?: roman[token] ?: token
            }
            .filter { it !in noise }
    }

    private fun scoreOne(target: String, candidate: String): Double {
        val a = tokens(target)
        val b = tokens(candidate)
        if (a.isEmpty() || b.isEmpty()) return 0.0

        val numbersA = a.filter { isNumber(it) }.toSet()
        val numbersB = b.filter { isNumber(it) }.toSet()
        if (numbersA != numbersB) return 0.0

        val wordsA = a.filterNot { isNumber(it) }.toSet()
        val wordsB = b.filterNot { isNumber(it) }.toSet()
        if (wordsA.isEmpty() || wordsB.isEmpty()) {
            return if (wordsA == wordsB) 1.0 else 0.0
        }

        val shared = wordsA.intersect(wordsB).size
        val all = wordsA.union(wordsB).size
        return shared.toDouble() / all
    }

    /** Best score of [candidate] against any of the [targets] (english, romaji, synonyms...). */
    fun score(targets: List<String>, candidate: String): Double {
        return targets
            .filter { it.isNotBlank() }
            .maxOfOrNull { scoreOne(it, candidate) }
            ?: 0.0
    }
}
