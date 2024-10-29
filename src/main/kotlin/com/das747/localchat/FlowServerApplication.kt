package com.das747.localchat

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.datetime.Clock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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
            val serverSocket = ServerSocket(5111)
            output.writeSystemMessage("Listening on port ${serverSocket.localPort}")
            val connectionHandler = launch(Dispatchers.IO) {
                handleConnections(serverSocket)
            }
            val outputHandler = launch(Dispatchers.IO) {
                handleUserOutput()
            }
            outputHandler.invokeOnCompletion { output.writeSystemMessage("User output closed") }
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
                val outputHandler = launch {
                    handleOutgoingMessages(socket)
                }
                launch {
                    handleIncomingMessages(socket)
                    outputHandler.cancel()
                }
                outputHandler.invokeOnCompletion {
                    output.writeSystemMessage("closing output to ${socket.port}")
                }
                clients.add(socket)
                output.writeSystemMessage("Connected ${socket.port}")
            }
        } catch (_: SocketException) {
        }
    }

    private suspend fun handleUserOutput(): Nothing = coroutineScope {
        output.writeSystemMessage("started user output")
        outputQueue.collect { (message, _) ->
            output.writeSystemMessage("found $message in queue")
            output.writeMessage(message)
        }
    }

    private suspend fun handleOutgoingMessages(socket: Socket): Nothing = coroutineScope {
        val socketWriter = PrintWriter(socket.getOutputStream(), true)
        outputQueue.collect { (message, clientId) ->
            if (clientId != socket.port) {
                val jsonData = Json.encodeToString(message)
                socketWriter.println(jsonData)
//                  output.writeSystemMessage("Sent $message to ${socket.port}")
            } // here we can send ack to the original sender
        }
    }

    private suspend fun handleUserInput() = coroutineScope {
        while (true) {
            val userInput = withCancelCheck { input.getInput() } ?: break
            if (userInput.isEmpty()) continue
//            output.writeSystemMessage("Read \"$userInput\" from input ")
            val messageData = MessageData(userInput, MetaData(Clock.System.now(), user))
            messageQueue.emit(messageData to localId)
        }
        output.writeSystemMessage("User input closed")
    }

    private suspend fun handleIncomingMessages(socket: Socket) = coroutineScope {
        try {
            val socketReader = BufferedReader(InputStreamReader(socket.getInputStream()))
            while (true) {
                try {
                    val jsonData = withCancelCheck { socketReader.readLine() } ?: break
                    val messageData = Json.decodeFromString<MessageData>(jsonData)
                    messageQueue.emit(messageData to socket.port)
//                    output.writeSystemMessage("Read $messageData from ${socket.port}")
                } catch (e: IllegalArgumentException) {
                    output.writeSystemMessage("Failed to decode message from ${socket.port}")
                }
            }
        } catch (_: SocketException) {
        } finally {
            output.writeSystemMessage("closing input from ${socket.port}")
            socket.close()
            clients.remove(socket)
        }
    }
}

fun <T> CoroutineScope.withCancelCheck(block: () -> T): T {
    ensureActive()
    val result = block()
    ensureActive()
    return result
}