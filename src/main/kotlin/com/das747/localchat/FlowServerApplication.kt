package com.das747.localchat

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.datetime.Clock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.ConcurrentHashMap

class FlowServerApplication(
    private val input: UserInputProvider,
    private val output: UserOutputProvider
) : Application {

    companion object {
        val logger: Logger = LoggerFactory.getLogger(FlowServerApplication::class.java)
    }

    private val localId = -1
    private val user = "aboba"

    private val clients: MutableSet<Socket> = ConcurrentHashMap.newKeySet()

    private val messageQueue = MutableSharedFlow<Pair<MessageData, ClientId>>(
        replay = 100,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private val outputQueue = messageQueue.asSharedFlow()


    override suspend fun run() {
        coroutineScope {
            val serverSocket = ServerSocket(0)
            output.writeSystemMessage("Listening on port ${serverSocket.localPort}")

            val connectionHandler = launch(Dispatchers.IO) { handleConnections(serverSocket) }

            val outputHandler = launch(Dispatchers.IO) { handleUserOutput() }
                .apply { invokeOnCompletion { logger.debug("user output closed") } }

            launch {
                handleUserInput()
                connectionHandler.cancel()
                outputHandler.cancel()
                serverSocket.close()
                clients.forEach { it.close() }
            }
        }
        output.writeSystemMessage("Exiting application...")
    }


    private suspend fun handleConnections(serverSocket: ServerSocket) = coroutineScope {
        try {
            while (true) {
                val socket = withCancelCheck { serverSocket.accept() }

                val outputHandler = launch { handleOutgoingMessages(socket) }
                    .apply { invokeOnCompletion { logger.debug("output to ${socket.port} closed") } }

                launch {
                    handleIncomingMessages(socket)
                    outputHandler.cancel()
                    socket.close()
                    clients.remove(socket)
                    output.writeSystemMessage("Disconnected ${socket.port}")
                }

                clients.add(socket)
                output.writeSystemMessage("Connected ${socket.port}")
            }
        } catch (_: SocketException) {
        } finally {
            logger.debug("connection handler stopped")
        }
    }

    private suspend fun handleUserOutput(): Nothing {
        logger.debug("started user output")
        outputQueue.collect { (message, clientId) ->
            output.writeMessage(message)
        }
    }

    private suspend fun handleOutgoingMessages(socket: Socket): Nothing = coroutineScope {
        val socketWriter = PrintWriter(socket.getOutputStream(), true)
        outputQueue.collect { (message, clientId) ->
            if (clientId == socket.port) {
                // here we can send ack to the original sender
                return@collect
            }
            val jsonData = Json.encodeToString(message)
            socketWriter.println(jsonData)
            logger.debug("Sent {} to {}", message, socket.port)
        }
    }

    private suspend fun handleUserInput() = coroutineScope {
        while (true) {
            val userInput = withCancelCheck { input.getInput() } ?: break
            if (userInput.isEmpty()) continue
            logger.debug("Read \"$userInput\" from input")
            val messageData = MessageData(userInput, MetaData(Clock.System.now(), user))
            messageQueue.emit(messageData to localId)
        }
        logger.debug("User input closed")
    }

    private suspend fun handleIncomingMessages(socket: Socket) = coroutineScope {
        try {
            val socketReader = BufferedReader(InputStreamReader(socket.getInputStream()))
            while (true) {
                try {
                    val jsonData = withCancelCheck { socketReader.readLine() } ?: break
                    val messageData = Json.decodeFromString<MessageData>(jsonData)
                    messageQueue.emit(messageData to socket.port)
                    logger.debug("Read {} from {}", messageData, socket.port)
                } catch (e: IllegalArgumentException) {
                    output.writeSystemMessage("Failed to decode message from ${socket.port}")
                }
            }
        } catch (_: SocketException) {
        } finally {
            logger.debug("closing input from ${socket.port}")
        }
    }
}

internal fun <T> CoroutineScope.withCancelCheck(block: () -> T): T {
    ensureActive()
    val result = block()
    ensureActive()
    return result
}