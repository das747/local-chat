package com.das747.localchat

import com.das747.localchat.application.Application
import com.das747.localchat.application.ClientApplication
import com.das747.localchat.application.server.ChannelServerApplication
import com.das747.localchat.application.server.FlowServerApplication
import com.das747.localchat.io.StdinInputProvider
import com.das747.localchat.io.StdoutOutputProvider
import com.das747.localchat.io.UserInputProvider
import com.das747.localchat.io.UserOutputProvider
import kotlinx.coroutines.runBlocking

fun UserInputProvider.getNonEmptyString(): String? {
    return getInput()?.ifBlank { getNonEmptyString() }
}

fun createApplication(
    input: UserInputProvider,
    output: UserOutputProvider,
    name: String
): Application? {
    while (true) {
        output.writeSystemMessage("Do you want to start a new chat or join existing? [join/start]")
        val type = input.getNonEmptyString() ?: return null
        when (type) {
            "join" -> return ClientApplication(input, output, name)
            "start" -> return when (System.getProperty("localChat.server", "flow")) {
                "channel" -> ChannelServerApplication(input, output, name)
                else -> FlowServerApplication(input, output, name)
            }
        }
    }
}

fun main() {
    val input = StdinInputProvider()
    val output = StdoutOutputProvider()
    output.writeSystemMessage("Please enter your nickname:")
    val name = input.getNonEmptyString() ?: return
    val app = createApplication(input, output, name) ?: return
    runBlocking {
        app.run()
    }
}