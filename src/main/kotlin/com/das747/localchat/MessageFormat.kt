package com.das747.localchat


import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class Metadata(val timestamp: Instant, val author: String)

@Serializable
data class Message(val text: String, val meta: Metadata)

