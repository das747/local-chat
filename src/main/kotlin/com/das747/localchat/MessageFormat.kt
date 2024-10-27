package com.das747.localchat


import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class MetaData(val timestamp: Instant, val author: String)

@Serializable
data class MessageData(val message: String, val meta: MetaData)

