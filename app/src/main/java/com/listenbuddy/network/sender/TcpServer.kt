package com.listenbuddy.network.sender

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.io.DataOutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.CopyOnWriteArrayList

class TcpServer(
    private val port: Int,
    private val sampleRate: Int,
    private val channelCount: Int,
    private val onClientCountChanged: (Int) -> Unit
) {

    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null

    private val clients = CopyOnWriteArrayList<ClientConnection>()

    fun start(scope: CoroutineScope) {
        acceptJob = scope.launch(Dispatchers.IO) {
            try {
                serverSocket = ServerSocket(port)

                while (isActive) {
                    try {
                        val socket = serverSocket!!.accept()
                        socket.tcpNoDelay = true
                        socket.sendBufferSize = 64 * 1024

                        val connection = createClient(socket, this)
                        clients.add(connection)
                        onClientCountChanged(clients.size)

                    } catch (e: SocketException) {
                        if (serverSocket?.isClosed == true) break
                        else throw e
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun createClient(
        socket: Socket,
        scope: CoroutineScope
    ): ClientConnection {

        val output = DataOutputStream(socket.getOutputStream())

        // 🔹 Send header immediately
        output.writeInt(sampleRate)
        output.writeInt(channelCount)
        output.flush()

        val channel = Channel<ByteArray>(capacity = 8)

        val job = scope.launch {
            try {
                for (frame in channel) {
                    output.writeInt(frame.size)
                    output.write(frame)
                }
            } catch (_: Exception) {
            } finally {
                removeClient(socket)
            }
        }

        return ClientConnection(socket, channel, job)
    }

    fun sendToAll(pcm: ByteArray) {
        for (client in clients) {
            client.channel.trySend(pcm) // non-blocking
        }
    }

    private fun removeClient(socket: Socket) {
        val client = clients.find { it.socket == socket } ?: return

        clients.remove(client)

        client.channel.close()
        client.job.cancel()

        try { socket.close() } catch (_: Exception) {}

        onClientCountChanged(clients.size)
    }

    fun stop() {
        acceptJob?.cancel()

        try { serverSocket?.close() } catch (_: Exception) {}

        for (client in clients) {
            client.channel.close()
            client.job.cancel()
            try { client.socket.close() } catch (_: Exception) {}
        }

        clients.clear()
        onClientCountChanged(0)
    }
}

data class ClientConnection(
    val socket: Socket,
    val channel: Channel<ByteArray>,
    val job: Job
)
