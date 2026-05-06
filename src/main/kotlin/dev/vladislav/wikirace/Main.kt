package dev.vladislav.wikirace

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.prompt
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.options.validate
import com.github.ajalt.clikt.parameters.types.int
import kotlin.system.exitProcess

class WikiRaceCommand : CliktCommand() {
    private val searchDepth by option("--search-depth").int().required()
        .validate { require(it > 0) { "--search-depth must be > 0" } }
    private val maxThreads by option("--max-threads").int().default(1)
        .validate { require(it > 0) { "--max-threads must be > 0" } }
    private val start by option("--start").prompt("Start page")
    private val final by option("--final").prompt("Final page")

    override fun run() {
        val path = WikiRacer.get(maxThreads).race(start, final, searchDepth)
        if (path == WikiPath.NOT_FOUND) {
            echo("No path found from '$start' to '$final' within depth $searchDepth.")
            exitProcess(1)
        }
        echo(path.path.joinToString(" -> "))
    }
}

fun main(args: Array<String>) = WikiRaceCommand().main(args)
