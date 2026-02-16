package com.listenbuddy.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.listenbuddy.audio.AudioDecoder
import com.listenbuddy.audio.AudioPlayer
import com.listenbuddy.network.sender.DiscoveryBroadcaster
import com.listenbuddy.network.sender.TcpServer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

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

    private var discovery: DiscoveryBroadcaster? = null
    private var tcpServer: TcpServer? = null
    private var decoder: AudioDecoder? = null
    private var player: AudioPlayer? = null

    private val TCP_PORT = 60000

    @Volatile
    private var isStopping = false

    fun setFile(uri: Uri?, fileName: String) {
        _uiState.update {
            it.copy(selectedFileUri = uri, selectedFileName = fileName)
        }
    }

    fun setServerName(name: String) {
        _uiState.update { it.copy(serverName = name) }
    }

    fun toggleBroadcast(context: Context) {
        if (!_uiState.value.isBroadcasting) {
            startBroadcast(context)
        } else {
            stopBroadcast()
        }
    }

    private fun startBroadcast(context: Context) {

        val state = _uiState.value
        if (state.selectedFileUri == null || state.serverName.isBlank()) return

        isStopping = false

        _uiState.update {
            it.copy(
                isBroadcasting = true,
                broadcastStatus = "Preparing..."
            )
        }

        broadcastJob = viewModelScope.launch(Dispatchers.IO) {
            try {

                decoder = AudioDecoder(context, state.selectedFileUri!!)
                decoder!!.prepare()

                player = AudioPlayer(
                    decoder!!.sampleRate,
                    decoder!!.channelCount
                )

                tcpServer = TcpServer(
                    port = TCP_PORT,
                    sampleRate = decoder!!.sampleRate,
                    channelCount = decoder!!.channelCount,
                    onClientCountChanged = { count ->
                        _uiState.update { it.copy(connectedClients = count) }
                    }
                )

                tcpServer!!.start(this)

                discovery = DiscoveryBroadcaster(
                    state.serverName,
                    TCP_PORT
                )
                discovery!!.start(this)

                _uiState.update {
                    it.copy(
                        broadcastStatus =
                            "Broadcasting: ${decoder!!.sampleRate} Hz, ${decoder!!.channelCount} channels"
                    )
                }

                decoder!!.decode { pcm ->
                    if (!isStopping && isActive) {
                        tcpServer?.sendToAll(pcm)
                        player?.play(pcm)
                    }
                }

                if (!isStopping && isActive) {
                    stopBroadcast()
                }

            } catch (e: Exception) {
                if (!isStopping) {
                    e.printStackTrace()
                    _uiState.update {
                        it.copy(
                            isBroadcasting = false,
                            broadcastStatus = "Error: ${e.message}"
                        )
                    }
                }
                cleanupResources()
            }
        }
    }

    fun stopBroadcast() {
        isStopping = true

        broadcastJob?.cancel()
        broadcastJob = null

        cleanupResources()

        _uiState.update {
            it.copy(
                isBroadcasting = false,
                broadcastStatus = "Stopped",
                connectedClients = 0
            )
        }
    }

    private fun cleanupResources() {
        try { discovery?.stop() } catch (_: Exception) {}
        discovery = null

        try { tcpServer?.stop() } catch (_: Exception) {}
        tcpServer = null

        try { player?.release() } catch (_: Exception) {}
        player = null

        try { decoder?.release() } catch (_: Exception) {}
        decoder = null
    }

    override fun onCleared() {
        stopBroadcast()
        super.onCleared()
    }
}
