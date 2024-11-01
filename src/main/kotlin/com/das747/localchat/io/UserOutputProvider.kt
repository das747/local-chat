package com.das747.localchat.io

import com.das747.localchat.Message

interface UserOutputProvider {
    fun writeMessage(message: Message)

    fun writeSystemMessage(message: String)
}