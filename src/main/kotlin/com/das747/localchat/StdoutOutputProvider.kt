package com.das747.localchat

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class StdoutOutputProvider : UserOutputProvider {
    override fun writeMessage(message: Message) {
        val time = message.meta.timestamp.toLocalDateTime(TimeZone.currentSystemDefault())
        println("[$time] ${message.meta.author}: ${message.text}")
    }

    override fun writeSystemMessage(message: String) {
        println("[SYSTEM] $message")
    }
}