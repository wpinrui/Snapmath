package com.wpinrui.snapmath.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.wpinrui.snapmath.data.llm.OpenAiService
import com.wpinrui.snapmath.data.preferences.ApiKeyManager
import com.wpinrui.snapmath.ui.components.CameraCapture
import kotlinx.coroutines.launch

private const val TAG = "Snapmath.Convert"

private const val CONVERT_PROMPT = """Recognize the handwritten math in this image and convert it to LaTeX format.
Return ONLY the LaTeX code, no explanations or surrounding text.
If there are multiple expressions, separate them with newlines."""

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConvertScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var hasCameraPermission by remember { mutableStateOf(false) }
    var showCamera by remember { mutableStateOf(true) }
    var isLoading by remember { mutableStateOf(false) }
    var latexResult by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showExitWarning by remember { mutableStateOf(false) }

    val apiKeyManager = remember { ApiKeyManager(context) }

    // Prevent back navigation while loading
    BackHandler(enabled = isLoading) {
        Log.d(TAG, "[UI] Back pressed while loading - showing warning")
        showExitWarning = true
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
        Log.d(TAG, "[UI] Camera permission granted: $isGranted")
    }

    LaunchedEffect(Unit) {
        Log.d(TAG, "[UI] ConvertScreen mounted")
        permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    // Exit warning dialog
    if (showExitWarning) {
        AlertDialog(
            onDismissRequest = { showExitWarning = false },
            title = { Text("Processing in Progress") },
            text = { Text("The image is still being processed. Are you sure you want to leave?") },
            confirmButton = {
                TextButton(onClick = {
                    Log.d(TAG, "[UI] User confirmed exit during loading")
                    showExitWarning = false
                    onNavigateBack()
                }) {
                    Text("Leave")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitWarning = false }) {
                    Text("Stay")
                }
            }
        )
    }

    fun processImage(bitmap: Bitmap) {
        Log.d(TAG, "[PROCESS] Starting image processing")
        val apiKey = apiKeyManager.getApiKey()
        if (apiKey.isBlank()) {
            Log.e(TAG, "[PROCESS] No API key configured")
            showCamera = false
            errorMessage = "No API key configured. Please add your OpenAI API key in Settings."
            return
        }

        scope.launch {
            isLoading = true
            showCamera = false
            errorMessage = null
            Log.d(TAG, "[PROCESS] Calling OpenAI service...")

            val service = OpenAiService(apiKey)
            val result = service.analyzeImage(bitmap, CONVERT_PROMPT)

            result.fold(
                onSuccess = { latex ->
                    Log.d(TAG, "[PROCESS] Success - received LaTeX result")
                    latexResult = latex
                    isLoading = false
                },
                onFailure = { error ->
                    Log.e(TAG, "[PROCESS] Failed: ${error.message}")
                    errorMessage = error.message ?: "Unknown error occurred"
                    isLoading = false
                }
            )
        }
    }

    fun copyToClipboard(text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("LaTeX", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
        Log.d(TAG, "[UI] Copied to clipboard")
    }

    fun handleBackNavigation() {
        if (isLoading) {
            showExitWarning = true
        } else {
            onNavigateBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Convert to LaTeX") },
                navigationIcon = {
                    IconButton(
                        onClick = { handleBackNavigation() },
                        enabled = !isLoading || showExitWarning
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = if (isLoading) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                   else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                !hasCameraPermission -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("Camera permission is required")
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                            Text("Grant Permission")
                        }
                    }
                }

                showCamera -> {
                    CameraCapture(
                        onImageCaptured = { bitmap ->
                            processImage(bitmap)
                        },
                        onError = { error ->
                            errorMessage = error.message
                        }
                    )
                }

                isLoading -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Converting to LaTeX...")
                    }
                }

                else -> {
                    // Results view
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        if (errorMessage != null) {
                            Card(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = "Error",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = errorMessage!!,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }

                        if (latexResult != null) {
                            Card(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "LaTeX Output",
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        IconButton(onClick = { copyToClipboard(latexResult!!) }) {
                                            Icon(
                                                imageVector = Icons.Default.ContentCopy,
                                                contentDescription = "Copy"
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = latexResult!!,
                                        fontFamily = FontFamily.Monospace,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    showCamera = true
                                    latexResult = null
                                    errorMessage = null
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null
                                )
                                Spacer(modifier = Modifier.padding(4.dp))
                                Text("New Capture")
                            }

                            if (latexResult != null) {
                                Button(
                                    onClick = { copyToClipboard(latexResult!!) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = null
                                    )
                                    Spacer(modifier = Modifier.padding(4.dp))
                                    Text("Copy LaTeX")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
