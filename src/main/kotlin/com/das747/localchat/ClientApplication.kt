package com.das747.localchat

import java.net.Socket
import kotlinx.coroutines.*
import kotlinx.datetime.Clock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.SocketException

class ClientApplication(
    private val input: UserInputProvider,
    private val output: UserOutputProvider
) : Application {
    override suspend fun run() {
        val port = getPort() ?: return
        val socket = connect("localhost", port) ?: return
        coroutineScope {
            val receiverLoop = launch(Dispatchers.IO) {
                handleIncomingMessages(socket)
            }
            val senderLoop = launch(Dispatchers.IO) {
                handleUserInput(socket)
            }
            receiverLoop.invokeOnCompletion { reason ->
                if (reason == null) {
                    output.writeSystemMessage("Please press enter to exit")
                    senderLoop.cancel()
                    socket.close()
                }
            }
            senderLoop.invokeOnCompletion { reason ->
                if (reason == null) {
                    output.writeSystemMessage("Input closed, closing connection...")
                    receiverLoop.cancel()
                    socket.close()
                }
            }
        }
        output.writeSystemMessage("Exiting application...")
    }

    private fun getPort(): Int? {
        while (true) {
            try {
                output.writeSystemMessage("Please select destination port:")
//                val port = input.getInput()?.toInt() ?: return null
                val port = 5111
                if (port !in 1..65535) {
                    throw IllegalArgumentException("Invalid port value: $port")
                }
                return port
            } catch (e: IllegalArgumentException) {
                output.writeSystemMessage("Not a valid port, reason: ${e.message}")
            }
        }
    }

    private fun connect(address: String, port: Int): Socket? {
        try {
            val socket = Socket(address, port)
            output.writeSystemMessage("Connected successfully to $port")
            return socket
        } catch (e: Exception) {
            output.writeSystemMessage("Failed to connect to $port, reason: ${e.message}")
            return null
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
                    output.writeMessage(messageData)
                } catch (e: IllegalArgumentException) {
                    output.writeSystemMessage("Failed to decode message")
                }
            }
        } catch (_: SocketException) {
        } finally {
            socket.close()
            output.writeSystemMessage("Connection to ${socket.port} closed")
        }
    }

    private suspend fun handleUserInput(socket: Socket) = coroutineScope {
        val socketWriter = PrintWriter(socket.getOutputStream(), true)
        while (true) {
            val userInput = input.getInput() ?: break
            ensureActive()
            if (userInput.isEmpty()) continue
            val messageData = MessageData(userInput, MetaData(Clock.System.now(), "aboba"))
            val jsonData = Json.encodeToString(messageData)
            socketWriter.println(jsonData)
        }
    }
}