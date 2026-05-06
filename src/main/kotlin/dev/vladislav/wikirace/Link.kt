package dev.vladislav.wikirace

import kotlinx.serialization.Serializable

@Serializable
data class Link(val ns: Int, val exists: Boolean = false, val title: String)
