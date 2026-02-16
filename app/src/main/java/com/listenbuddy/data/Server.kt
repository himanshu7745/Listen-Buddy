package com.listenbuddy.data

data class Server(
    val name: String,
    val address: String,
    val port: Int = 8080
)