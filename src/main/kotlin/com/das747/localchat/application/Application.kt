package com.das747.localchat.application

interface Application {
    suspend fun run(): Unit
}