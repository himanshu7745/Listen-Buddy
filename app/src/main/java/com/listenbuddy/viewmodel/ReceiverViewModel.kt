package com.listenbuddy.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.listenbuddy.audio.AudioPlayer
import com.listenbuddy.data.Server
import com.listenbuddy.network.receiver.DiscoveryListener
import com.listenbuddy.network.receiver.TcpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.DataInputStream

data class ReceiverUiState(
    val isDiscovering: Boolean = false,
    val discoveryStatus: String = "Press 'Start Discovery' to find servers",
    val discoveredServers: List<Server> = emptyList(),
    val connectedServer: Server? = null
)

class ReceiverViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(ReceiverUiState())
    val uiState: StateFlow<ReceiverUiState> = _uiState.asStateFlow()

    private var discoveryListener: DiscoveryListener? = null
    private val tcpClient = TcpClient()

    // CRITICAL FIX: Track audio player and connection job
    private var audioPlayer: AudioPlayer? = null
    private var connectionJob: Job? = null

    fun toggleDiscovery(context: Context) {
        val current = _uiState.value

        if (!current.isDiscovering) {
            startDiscovery(context)
        } else {
            stopDiscovery()
        }
    }

    private fun startDiscovery(context: Context) {

        _uiState.update {
            it.copy(
                isDiscovering = true,
                discoveryStatus = "Searching for servers...",
                discoveredServers = emptyList()
            )
        }

        discoveryListener = DiscoveryListener(
            context = context
        )

        discoveryListener?.start(viewModelScope) { name, ip, port ->

            val server = Server(name, ip, port)

            _uiState.update { current ->
                if (current.discoveredServers.any { it.address == ip }) {
                    current
                } else {
                    current.copy(
                        discoveredServers = current.discoveredServers + server,
                        discoveryStatus = "Found ${current.discoveredServers.size + 1} server(s)"
                    )
                }
            }
        }
    }

    fun stopDiscovery() {
        discoveryListener?.stop()
        discoveryListener = null

        _uiState.update {
            it.copy(
                isDiscovering = false,
                discoveryStatus = if (it.connectedServer != null)
                    "Connected to ${it.connectedServer.name}"
                else
                    "Discovery stopped"
            )
        }
    }

    fun connectToServer(server: Server) {
        // CRITICAL FIX: Disconnect previous connection first
        disconnect()

        // CRITICAL FIX: Update state to show connection attempt
        _uiState.update {
            it.copy(
                connectedServer = server,
                discoveryStatus = "Connecting to ${server.name}..."
            )
        }

        connectionJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val success = tcpClient.connect(server.address, server.port)
                if (!success) {
                    _uiState.update {
                        it.copy(
                            connectedServer = null,
                            discoveryStatus = "Failed to connect to ${server.name}"
                        )
                    }
                    return@launch
                }

                val socket = tcpClient.getSocket() ?: return@launch
                val inputStream = DataInputStream(BufferedInputStream(socket.getInputStream()))

                // 1. READ HEADER FIRST
                val sampleRate = inputStream.readInt()
                val channels = inputStream.readInt()

                // 2. INITIALIZE PLAYER WITH CORRECT SETTINGS
                audioPlayer = AudioPlayer(sampleRate, channels)

                _uiState.update {
                    it.copy(
                        discoveryStatus = "Streaming: $sampleRate Hz, $channels channels"
                    )
                }

                // 3. START DATA LOOP
                while (isActive) {
                    val size = inputStream.readInt()
                    if (size <= 0) break

                    val buffer = ByteArray(size)
                    inputStream.readFully(buffer)
                    audioPlayer?.play(buffer)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update {
                    it.copy(
                        connectedServer = null,
                        discoveryStatus = "Connection Lost: ${e.message}"
                    )
                }
            } finally {
                // CRITICAL FIX: Clean up audio player when connection ends
                cleanupConnection()
            }
        }
    }

    fun disconnect() {
        // CRITICAL FIX: Cancel connection job
        connectionJob?.cancel()
        connectionJob = null

        cleanupConnection()

        _uiState.update {
            it.copy(
                connectedServer = null,
                discoveryStatus = "Disconnected"
            )
        }
    }

    // CRITICAL FIX: Proper cleanup method
    private fun cleanupConnection() {
        try {
            audioPlayer?.release()
            audioPlayer = null
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            tcpClient.disconnect()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopDiscovery()
        disconnect()
    }
}