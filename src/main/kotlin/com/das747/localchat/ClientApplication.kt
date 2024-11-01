package com.das747.localchat

import java.net.Socket
import kotlinx.coroutines.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class ClientApplication(
    input: UserInputProvider,
    output: UserOutputProvider
) : ApplicationBase(input, output) {

    override val logger: Logger = LoggerFactory.getLogger(ClientApplication::class.java)


    override suspend fun run() {
        val port = getPort() ?: return
        val socket = connect("localhost", port) ?: return
        val remoteClient = RemoteClient(socket)
        coroutineScope {
            val outputHandler = launch(Dispatchers.IO) {
                processIncomingMessages(remoteClient) { message ->
                    localClient.sendMessage(message)
                }
            }
            val inputHandler = launch(Dispatchers.IO) {
                processIncomingMessages(localClient) { message ->
                    localClient.sendMessage(message)
                    remoteClient.sendMessage(message)
                }
            }
            outputHandler.onSuccess {
                inputHandler.cancel()
                remoteClient.close()
                output.writeSystemMessage("Connection to ${remoteClient.id}) closed.")
                output.writeSystemMessage("Please press enter to exit")

            }
            inputHandler.onSuccess {
                outputHandler.cancel()
                remoteClient.close()
                output.writeSystemMessage("Input closed. Disconnecting ${remoteClient.id}...")
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

    private fun Job.onSuccess(action: () -> Unit) {
        invokeOnCompletion {
            if (it == null) {
                action()
            }
        }
    }
}