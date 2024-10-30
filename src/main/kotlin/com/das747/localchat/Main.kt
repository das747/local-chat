package com.das747.localchat

import kotlinx.coroutines.runBlocking

fun main() {
//    val app = if (readlnOrNull() == "server") {
//        FlowServerApplication(StdinInputProvider(), StdoutOutputProvider())
//    } else {
//        ClientApplication(StdinInputProvider(), StdoutOutputProvider())
//    }
    val app = ChannelServerApplication(StdinInputProvider(), StdoutOutputProvider())
    runBlocking {
        app.run()
    }
}