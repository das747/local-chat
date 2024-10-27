package com.das747.localchat

import kotlinx.coroutines.runBlocking

fun main() {
    val app = ServerApplication(StdinInputProvider(), StdoutOutputProvider())
    runBlocking {
        app.run()
    }
}