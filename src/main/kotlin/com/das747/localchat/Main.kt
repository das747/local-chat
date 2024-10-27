package com.das747.localchat

import kotlinx.coroutines.runBlocking

fun main() {
    val app = if (readlnOrNull() == "server") {
        ServerApplication(StdinInputProvider(), StdoutOutputProvider())
    } else {
        ClientApplication(StdinInputProvider(), StdoutOutputProvider())
    }
    runBlocking {
        app.run()
    }
}