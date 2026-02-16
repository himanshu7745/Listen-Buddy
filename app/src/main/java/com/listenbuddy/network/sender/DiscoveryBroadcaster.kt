package com.listenbuddy.network.sender

import com.listenbuddy.utils.getBroadcastAddress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket

class DiscoveryBroadcaster(
    private val serverName: String,
    private val tcpPort: Int
) {

    private var job: Job? = null

    fun start(scope: CoroutineScope) {
        job = scope.launch(Dispatchers.IO) {

            val socket = DatagramSocket()
            socket.broadcast = true

            val message = "DISCOVER|$serverName|$tcpPort"
            val data = message.toByteArray()
            val broadcastAddress = getBroadcastAddress()

            while (isActive) {
                val packet = DatagramPacket(
                    data,
                    data.size,
                    broadcastAddress,
                    50000
                )
                socket.send(packet)
                delay(1000)
            }

            socket.close()
        }
    }

    fun stop() {
        job?.cancel()
    }
}