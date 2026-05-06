import dev.vladislav.wikirace.WikiPath
import dev.vladislav.wikirace.WikiRacer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

class SearchMultiThreadedTest {
    /**
     * Multi-threaded search must return a *shortest* path of the same length as the single-threaded
     * version.  Due to scheduling non-determinism the exact intermediate pages may differ when there
     * are multiple equally-short paths, so we only assert:
     *   - the number of steps is correct,
     *   - the first and last pages are correct (or the result is NOT_FOUND when expected).
     */
    @ParameterizedTest
    @MethodSource("searchTestData")
    fun searchTestMultiThread(expectedSteps: Int, startPage: String, destPage: String, searchDepth: Int) {
        val searchResult = WikiRacer.get(4).race(startPage, KOTLIN_PAGE, searchDepth)

        assertEquals(
            expectedSteps,
            searchResult.steps,
            "From '$startPage' to '$KOTLIN_PAGE': expected $expectedSteps steps.",
        )

        if (expectedSteps >= 0) {
            assertEquals(
                startPage.replace(' ', '_'),
                searchResult.path.first(),
                "Path must start with '$startPage'.",
            )
            assertEquals(
                destPage.replace(' ', '_'),
                searchResult.path.last(),
                "Path must end with '$destPage'.",
            )
            // Every page in the path must be a non-empty slug without anchors
            searchResult.path.forEachIndexed { i, slug ->
                assertTrue(slug.isNotBlank(), "Path[$i] must not be blank.")
                assertTrue('#' !in slug, "Path[$i] '$slug' must not contain an anchor.")
            }
        } else {
            assertEquals(WikiPath.NOT_FOUND, searchResult, "Expected NOT_FOUND.")
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
                Arguments.of(0, KOTLIN_PAGE, KOTLIN_PAGE, 2),
                Arguments.of(1, JETBRAINS_PAGE, KOTLIN_PAGE, 2),
                Arguments.of(2, AVL_PAGE, KOTLIN_PAGE, 2),
                // NOT_FOUND with depth=1: just checks Bremen's direct references (fast)
                Arguments.of(WikiPath.NOT_FOUND.steps, BREMEN_PAGE, KOTLIN_PAGE, 1),
            )
    }
}
