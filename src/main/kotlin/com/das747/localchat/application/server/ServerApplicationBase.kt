package com.das747.localchat.application.server

import com.das747.localchat.client.Client
import com.das747.localchat.io.UserInputProvider
import com.das747.localchat.io.UserOutputProvider
import com.das747.localchat.application.ApplicationBase
import com.das747.localchat.application.withCancelCheck
import com.das747.localchat.client.RemoteClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.ServerSocket
import java.net.SocketException

abstract class ServerApplicationBase(
    input: UserInputProvider,
    output: UserOutputProvider,
    name: String
) : ApplicationBase(input, output, name) {

    protected fun CoroutineScope.launchConnectionHandler(serverSocket: ServerSocket) =
        launch(Dispatchers.IO) {
            try {
                while (true) {
                    val socket = withCancelCheck { serverSocket.accept() }
                    connectClient(RemoteClient(socket))
                    output.writeSystemMessage("Connected ${socket.port}")
                }
            } catch (_: SocketException) {
            } finally {
                logger.debug("connection handler stopped")
            }
        }

    protected abstract fun CoroutineScope.connectClient(client: Client)
}