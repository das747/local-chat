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
import java.util.concurrent.ConcurrentHashMap


class ServerApplication(
    private val input: UserInputProvider,
    private val output: UserOutputProvider
) : Application {
    private data class ClientData(
        val scope: CoroutineScope,
        val socket: Socket,
        val queue: Channel<Message>
    )

    private val user = "aboba"
    private val centralQueue = Channel<Pair<Message, ClientId>>(100)
    private val clientData: MutableMap<ClientId, ClientData> = ConcurrentHashMap()

    private val localId = -1

    override suspend fun run() {
        coroutineScope {
            val serverSocket = ServerSocket(0)
            output.writeSystemMessage("Listening on port ${serverSocket.localPort}")
            launch {
                handleMessageQueue()
            }
            launch(Dispatchers.IO) {
                val outputQueue = Channel<Message>(10)
                clientData[localId] = ClientData(this, Socket(), outputQueue)
                for (message in outputQueue) {
                    output.writeMessage(message)
                }
            }
            val connectionHandler = launch(Dispatchers.IO) {
                handleConnections(serverSocket)
            }
            launch(Dispatchers.IO) {
                handleUserInput()
            }.invokeOnCompletion {
                connectionHandler.cancel()
                serverSocket.close()
                centralQueue.cancel()
                for (clientId in clientData.keys) {
                    disconnectClient(clientId)
                }
            }
        }
    }

    private suspend fun handleMessageQueue() {
        for ((message, senderId) in centralQueue) {
//            output.writeSystemMessage("Dequeued $message from central queue")
            for ((clientId, data) in clientData) {
                if (clientId != senderId) {
                    data.queue.send(message)
                }
            }
        }
    }

    private suspend fun handleConnections(serverSocket: ServerSocket) = coroutineScope {
        try {
            while (true) {
                val socket = serverSocket.accept()
                output.writeSystemMessage("Connected ${socket.port}")
                val queue = Channel<Message>(10)
                val scope = CoroutineScope(Dispatchers.IO + Job())
                scope.launch {
                    handleIncomingMessages(socket)
                }.invokeOnCompletion {
                    queue.cancel()
                    clientData.remove(socket.port)
                    output.writeSystemMessage("${socket.port} disconnected")
                }
                scope.launch {
                    handleOutgoingMessages(socket, queue)
                }
                clientData[socket.port] = ClientData(scope, socket, queue)
            }
        } catch (_: SocketException) {
        }
    }

    private suspend fun handleUserInput() = coroutineScope {
        while (true) {
            val userInput = input.getInput() ?: break
            ensureActive()
            if (userInput.isEmpty()) continue
//            output.writeSystemMessage("Read \"$userInput\" from input ")
            val messageData = Message(userInput,
                Metadata(Clock.System.now(), user)
            )
            centralQueue.send(messageData to localId)
        }
    }

    private fun disconnectClient(clientId: ClientId) {
        clientData[clientId]?.run {
            scope.cancel()
            socket.close()
        }
    }

    private suspend fun handleOutgoingMessages(socket: Socket, queue: Channel<Message>) {
        val socketWriter = PrintWriter(socket.getOutputStream(), true)
        for (message in queue) {
//            output.writeSystemMessage("Sent $message to ${socket.port}")
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
                    val messageData = Json.decodeFromString<Message>(jsonData)
//                    output.writeSystemMessage("Read $messageData from ${socket.port}")
                    centralQueue.send(messageData to socket.port)
                } catch (e: IllegalArgumentException) {
                    output.writeSystemMessage("Failed to decode message")
                }
            }
        } catch (_: SocketException) {
        } finally {
            socket.close()
        }
    }
}