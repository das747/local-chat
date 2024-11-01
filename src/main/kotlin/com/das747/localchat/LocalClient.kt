package com.das747.localchat

import kotlinx.datetime.Clock

open class LocalClient(
    private val input: UserInputProvider,
    private val output: UserOutputProvider
) : Client {
    override val id: ClientId = -1
    private val name = "aboba"

    override fun sendMessage(message: ReceivedMessage) {
        output.writeMessage(message.first)
    }

    override fun readMessage(): Message? {
        val message = input.getInput() ?: return null
        return Message(message, Metadata(Clock.System.now(), name))
    }

}