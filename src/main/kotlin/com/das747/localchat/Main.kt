package com.das747.localchat

import kotlinx.coroutines.runBlocking

fun main() {
    val app = ClientApplication(StdinInputProvider(), StdoutOutputProvider())
    runBlocking {
        app.run()
    }
}