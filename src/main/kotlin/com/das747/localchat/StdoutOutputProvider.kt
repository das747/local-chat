package com.das747.localchat

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class StdoutOutputProvider : UserOutputProvider {
    override fun writeMessage(message: MessageData) {
        val time = message.meta.timestamp.toLocalDateTime(TimeZone.currentSystemDefault())
        println("[$time] ${message.meta.author}: ${message.message}")
    }

    override fun writeSystemMessage(message: String) {
        println(message)
    }
}