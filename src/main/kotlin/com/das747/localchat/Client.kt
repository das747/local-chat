package com.das747.localchat

typealias ClientId = Int


interface Client {
    val id: ClientId

    fun sendMessage(message: ReceivedMessage)

    fun readMessage(): Message?

    fun close() = Unit

}