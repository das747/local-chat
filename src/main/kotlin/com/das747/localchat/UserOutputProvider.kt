package com.das747.localchat

interface UserOutputProvider {
    fun writeMessage(message: Message)

    fun writeSystemMessage(message: String)
}