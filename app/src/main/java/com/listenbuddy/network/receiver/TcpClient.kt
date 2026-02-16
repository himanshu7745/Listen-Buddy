package com.listenbuddy.network.receiver

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.Socket

class TcpClient {

    private var socket: Socket? = null

    suspend fun connect(ip: String, port: Int): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                socket = Socket(ip, port)
                socket?.tcpNoDelay = true
                true
            } catch (e: Exception) {
                false
            }
        }
    }

    fun getSocket(): Socket? = socket

    fun disconnect() {
        socket?.close()
    }
}