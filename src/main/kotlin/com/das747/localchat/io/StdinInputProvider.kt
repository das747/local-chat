package com.das747.localchat.io

class StdinInputProvider : UserInputProvider {

    override fun getInput(): String? {
        return readlnOrNull()
    }
}