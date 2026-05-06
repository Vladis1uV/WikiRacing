package dev.vladislav.wikirace

import kotlinx.serialization.Serializable

@Serializable
data class Parse(val links: List<Link>)
