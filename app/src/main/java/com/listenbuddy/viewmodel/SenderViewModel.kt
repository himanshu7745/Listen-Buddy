package com.listenbuddy.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.listenbuddy.audio.AudioDecoder
import com.listenbuddy.audio.AudioPlayer
import com.listenbuddy.network.sender.DiscoveryBroadcaster
import com.listenbuddy.network.sender.TcpServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.DataOutputStream

data class SenderUiState(
    val selectedFileUri: Uri? = null,
    val selectedFileName: String = "No file selected",
    val serverName: String = "",
    val isBroadcasting: Boolean = false,
    val broadcastStatus: String = "",
    val connectedClients: Int = 0
)

class SenderViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(SenderUiState())
    val uiState: StateFlow<SenderUiState> = _uiState.asStateFlow()

    private var broadcastJob: Job? = null
    private var decodingJob: Job? = null  // Track decoding separately

    private var discovery: DiscoveryBroadcaster? = null
    private var tcpServer: TcpServer? = null

    private var decoder: AudioDecoder? = null

    private var player: AudioPlayer? = null

    private val TCP_PORT = 60000

    @Volatile
    private var isStopping = false  // Flag to coordinate cleanup

    fun setFile(uri: Uri?, fileName: String) {
        _uiState.update {
            it.copy(
                selectedFileUri = uri,
                selectedFileName = fileName
            )
        }
    }

    fun setServerName(name: String) {
        _uiState.update { it.copy(serverName = name) }
    }

    fun toggleBroadcast(context: Context) {
        val current = _uiState.value

        if (!current.isBroadcasting) {
            startBroadcast(current, context)
        } else {
            stopBroadcast()
        }
    }

    private fun startBroadcast(state: SenderUiState, context: Context) {
        if (state.selectedFileUri == null || state.serverName.isBlank()) return

        isStopping = false  // Reset flag

        _uiState.update {
            it.copy(
                isBroadcasting = true,
                broadcastStatus = "Starting server..."
            )
        }

        broadcastJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                decoder = AudioDecoder(context, state.selectedFileUri)
                decoder?.prepare()

                // 1. Initialize Player and Server
                player = AudioPlayer(decoder!!.sampleRate, decoder!!.channelCount)
                tcpServer = TcpServer(
                    port = TCP_PORT,
                    onClientConnected = { socket ->
                        // Send Metadata Header immediately
                        viewModelScope.launch(Dispatchers.IO) {
                            try {
                                val dos = DataOutputStream(socket.getOutputStream())
                                dos.writeInt(decoder?.sampleRate ?: 44100)
                                dos.writeInt(decoder?.channelCount ?: 2)
                                dos.flush()
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    },
                    onClientCountChanged = { count ->
                        _uiState.update { it.copy(connectedClients = count) }
                    }
                )
                tcpServer?.start(this) // Use 'this' for scope

                // 2. Start Discovery AFTER server is ready
                discovery = DiscoveryBroadcaster(state.serverName, TCP_PORT)
                discovery?.start(this)

                _uiState.update {
                    it.copy(broadcastStatus = "Broadcasting: ${decoder?.sampleRate} Hz, ${decoder?.channelCount} channels")
                }

                // 3. Decoding Loop (Run in separate job to avoid blocking)
                decodingJob = launch(Dispatchers.IO) {
                    try {
                        decoder?.decode { pcm ->
                            // Check if we're still active before processing
                            if (!isStopping && isActive) {
                                tcpServer?.sendToAll(pcm) // Send to network
                                player?.play(pcm)       // Play locally
                            } else {
                                return@decode  // Stop decoding
                            }
                        }

                        // Only auto-stop if we finished naturally (not cancelled)
                        if (!isStopping && isActive) {
                            stopBroadcast()
                        }
                    } catch (e: Exception) {
                        if (!isStopping) {
                            e.printStackTrace()
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update {
                    it.copy(
                        isBroadcasting = false,
                        broadcastStatus = "Error: ${e.message}"
                    )
                }
                // CRITICAL FIX: Clean up on error
                cleanupResources()
            }
        }
    }

    fun stopBroadcast() {
        isStopping = true  // Set flag first to stop decode loop

        // Cancel decoding job first
        decodingJob?.cancel()
        decodingJob = null

        cleanupResources()

        broadcastJob?.cancel()
        broadcastJob = null

        _uiState.update {
            it.copy(
                isBroadcasting = false,
                broadcastStatus = "Broadcast stopped",
                connectedClients = 0,
                serverName = ""
            )
        }
    }

    // CRITICAL FIX: Proper resource cleanup
    private fun cleanupResources() {
        try {
            discovery?.stop()
            discovery = null
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            tcpServer?.stop()
            tcpServer = null
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            player?.release()
            player = null
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            decoder?.release()
            decoder = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopBroadcast()
    }
}