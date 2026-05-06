package dev.vladislav.wikirace

interface WikiRacer {
    /**
     * Finds a shortest path of `/wiki/` links from [startPage] to [destinationPage] using BFS,
     * giving up once the search reaches [searchDepth] transitions.
     *
     * @return the path including both endpoints, or [WikiPath.NOT_FOUND] if no path exists within the limit.
     */
    fun race(startPage: String, destinationPage: String, searchDepth: Int): WikiPath

    /**
     * Returns the unique outgoing Wikipedia article slugs from [page] (article namespace only).
     *
     * Slugs are URL-style titles like `JetBrains` or `Kotlin_(programming_language)`. The page itself,
     * the main page, and non-article namespaces are excluded; anchors are stripped. Spaces and underscores
     * in [page] are treated interchangeably. Returns an empty list if the page does not exist.
     */
    fun getReferences(page: String): List<String>

    companion object {
        /** @param maxThreads Upper bound on concurrent fetches during the search. */
        fun get(maxThreads: Int): WikiRacer = Racer(maxThreads)
    }
}
