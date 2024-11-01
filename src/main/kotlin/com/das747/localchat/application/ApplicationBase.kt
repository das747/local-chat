package com.das747.localchat.application

import com.das747.localchat.*
import com.das747.localchat.client.Client
import com.das747.localchat.client.ClientId
import com.das747.localchat.client.LocalClient
import com.das747.localchat.io.UserInputProvider
import com.das747.localchat.io.UserOutputProvider
import kotlinx.coroutines.*
import org.slf4j.Logger

typealias ReceivedMessage = Pair<Message, ClientId>


abstract class ApplicationBase(
    protected val input: UserInputProvider,
    protected val output: UserOutputProvider,
    name: String
) : Application {

    protected abstract val logger: Logger

    protected val user = "aboba"

    protected val localClient = LocalClient(input, output, name)

    protected fun CoroutineScope.launchOutputHandler(
        client: Client,
        loop: suspend (suspend (ReceivedMessage) -> Unit) -> Unit
    ) = launch(Dispatchers.IO) {
        logger.debug("starting output to ${client.id}")
        loop { message ->
            client.sendMessage(message)
        }
    }.also { it.invokeOnCompletion { logger.debug("output to ${client.id} closed") } }

    protected suspend fun processIncomingMessages(
        client: Client,
        processor: suspend (ReceivedMessage) -> Unit
    ) = coroutineScope {
        logger.debug("starting input from ${client.id}")
        while (true) {
            try {
                val message = withCancelCheck { client.readMessage() } ?: break
                processor(message to client.id)
            } catch (e: IllegalArgumentException) {
                output.writeSystemMessage("Failed to decode message from ${client.id}")
            }
        }
//        logger.debug("closing input from ${client.id}")
    }
}

fun <T> CoroutineScope.withCancelCheck(block: () -> T): T {
    ensureActive()
    val result = block()
    ensureActive()
    return result
}