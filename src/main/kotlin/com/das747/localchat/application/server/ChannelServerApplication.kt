@file:OptIn(ExperimentalCoroutinesApi::class)

package com.das747.localchat.application.server

import com.das747.localchat.client.Client
import com.das747.localchat.io.UserInputProvider
import com.das747.localchat.io.UserOutputProvider
import com.das747.localchat.application.ReceivedMessage
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.selects.whileSelect
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.ServerSocket
import java.util.concurrent.ConcurrentHashMap

class ChannelServerApplication(
    input: UserInputProvider,
    output: UserOutputProvider,
    name: String
) : ServerApplicationBase(input, output, name) {

    override val logger: Logger = LoggerFactory.getLogger(FlowServerApplication::class.java)

    private data class ClientData(
        val client: Client,
        val inputQueue: ReceiveChannel<ReceivedMessage>,
        val outputQueue: Channel<ReceivedMessage>
    )

    private fun ClientData.disconnect() {
        inputQueue.cancel()
        outputQueue.cancel()
        client.close()
        output.writeSystemMessage("Disconnected ${client.id}")
    }

    private val clients: MutableSet<ClientData> = ConcurrentHashMap.newKeySet()

    override suspend fun run() {
        coroutineScope {
            val serverSocket = ServerSocket(0)
            output.writeSystemMessage("Listening on port ${serverSocket.localPort}")
            val connectionHandler = launchConnectionHandler(serverSocket)

            connectClient(localClient)

            launchQueueHandler().invokeOnCompletion {
                connectionHandler.cancel()
                serverSocket.close()
                clients.forEach { it.disconnect() }
            }
        }
        output.writeSystemMessage("Exiting application...")
    }


    private fun CoroutineScope.launchQueueHandler() = launch(Dispatchers.IO) {
        whileSelect {
            for (receiver in clients) {
                receiver.inputQueue.onReceiveCatching {
                    it.getOrNull()?.let { message ->
                        for (sender in clients) {
                            sender.outputQueue.send(message)
                        }
                        true
                    } ?: let {
                        receiver.disconnect()
                        clients.remove(receiver)
                        receiver.client.id != localClient.id
                    }
                }
            }
        }
    }

    override fun CoroutineScope.connectClient(client: Client) {
        val outputQueue = Channel<ReceivedMessage>()
        launchOutputHandler(client) { handler ->
            for (message in outputQueue) {
                handler(message)
            }
        }

        val inputQueue = produce(Dispatchers.IO, 10) {
            processIncomingMessages(client) {
                send(it)
            }
            logger.debug("closing input from ${client.id}")
        }
        clients.add(ClientData(client, inputQueue, outputQueue))
    }
}