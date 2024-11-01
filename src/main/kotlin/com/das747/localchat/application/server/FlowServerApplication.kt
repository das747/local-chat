package com.das747.localchat.application.server

import com.das747.localchat.*
import com.das747.localchat.client.Client
import com.das747.localchat.client.ClientId
import com.das747.localchat.io.UserInputProvider
import com.das747.localchat.io.UserOutputProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.ServerSocket
import java.util.concurrent.ConcurrentHashMap

class FlowServerApplication(
    input: UserInputProvider,
    output: UserOutputProvider,
    name: String
) : ServerApplicationBase(input, output, name) {

    override val logger: Logger = LoggerFactory.getLogger(FlowServerApplication::class.java)

    private val clients: MutableSet<Client> = ConcurrentHashMap.newKeySet()

    private val messageQueue = MutableSharedFlow<Pair<Message, ClientId>>(
        replay = 100,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private val outputQueue = messageQueue.asSharedFlow()



    override suspend fun run() {
        coroutineScope {
            val serverSocket = ServerSocket(0)
            output.writeSystemMessage("Listening on port ${serverSocket.localPort}")

            val connectionHandler = launchConnectionHandler(serverSocket)
            connectClient(object : Client by localClient {
                override fun close() {
                    super.close()
                    connectionHandler.cancel()
                    serverSocket.close()
                    clients.forEach { client ->
                        if (client != this) {
                            client.close()
                            output.writeSystemMessage("Disconnected ${client.id}")
                        }
                    }
                }
            })
        }
        output.writeSystemMessage("Exiting application...")
    }

    override fun CoroutineScope.connectClient(client: Client) {
        val outputHandler = launchOutputHandler(client) { handler ->
            outputQueue.collect { message ->
                handler(message)
            }
        }

        launch(Dispatchers.IO) {
            processIncomingMessages(client) { message ->
                messageQueue.emit(message)
            }
            outputHandler.cancel()
            client.close()
            clients.remove(client)
            output.writeSystemMessage("Disconnected ${client.id}")
        }

        clients.add(client)
    }
}