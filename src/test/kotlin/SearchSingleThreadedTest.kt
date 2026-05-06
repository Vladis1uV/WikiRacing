import dev.vladislav.wikirace.WikiPath
import dev.vladislav.wikirace.WikiRacer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

class SearchSingleThreadedTest {
    @ParameterizedTest
    @MethodSource("searchTestData")
    fun searchTestSingleThread(
        expectedPathSize: Int,
        startPage: String,
        expectedPath: List<String>,
        searchDepth: Int,
    ) {
        val searchResult = WikiRacer.get(1).race(startPage, KOTLIN_PAGE, searchDepth)
        assertEquals(
            expectedPathSize,
            searchResult.steps,
            "From '$startPage' to '$KOTLIN_PAGE' (depth=$searchDepth): expected $expectedPathSize steps.",
        )
        if (expectedPath.isEmpty()) return
        // Always verify start and end pages.
        assertEquals(
            expectedPath.first().replace(' ', '_'),
            searchResult.path.firstOrNull(),
            "Path must start with '${expectedPath.first()}'.",
        )
        assertEquals(
            expectedPath.last().replace(' ', '_'),
            searchResult.path.lastOrNull(),
            "Path must end with '${expectedPath.last()}'.",
        )
        // Verify intermediate pages only when the full path is explicitly provided.
        // Omitting intermediates is intentional for paths where the exact route depends
        // on link ordering (HTML document order vs. API alphabetical order).
        if (expectedPath.size == searchResult.path.size && expectedPath.size > 2) {
            expectedPath.drop(1).dropLast(1).zip(searchResult.path.drop(1).dropLast(1))
                .forEachIndexed { index, (expected, actual) ->
                    assertEquals(
                        expected.replace(' ', '_'),
                        actual,
                        "Path item at index ${index + 1}: expected '$expected', got '$actual'.",
                    )
                }
        }
    }

    companion object {
        private const val AVL_PAGE = "AVL tree"
        private const val BREMEN_PAGE = "Bremen"
        private const val JETBRAINS_PAGE = "JetBrains"
        private const val KOTLIN_PAGE = "Kotlin (programming language)"

        @JvmStatic
        fun searchTestData() =
            listOf(
                // 0 steps: start equals destination
                Arguments.of(0, KOTLIN_PAGE, listOf(KOTLIN_PAGE), 2),
                // 1 step: JetBrains page directly links to Kotlin (very stable — JetBrains created Kotlin)
                Arguments.of(1, JETBRAINS_PAGE, listOf(JETBRAINS_PAGE, KOTLIN_PAGE), 2),
                // 2 steps: AVL tree → ... → Kotlin
                // Intermediate page not fixed: HTML and API return links in different order.
                Arguments.of(2, AVL_PAGE, listOf(AVL_PAGE, KOTLIN_PAGE), 2),
                // NOT_FOUND with depth=1: just checks Bremen's direct references (1 HTTP call — fast)
                Arguments.of(WikiPath.NOT_FOUND.steps, BREMEN_PAGE, WikiPath.NOT_FOUND.path, 1),
            )
    }
}
