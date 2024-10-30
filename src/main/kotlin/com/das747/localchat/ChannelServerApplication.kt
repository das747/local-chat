@file:OptIn(ExperimentalCoroutinesApi::class)

package com.das747.localchat

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.selects.whileSelect
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

typealias ReceivedMessage = Pair<MessageData, ClientId>

class ChannelServerApplication(
    private val input: UserInputProvider,
    private val output: UserOutputProvider
) : Application {

    companion object {
        val logger: Logger = LoggerFactory.getLogger(FlowServerApplication::class.java)
    }

    private val localId = 0
    private val user = "aboba"

    private data class Client(
        val socket: Socket,
        val inputQueue: ReceiveChannel<ReceivedMessage>,
        val outputQueue: Channel<ReceivedMessage>
    )

    private fun Client.disconnect() {
        inputQueue.cancel()
        outputQueue.cancel()
        socket.close()
        output.writeSystemMessage("Disconnected ${socket.port}")
    }

    private val clients: MutableSet<Client> = ConcurrentHashMap.newKeySet()


    override suspend fun run() {
        coroutineScope {
            val serverSocket = ServerSocket(5111)
            output.writeSystemMessage("Listening on port ${serverSocket.localPort}")

            val connectionHandler = handleConnections(serverSocket)
            val userInputQueue = produceUserInput()
            val userOutputQueue = Channel<ReceivedMessage>()
            handleUserOutput(userOutputQueue)
                .invokeOnCompletion { logger.debug("user output closed") }
            clients.add(Client(Socket(), userInputQueue, userOutputQueue))
            handleMessageQueues().invokeOnCompletion {
                connectionHandler.cancel()
                serverSocket.close()
                clients.forEach { it.disconnect() }
            }
        }
        output.writeSystemMessage("Exiting application...")
    }


    private fun CoroutineScope.handleMessageQueues() = launch(Dispatchers.IO) {
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
                        receiver.socket.port != localId
                    }
                }
            }
        }
    }

    private fun CoroutineScope.handleConnections(serverSocket: ServerSocket) =
        launch(Dispatchers.IO) {
            try {
                while (true) {
                    val socket = withCancelCheck { serverSocket.accept() }
                    val outputQueue = Channel<ReceivedMessage>()
                    handleOutgoingMessages(socket, outputQueue).invokeOnCompletion {
                        logger.debug("output to ${socket.port} closed")
                    }

                    val inputQueue = produceReceivedMessages(socket)
                    clients.add(Client(socket, inputQueue, outputQueue))
                    output.writeSystemMessage("Connected ${socket.port}")
                }
            } catch (_: SocketException) {
            } finally {
                logger.debug("connection handler stopped")
            }
        }

    private fun CoroutineScope.handleUserOutput(outputQueue: ReceiveChannel<ReceivedMessage>) =
        launch(Dispatchers.IO) {
            logger.debug("started user output")
            for ((message, _) in outputQueue) {
                output.writeMessage(message)
            }
        }

    private fun CoroutineScope.handleOutgoingMessages(
        socket: Socket,
        outputQueue: ReceiveChannel<Pair<MessageData, ClientId>>
    ) = launch(Dispatchers.IO) {
        val socketWriter = PrintWriter(socket.getOutputStream(), true)
        for ((message, clientId) in outputQueue) {
            if (clientId == socket.port) {
                // here we can send ack to the original sender
                continue
            }
            val jsonData = Json.encodeToString(message)
            socketWriter.println(jsonData)
            logger.debug("Sent {} to {}", message, socket.port)
        }
    }

    private fun CoroutineScope.produceUserInput() = produce(Dispatchers.IO) {
        while (true) {
            val userInput = withCancelCheck { input.getInput() } ?: break
            if (userInput.isEmpty()) continue
            logger.debug("Read \"$userInput\" from input")
            val messageData = MessageData(userInput, MetaData(Clock.System.now(), user))
            send(messageData to localId)
        }
        logger.debug("User input closed")
    }

    private fun CoroutineScope.produceReceivedMessages(socket: Socket) = produce(Dispatchers.IO) {
        try {
            val socketReader = BufferedReader(InputStreamReader(socket.getInputStream()))
            while (true) {
                try {
                    val jsonData = withCancelCheck { socketReader.readLine() } ?: break
                    val messageData = Json.decodeFromString<MessageData>(jsonData)
                    send(messageData to socket.port)
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

private fun <T> CoroutineScope.withCancelCheck(block: () -> T): T {
    ensureActive()
    val result = block()
    ensureActive()
    return result
}