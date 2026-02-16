package com.listenbuddy.utils

import java.net.InetAddress
import java.net.NetworkInterface

fun getBroadcastAddress(): InetAddress? {
    try {
        val interfaces = NetworkInterface.getNetworkInterfaces()
        while (interfaces.hasMoreElements()) {
            val networkInterface = interfaces.nextElement()

            // Skip loopback (127.0.0.1) and inactive interfaces
            if (networkInterface.isLoopback || !networkInterface.isUp) continue

            for (interfaceAddress in networkInterface.interfaceAddresses) {
                val broadcast = interfaceAddress.broadcast
                // If we found a broadcast address (non-null), return it
                if (broadcast != null) {
                    return broadcast
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    // Fallback if we can't find a specific one
    return InetAddress.getByName("255.255.255.255")
}