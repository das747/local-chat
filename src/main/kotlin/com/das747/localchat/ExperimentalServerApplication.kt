//@file:OptIn(ExperimentalCoroutinesApi::class)
//
//package com.das747.localchat
//
//import kotlinx.coroutines.*
//import kotlinx.coroutines.channels.Channel
//import kotlinx.coroutines.channels.ReceiveChannel
//import kotlinx.coroutines.channels.produce
//import kotlinx.coroutines.selects.whileSelect
//import kotlinx.datetime.Clock
//import kotlinx.serialization.encodeToString
//import kotlinx.serialization.json.Json
//import java.io.BufferedReader
//import java.io.InputStreamReader
//import java.io.PrintWriter
//import java.net.ServerSocket
//import java.net.Socket
//import java.net.SocketException
//import java.util.concurrent.ConcurrentHashMap
//
//
//class ExperimentalServerApplication (
//    private val input: UserInputProvider,
//    private val output: UserOutputProvider
//) : Application {
//
//    private data class ClientData(
//        val scope: CoroutineScope,
//        val socket: Socket,
//        val queue: Channel<MessageData>
//    )
//
//    private val user = "aboba"
//    private val centralQueue = Channel<Pair<MessageData, ClientId>>(100)
//    private val rawMessageProducers: MutableSet<ReceiveChannel<Pair<String, ClientId>>> = ConcurrentHashMap.newKeySet()
//
//    private val localId = -1
//
//    override suspend fun run() {
//        coroutineScope {
//            val serverSocket = ServerSocket(0)
//            output.writeSystemMessage("Listening on port ${serverSocket.localPort}")
//            launch {
//                handleMessageQueue()
//            }
//            launch(Dispatchers.IO) {
//                val outputQueue = Channel<MessageData>(10)
//                clientData[localId] = ClientData(this, Socket(), outputQueue)
//                for (message in outputQueue) {
//                    output.writeMessage(message)
//                }
//            }
//            val connectionHandler = launch(Dispatchers.IO) {
//                handleConnections(serverSocket)
//            }
//            launch(Dispatchers.IO) {
//                handleUserInput()
//            }.invokeOnCompletion {
//                connectionHandler.cancel()
//                serverSocket.close()
//                centralQueue.cancel()
//                for (clientId in clientData.keys) {
//                    disconnectClient(clientId)
//                }
//            }
//        }
//    }
//
//    private suspend fun handleMessageQueue() {
//        for ((message, senderId) in centralQueue) {
////            output.writeSystemMessage("Dequeued $message from central queue")
//            for ((clientId, data) in clientData) {
//                if (clientId != senderId) {
//                    data.queue.send(message)
//                }
//            }
//        }
//    }
//
//
//
//    private suspend fun handleConnections(serverSocket: ServerSocket) = coroutineScope {
//        try {
//            while (true) {
//                val socket = serverSocket.accept()
//                output.writeSystemMessage("Connected ${socket.port}")
//                val queue = Channel<MessageData>(10)
//                val scope = CoroutineScope(Dispatchers.IO + Job())
//                scope.launch {
//                    handleIncomingMessages(socket)
//                }.invokeOnCompletion {
//                    queue.cancel()
//                    clientData.remove(socket.port)
//                    output.writeSystemMessage("${socket.port} disconnected")
//                }
//                scope.launch {
//                    handleOutgoingMessages(socket, queue)
//                }
//                clientData[socket.port] = ClientData(scope, socket, queue)
//            }
//        } catch (_: SocketException) {
//        }
//    }
//
//    private suspend fun handleUserInput() = coroutineScope {
//        while (true) {
//            val userInput = input.getInput() ?: break
//            ensureActive()
//            if (userInput.isEmpty()) continue
////            output.writeSystemMessage("Read \"$userInput\" from input ")
//            val messageData = MessageData(userInput, MetaData(Clock.System.now(), user))
//            centralQueue.send(messageData to localId)
//        }
//    }
//
//    private fun disconnectClient(clientId: ClientId) {
//        clientData[clientId]?.run {
//            scope.cancel()
//            socket.close()
//        }
//    }
//
//    private suspend fun handleOutgoingMessages(socket: Socket, queue: Channel<MessageData>) {
//        val socketWriter = PrintWriter(socket.getOutputStream(), true)
//        for (message in queue) {
////            output.writeSystemMessage("Sent $message to ${socket.port}")
//            val jsonData = Json.encodeToString(message)
//            socketWriter.println(jsonData)
//        }
//    }
//
//    private suspend fun handleIncomingMessages(socket: Socket) = coroutineScope {
//        try {
//            val socketReader = BufferedReader(InputStreamReader(socket.getInputStream()))
//            while (true) {
//                try {
//                    val jsonData = socketReader.readLine() ?: break
//                    ensureActive()
//                    val messageData = Json.decodeFromString<MessageData>(jsonData)
////                    output.writeSystemMessage("Read $messageData from ${socket.port}")
//                    centralQueue.send(messageData to socket.port)
//                } catch (e: IllegalArgumentException) {
//                    output.writeSystemMessage("Failed to decode message")
//                }
//            }
//        } catch (_: SocketException) {
//        } finally {
//            socket.close()
//        }
//    }
//
//    fun CoroutineScope.produceDecodedMessages() = produce {
//        whileSelect {
//            rawMessageProducers.forEach { producer ->
//                producer.onReceiveCatching {
//
//                }
//            }
//        }
//    }
//
//    fun CoroutineScope.produceReceivedMessages(socket: Socket) = produce {
//        val socketReader = BufferedReader(InputStreamReader(socket.getInputStream()))
//        try {
//            while (true) {
//                val rawMessage = socketReader.readLine() ?: break
//                send(rawMessage to socket.port)
//            }
//        } catch (_: SocketException) {
//        } finally {
//            output.writeSystemMessage("${socket.port} disconnected")
//        }
//    }
//}
//
