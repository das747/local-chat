package com.das747.localchat.client

import com.das747.localchat.Message
import com.das747.localchat.application.ReceivedMessage

typealias ClientId = Int

interface Client {
    val id: ClientId

    fun sendMessage(message: ReceivedMessage)

    fun readMessage(): Message?

    fun close() = Unit

}