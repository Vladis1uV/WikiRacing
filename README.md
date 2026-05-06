# WikiRacer

A Kotlin command-line tool that finds the shortest chain of `/wiki/` links between two Wikipedia articles.
It's the classic [Wikipedia race](https://en.wikipedia.org/wiki/Wikipedia:Wikirace) solved by BFS over the live link
graph, with bounded coroutine concurrency to keep the search fast without hammering the API.

```
$ ./wikiRacer --search-depth 3 --max-threads 8 --start JetBrains --final "Kotlin (programming language)"
JetBrains -> Kotlin_(programming_language)
```

## How it works

The search is a level-synchronous BFS rooted at the start page. At each depth, the frontier is expanded in parallel:
every page in the current level is fetched concurrently, and the union of their outgoing links becomes the next
frontier. The first time the destination appears, BFS guarantees the path is one of the shortest possible.

A few design choices worth calling out:

- **Wikipedia action API instead of HTML scraping.** `action=parse&prop=links` returns structured link data with
  namespace tags. Filtering on `ns == 0` cleanly drops `File:`, `Help:`, `Template:`, `Category:`, etc. without
  maintaining a hardcoded prefix blocklist.
- **Coroutines over raw threads.** Parallelism is bounded by `Dispatchers.IO.limitedParallelism(maxThreads)`, so
  `--max-threads` is a real cap on in-flight HTTP requests. This is much cleaner than managing a thread pool by hand
  and makes the single- vs. multi-threaded path the same code.
- **Per-page reference cache.** A `ConcurrentHashMap<String, List<String>>` deduplicates work across the BFS — useful
  in practice because the same hub pages (countries, years, common topics) show up repeatedly.
- **Retries with exponential backoff.** The HTTP client retries on 429 and 5xx responses, which Wikipedia returns
  occasionally under sustained load.
- **Polite client.** The Ktor client sets a descriptive `User-Agent` per Wikipedia's
  [API etiquette](https://meta.wikimedia.org/wiki/User-Agent_policy).

Path reconstruction uses a `parents` map written during BFS, walked backwards from the destination once it's found.

## Tech stack

- **Kotlin** on JVM 21
- **Ktor** (CIO engine) + **kotlinx.serialization** for the Wikipedia API client
- **kotlinx.coroutines** for bounded parallel fetching
- **Clikt** for the CLI
- **JUnit 5** for tests, **ktlint** for style

## Build & run

```bash
# Build the fat JAR and install the ./wikiRacer wrapper script
./gradlew installCli

# Run directly
./wikiRacer --search-depth 3 --max-threads 8 --start JetBrains --final Kotlin

# Or run via Gradle
./gradlew run --args='--search-depth 2 --max-threads 4 --start JetBrains --final "Kotlin (programming language)"'
```

CLI arguments:

| Flag             | Description                                                            | Default |
|------------------|------------------------------------------------------------------------|---------|
| `--search-depth` | Maximum BFS depth (must be > 0)                                        | —       |
| `--max-threads`  | Upper bound on concurrent fetches                                      | `1`     |
| `--start`        | Source article title; prompts on stdin if omitted                      | —       |
| `--final`        | Destination article title; prompts on stdin if omitted                 | —       |

The process exits with code `1` when no path is found within the depth limit.

## Tests

```bash
./gradlew test         # all unit tests
./gradlew testCli      # end-to-end CLI tests (builds the shadow JAR first)
./gradlew ktlintCheck  # style
```

## Notes & limitations

- Depth grows the frontier exponentially. Depths above ~3 hit Wikipedia hard; the cache and `--max-threads` cap help,
  but this is a search tool, not a crawler.
- Only the English Wikipedia (`en.wikipedia.org`) is supported — switching domains is a one-line change in
  `Racer.kt`.
- The first hit at any BFS level is *a* shortest path, not *the* unique one. Wikipedia's link graph has many ties.
