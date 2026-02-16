package com.listenbuddy.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.listenbuddy.viewmodel.SenderViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SenderScreen(
    onBackClick: () -> Unit,
    viewModel: SenderViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    // CRITICAL FIX: Stop broadcast when leaving screen
    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopBroadcast()
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                val fileName = getFileName(context, uri)
                viewModel.setFile(uri, fileName)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sender Mode") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                }
            )
        }
    ) { paddingValues ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {

            // Choose File
            Button(
                onClick = {
                    val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                        type = "audio/*"
                        addCategory(Intent.CATEGORY_OPENABLE)
                    }
                    filePickerLauncher.launch(intent)
                },
                enabled = !uiState.isBroadcasting,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Choose File")
            }

            // File Name Display
            Text(uiState.selectedFileName)

            // Server Name
            OutlinedTextField(
                value = uiState.serverName,
                onValueChange = { viewModel.setServerName(it) },
                label = { Text("Server Name") },
                enabled = !uiState.isBroadcasting,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Connected Clients Display
            if (uiState.isBroadcasting) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "Connected Clients: ${uiState.connectedClients}",
                        modifier = Modifier.padding(16.dp),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Broadcast Button
            Button(
                onClick = {
                    viewModel.toggleBroadcast(context)
                },
                enabled = uiState.selectedFileUri != null &&
                        uiState.serverName.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor =
                        if (uiState.isBroadcasting)
                            MaterialTheme.colorScheme.error
                        else
                            MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    text = if (uiState.isBroadcasting)
                        "Stop Broadcast"
                    else
                        "Start Broadcast"
                )
            }

            if (uiState.broadcastStatus.isNotEmpty()) {
                Text(uiState.broadcastStatus)
            }
        }
    }
}


private fun getFileName(context: android.content.Context, uri: Uri): String {
    var fileName = "Unknown"
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        if (cursor.moveToFirst() && nameIndex != -1) {
            fileName = cursor.getString(nameIndex)
        }
    }
    return fileName
}