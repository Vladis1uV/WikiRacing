package dev.vladislav.wikirace

import kotlinx.serialization.Serializable

@Serializable
data class WikiResponse(val parse: Parse? = null)
