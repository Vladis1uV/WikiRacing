package dev.vladislav.wikirace

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.UserAgent
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap

class Racer(private val maxThreads: Int) : WikiRacer {
    companion object {
        private const val WIKI_API_BASE_URL = "https://en.wikipedia.org/w/api.php"
    }

    private val refCache = ConcurrentHashMap<String, List<String>>()

    private val client =
        HttpClient(CIO) {
            install(ContentNegotiation.Plugin) {
                json(Json { ignoreUnknownKeys = true })
            }
            install(UserAgent) {
                agent = "WikiRacer/1.0 (https://github.com/Vladis1uV; ogloblinvlad6@gmail.com)"
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 30_000
                connectTimeoutMillis = 30_000
                socketTimeoutMillis = 30_000
            }
            install(HttpRequestRetry) {
                maxRetries = 5
                retryIf { _, response -> response.status.value == 429 || response.status.value in 500..599 }
                retryOnExceptionIf(maxRetries = 5) { _, cause -> cause !is kotlinx.coroutines.CancellationException }
                exponentialDelay(base = 2.0, maxDelayMs = 10_000)
            }
        }

    private suspend fun fetchReferences(page: String): List<String> {
        val normalized = page.replace(' ', '_')
        refCache[normalized]?.let { return it }

        val response: WikiResponse =
            client.get(WIKI_API_BASE_URL) {
                parameter("action", "parse")
                parameter("page", normalized)
                parameter("prop", "links")
                parameter("format", "json")
                parameter("formatversion", "2")
                parameter("redirects", "1")
            }.body()
        val refs =
            response.parse?.links.orEmpty()
                .filter { it.ns == 0 }
                .map { it.title.replace(' ', '_') }
                .filter { it != normalized && it != "Main_Page" }
                .distinct()
        refCache[normalized] = refs
        return refs
    }

    override fun getReferences(page: String): List<String> =
        runBlocking {
            fetchReferences(page)
        }

    private fun reconstruct(parents: MutableMap<String, String>, start: String, destination: String): WikiPath {
        val path: MutableList<String> = mutableListOf()
        var current: String? = destination
        while (current != null) {
            path.add(current)
            if (current == start) break
            current = parents[current]
        }
        return WikiPath(path.reversed())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val dispatcher = Dispatchers.IO.limitedParallelism(maxThreads)

    override fun race(startPage: String, destinationPage: String, searchDepth: Int): WikiPath =
        runBlocking {
            val start = startPage.replace(' ', '_')
            val destination = destinationPage.replace(' ', '_')
            if (start == destination) return@runBlocking WikiPath(listOf(start))
            if (searchDepth == 0) return@runBlocking WikiPath.NOT_FOUND
            var frontier: List<String> = listOf(start)
            val visited: MutableSet<String> = mutableSetOf(start)
            val parents: MutableMap<String, String> = HashMap()
            repeat(searchDepth) {
                val results: List<Pair<String, List<String>>> =
                    frontier.map { page ->
                        async(dispatcher) { page to fetchReferences(page) }
                    }.awaitAll()
                val nextFrontier: MutableList<String> = mutableListOf()
                for ((page, refs) in results) {
                    for (ref in refs) {
                        if (ref in visited) continue
                        visited.add(ref)
                        nextFrontier.add(ref)
                        parents[ref] = page
                        if (ref == destination) return@runBlocking reconstruct(parents, start, destination)
                    }
                }
                if (nextFrontier.isEmpty()) return@runBlocking WikiPath.NOT_FOUND
                frontier = nextFrontier
            }
            WikiPath.NOT_FOUND
        }
}
