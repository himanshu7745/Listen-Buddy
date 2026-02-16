package com.listenbuddy.network.sender

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.DataOutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException

class TcpServer(
    private val port: Int,
    private val onClientConnected: (Socket) -> Unit,
    private val onClientCountChanged: (Int) -> Unit
) {

    private var serverSocket: ServerSocket? = null
    private var job: Job? = null

    private val clients = mutableListOf<Socket>()

    fun start(scope: CoroutineScope) {
        job = scope.launch(Dispatchers.IO) {

            try {
                serverSocket = ServerSocket(port)

                while (isActive) {
                    try {
                        val client = serverSocket!!.accept()
                        client.tcpNoDelay = true

                        synchronized(clients) {
                            clients.add(client)
                            onClientConnected(client)
                            onClientCountChanged(clients.size)
                        }

                    } catch (e: SocketException) {
                        // Happens when serverSocket.close() is called
                        if (serverSocket?.isClosed == true) {
                            break
                        } else {
                            throw e
                        }
                    }
                }

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }


    fun stop() {
        try {
            serverSocket?.close()
        } catch (_: Exception) {}

        job?.cancel()

        synchronized(clients) {
            clients.forEach { it.close() }
            clients.clear()
        }

        onClientCountChanged(0)
    }


    fun sendToAll(pcm: ByteArray) {
        synchronized(clients) {
            val iterator = clients.iterator()
            while (iterator.hasNext()) {
                val client = iterator.next()
                try {
                    if (client.isConnected && !client.isOutputShutdown) {
                        val dos = DataOutputStream(client.getOutputStream())
                        dos.writeInt(pcm.size) // Send size first (Framing)
                        dos.write(pcm)         // Send actual data
                        dos.flush()
                    }
                } catch (e: Exception) {
                    // If a client disconnects, remove them
                    client.close()
                    iterator.remove()
                    onClientCountChanged(clients.size)
                }
            }
        }
    }

}