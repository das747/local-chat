package com.das747.localchat

interface UserOutputProvider {
    fun writeMessage(message: MessageData)

    fun writeSystemMessage(message: String)
}