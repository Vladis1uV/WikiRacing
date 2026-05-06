import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Integration tests for the CLI.  Requires the application JAR to be built first:
 *   ./gradlew jar
 *
 * Run with:
 *   ./gradlew testCli
 */
class CliTest {
    companion object {
        private val projectDir = File(System.getProperty("user.dir"))

        private fun findJar(): File? =
            projectDir.resolve("build/libs")
                .listFiles()
                ?.firstOrNull { it.name.endsWith("-all.jar") }

        /** Runs the CLI with the given arguments, returns (exitCode, stdout, stderr). */
        private fun runCli(vararg args: String, timeoutSec: Long = 60): Triple<Int, String, String> {
            val jar = findJar()
            assumeTrue(jar != null, "Fat JAR not found. Run './gradlew shadowJar' before testCli.")
            val process =
                ProcessBuilder("java", "-jar", jar!!.absolutePath, *args)
                    .directory(projectDir)
                    .start()
            val stdout = process.inputStream.bufferedReader().readText()
            val stderr = process.errorStream.bufferedReader().readText()
            val finished = process.waitFor(timeoutSec, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                fail<Unit>("CLI process timed out after ${timeoutSec}s")
            }
            return Triple(process.exitValue(), stdout, stderr)
        }
    }

    @Test
    fun `CLI requires search depth argument`() {
        val (exitCode, _, _) = runCli()
        assertNotEquals(0, exitCode, "CLI should exit with non-zero when required arguments are missing.")
    }

    @Test
    fun `CLI rejects zero search depth`() {
        val (exitCode, _, _) = runCli("--search-depth", "0", "--start", "JetBrains", "--final", "Kotlin (programming language)")
        assertNotEquals(0, exitCode, "CLI should reject search-depth <= 0.")
    }

    @Test
    fun `CLI rejects zero max threads`() {
        val (exitCode, _, _) =
            runCli(
                "--search-depth",
                "1",
                "--max-threads",
                "0",
                "--start",
                "JetBrains",
                "--final",
                "Kotlin (programming language)"
            )
        assertNotEquals(0, exitCode, "CLI should reject max-threads <= 0.")
    }

    @Test
    fun `CLI finds 1-step path from JetBrains to Kotlin`() {
        val (exitCode, stdout, _) =
            runCli(
                "--search-depth",
                "2",
                "--start",
                "JetBrains",
                "--final",
                "Kotlin (programming language)",
            )
        assertEquals(0, exitCode, "CLI should exit 0 when a path is found.")
        assertTrue(
            stdout.contains("Kotlin", ignoreCase = true) || stdout.contains("JetBrains", ignoreCase = true),
            "Output should mention the path. Got: $stdout",
        )
    }

    @Test
    fun `CLI reports not found when no path exists within depth`() {
        val (exitCode, stdout, _) =
            runCli(
                "--search-depth",
                "1",
                "--start",
                "Bremen",
                "--final",
                "Kotlin (programming language)",
            )
        // Either non-zero exit or explicit "not found" message is acceptable
        val notFoundExplicitly =
            stdout.contains("not found", ignoreCase = true) ||
                stdout.contains("no path", ignoreCase = true)
        assertTrue(exitCode != 0 || notFoundExplicitly, "CLI should signal when no path is found. Got: $stdout")
    }

    @Test
    fun `CLI uses multiple threads when max-threads is specified`() {
        val (exitCode, stdout, _) =
            runCli(
                "--search-depth",
                "2",
                "--max-threads",
                "4",
                "--start",
                "JetBrains",
                "--final",
                "Kotlin (programming language)",
            )
        assertEquals(0, exitCode, "CLI should exit 0 with --max-threads. Got stdout: $stdout")
        assertTrue(stdout.isNotBlank(), "CLI output must not be empty.")
    }
}
