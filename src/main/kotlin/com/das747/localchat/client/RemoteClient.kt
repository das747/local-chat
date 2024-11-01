package com.das747.localchat.client

import com.das747.localchat.Message
import com.das747.localchat.application.ReceivedMessage
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket
import java.net.SocketException

internal class RemoteClient(private val socket: Socket) : Client {
    companion object {
        private val logger: Logger = LoggerFactory.getLogger(RemoteClient::class.java)
    }

    override val id: ClientId = socket.port

    private val socketReader = BufferedReader(InputStreamReader(socket.getInputStream()))
    private val socketWriter = PrintWriter(socket.getOutputStream(), true)

    override fun sendMessage(message: ReceivedMessage) {
        message.let { (message, clientId) ->
            if (clientId == socket.port) {
                // here we can send ack to the original sender
                return
            }
            val jsonData = Json.encodeToString(message)
            socketWriter.println(jsonData)
            logger.debug("Sent {} to {}", message, id)
        }
    }

    override fun readMessage(): Message? {
        try {
            while (true) {
                try {
                    val jsonData = socketReader.readLine() ?: return null
                    return Json.decodeFromString<Message>(jsonData)
                        .also { logger.debug("Read {} from {}", it, id ) }
                } catch (_: IllegalArgumentException) {
                    logger.debug("Failed to decode message from $id")
                }
            }
        } catch (_: SocketException) {
            logger.debug("socket $id destroyed")
            return null
        }
    }

    override fun close() {
        socket.close()
    }
}