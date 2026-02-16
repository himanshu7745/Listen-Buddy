package com.listenbuddy.network.receiver

import android.content.Context
import android.net.wifi.WifiManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket


class DiscoveryListener(context: Context) {

    private var job: Job? = null
    private var socket: DatagramSocket? = null

    private val wifi =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    private val lock: WifiManager.MulticastLock =
        wifi.createMulticastLock("discovery_lock")

    fun start(
        scope: CoroutineScope,
        onServerFound: (name: String, ip: String, tcpPort: Int) -> Unit
    ) {

        if (job?.isActive == true) return // prevent double start

        lock.acquire()

        job = scope.launch(Dispatchers.IO) {
            try {
                socket = DatagramSocket(null).apply {
                    reuseAddress = true
                    bind(java.net.InetSocketAddress(50000))
                }

                val buffer = ByteArray(1024)

                while (isActive) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket?.receive(packet)

                    val message = String(packet.data, 0, packet.length)

                    if (message.startsWith("DISCOVER")) {
                        val parts = message.split("|")
                        if (parts.size >= 3) {
                            val name = parts[1]
                            val port = parts[2].toInt()
                            val senderIp = packet.address.hostAddress

                            onServerFound(name, senderIp, port)
                        }
                    }
                }
            } catch (_: Exception) {
            } finally {
                socket?.close()
                socket = null
                if (lock.isHeld) lock.release()
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null

        socket?.close()
        socket = null

        if (lock.isHeld) lock.release()
    }
}
