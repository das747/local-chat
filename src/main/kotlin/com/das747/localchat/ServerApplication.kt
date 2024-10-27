package com.das747.localchat

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.datetime.Clock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import kotlin.coroutines.cancellation.CancellationException

typealias ClientId = Int

class ServerApplication(
    private val input: UserInputProvider,
    private val output: UserOutputProvider
) : Application {
    private data class ClientData(val job: Job, val socket: Socket, val queue: Channel<MessageData>)

    private val user = "aboba"
    private val centralQueue = Channel<Pair<MessageData, ClientId>>(100)
    private val clientData = mutableMapOf<ClientId, ClientData>()

    private val LOCAL_ID = -1

    override suspend fun run() {
        coroutineScope {
            val serverSocket = ServerSocket(5111)
            output.writeSystemMessage("Listening on port ${serverSocket.localPort}")
            launch {
                handleMessageQueue()
            }
            launch(Dispatchers.IO) {
                val outputQueue = Channel<MessageData>(10)
                clientData[LOCAL_ID] = ClientData(Job(), Socket(), outputQueue)
                for (message in outputQueue) {
                    output.writeMessage(message)
                }
            }
            launch(Dispatchers.IO) {
                handleConnections(serverSocket)
            }
            launch(Dispatchers.IO) {
                handleUserInput()
                serverSocket.close()
                centralQueue.cancel()
                for (clientId in clientData.keys) {
                    disconnectClient(clientId)
                }
            }
        }
    }

    private suspend fun handleUserInput() = coroutineScope {
        try {
            while (true) {
                val userInput = input.getInput() ?: break
                if (userInput.isEmpty()) continue
                output.writeSystemMessage("Read \"$userInput\" from input ")
                ensureActive()
                val messageData = MessageData(userInput, MetaData(Clock.System.now(), user))
                centralQueue.send(messageData to LOCAL_ID)
            }
        } catch (e: CancellationException) {
            output.writeSystemMessage("Input closed")
        }
    }

    private suspend fun handleConnections(serverSocket: ServerSocket) = coroutineScope {
        try {
            while (true) {
                val socket = serverSocket.accept()
                output.writeSystemMessage("Connected ${socket.port}")
                val queue = Channel<MessageData>(10)
                val job = launch {
                    launch(Dispatchers.IO) {
                        handleIncomingMessages(socket)
                        disconnectClient(socket.port)
                    }
                    launch(Dispatchers.IO) {
                        handleOutgoingMessages(socket, queue)
                        disconnectClient(socket.port)
                    }
                }
                clientData[socket.port] = ClientData(job, socket, queue)
            }
        } catch (_: SocketException) {}
    }

    private suspend fun handleMessageQueue() {
        for ((message, senderId) in centralQueue) {
            output.writeSystemMessage("Dequeued $message from central queue")
            for ((clientId, data) in clientData) {
                if (clientId != senderId) {
                    data.queue.send(message)
                }
            }
        }
    }

    private fun disconnectClient(clientId: ClientId) {
        clientData[clientId]?.run {
            queue.cancel()
            socket.close()
            output.writeSystemMessage("Disconnected $clientId")
        }
    }

    private suspend fun handleOutgoingMessages(socket: Socket, queue: Channel<MessageData>) {
        val socketWriter = PrintWriter(socket.getOutputStream(), true)
        for (message in queue) {
            output.writeSystemMessage("Sent $message to ${socket.port}")
            val jsonData = Json.encodeToString(message)
            socketWriter.println(jsonData)
        }
    }

    private suspend fun handleIncomingMessages(socket: Socket) = coroutineScope {
        try {
            val socketReader = BufferedReader(InputStreamReader(socket.getInputStream()))
            while (true) {
                try {
                    val jsonData = socketReader.readLine() ?: break
                    ensureActive()
                    val messageData = Json.decodeFromString<MessageData>(jsonData)
                    output.writeSystemMessage("Read $messageData from ${socket.port}")
                    centralQueue.send(messageData to socket.port)
                } catch (e: IllegalArgumentException) {
                    output.writeSystemMessage("Failed to decode message")
                }
            }
        } catch (_: SocketException) {
        }
    }

}