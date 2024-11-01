package com.das747.localchat

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.ServerSocket
import java.net.SocketException

abstract class ServerApplicationBase(
    input: UserInputProvider,
    output: UserOutputProvider
) : ApplicationBase(input, output) {

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