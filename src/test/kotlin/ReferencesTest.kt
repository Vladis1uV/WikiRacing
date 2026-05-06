import dev.vladislav.wikirace.WikiRacer
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

class ReferencesTest {
    /**
     * Tests that getReferences returns the expected slugs for a given page.
     *
     * Two parsing approaches are acceptable:
     *  - **Raw HTML** (e.g. Jsoup `select("[href^=/wiki/]")`): picks up sidebar, navigation, and
     *    footer links in addition to article-body links → typically **150–350** slugs.
     *  - **Wikipedia API** (e.g. `action=parse&prop=links`): returns article-body links only →
     *    typically **50–200** slugs.
     *
     * [htmlMin]..[htmlMax] and [apiMin]..[apiMax] document these expected ranges.
     * The test passes when the actual count falls within *either* range.
     */
    @ParameterizedTest
    @MethodSource("referencesData")
    fun referencesExtractorTest(
        page: String,
        requiredSlugs: List<String>,
        htmlMin: Int,
        htmlMax: Int,
        apiMin: Int,
        apiMax: Int,
    ) {
        val references = WikiRacer.get(1).getReferences(page)

        val inHtmlRange = references.size in htmlMin..htmlMax
        val inApiRange = references.size in apiMin..apiMax
        assertTrue(
            inHtmlRange || inApiRange,
            "For page '$page' expected $htmlMin..$htmlMax (HTML) or $apiMin..$apiMax (API) references, " +
                "got ${references.size}.",
        )

        requiredSlugs.forEach { slug ->
            assertTrue(slug in references, "Slug '$slug' must be present for page '$page'.")
        }

        // No duplicates
        val duplicates = references.groupBy { it }.filter { it.value.size > 1 }.keys
        assertTrue(duplicates.isEmpty(), "References must not contain duplicates, but found: $duplicates")

        // No anchors
        val withAnchor = references.filter { '#' in it }
        assertTrue(withAnchor.isEmpty(), "References must not contain anchors (#), but found: $withAnchor")

        // No forbidden prefixes
        val forbidden =
            listOf(
                "File:", "Wikipedia:", "Help:", "Template:", "Category:", "Special:",
                "Portal:", "User:", "MediaWiki:", "Draft:", "TimedText:", "Module:",
                "Media:", "Template_talk:", "Talk:",
            )
        val withForbidden = references.filter { slug -> forbidden.any { slug.startsWith(it) } }
        assertTrue(withForbidden.isEmpty(), "References must not contain forbidden prefixes, but found: $withForbidden")
    }

    companion object {
        @JvmStatic
        fun referencesData() =
            listOf(
                // Non-existent page must return an empty list regardless of parsing approach
                Arguments.of(
                    "Kotlin (programming language) aaaaa",
                    emptyList<String>(),
                    // HTML range
                    0,
                    0,
                    // API range
                    0,
                    0,
                ),
                // Well-known stable references present in both HTML and API results.
                // HTML scraping (Jsoup) returns ~150-350 links; Wikipedia API returns ~50-200.
                Arguments.of(
                    "Kotlin (programming language)",
                    listOf(
                        "JetBrains",
                        "Java_Community_Process",
                        "James_Gosling",
                    ),
                    // HTML range: sidebar + nav + body links
                    150,
                    400,
                    // API range: body links only
                    50,
                    200,
                ),
            )
    }
}
