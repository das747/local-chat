package com.das747.localchat.client

import com.das747.localchat.Message
import com.das747.localchat.Metadata
import com.das747.localchat.io.UserInputProvider
import com.das747.localchat.io.UserOutputProvider
import com.das747.localchat.application.ReceivedMessage
import kotlinx.datetime.Clock

open class LocalClient(
    private val input: UserInputProvider,
    private val output: UserOutputProvider,
    private val name: String
) : Client {
    override val id: ClientId = -1

    override fun sendMessage(message: ReceivedMessage) {
        output.writeMessage(message.first)
    }

    override fun readMessage(): Message? {
        val message = input.getInput() ?: return null
        return Message(message, Metadata(Clock.System.now(), name))
    }

}