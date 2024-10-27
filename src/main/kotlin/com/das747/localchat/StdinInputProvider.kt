package com.das747.localchat

class StdinInputProvider : UserInputProvider {

    override fun getInput(): String? {
        return readlnOrNull()
    }
}