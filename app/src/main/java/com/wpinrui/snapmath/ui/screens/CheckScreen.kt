package com.wpinrui.snapmath.ui.screens

import android.Manifest
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wpinrui.snapmath.data.llm.OpenAiService
import com.wpinrui.snapmath.data.preferences.ApiKeyManager
import com.wpinrui.snapmath.ui.components.CameraCapture
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val CHECK_PROMPT = """Look at this handwritten math problem and the student's solution attempt.
Analyze each step of their work and identify any errors.

You MUST respond with valid JSON only, no other text. Use this exact format:
{
  "problem": "the original problem as written",
  "steps": [
    {"step": 1, "content": "what they wrote for this step", "correct": true, "explanation": ""},
    {"step": 2, "content": "what they wrote for this step", "correct": false, "explanation": "explanation of the error"}
  ],
  "final_answer_correct": true,
  "summary": "brief overall feedback"
}

Important:
- Include ALL steps the student wrote
- Set "correct" to true or false for each step
- Only include "explanation" text if the step is incorrect
- Be specific about what went wrong in incorrect steps"""

@Serializable
data class CheckResult(
    val problem: String,
    val steps: List<StepResult>,
    val final_answer_correct: Boolean,
    val summary: String
)

@Serializable
data class StepResult(
    val step: Int,
    val content: String,
    val correct: Boolean,
    val explanation: String = ""
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var hasCameraPermission by remember { mutableStateOf(false) }
    var showCamera by remember { mutableStateOf(true) }
    var isLoading by remember { mutableStateOf(false) }
    var checkResult by remember { mutableStateOf<CheckResult?>(null) }
    var rawResponse by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showExitWarning by remember { mutableStateOf(false) }

    val apiKeyManager = remember { ApiKeyManager(context) }
    val json = remember { Json { ignoreUnknownKeys = true } }

    // Prevent back navigation while loading
    BackHandler(enabled = isLoading) {
        showExitWarning = true
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    LaunchedEffect(Unit) {
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
        val apiKey = apiKeyManager.getApiKey()
        if (apiKey.isBlank()) {
            showCamera = false
            errorMessage = "No API key configured. Please add your OpenAI API key in Settings."
            return
        }

        scope.launch {
            isLoading = true
            showCamera = false
            errorMessage = null
            checkResult = null

            val service = OpenAiService(apiKey)
            val result = service.analyzeImage(bitmap, CHECK_PROMPT)

            result.fold(
                onSuccess = { response ->
                    rawResponse = response
                    try {
                        val start = response.indexOf('{')
                        val end = response.lastIndexOf('}')
                        val jsonString = if (start != -1 && end != -1 && end > start) {
                            response.substring(start, end + 1)
                        } else {
                            response
                        }
                        checkResult = json.decodeFromString<CheckResult>(jsonString)
                    } catch (e: Exception) {
                        errorMessage = "Failed to parse response: ${e.message}\n\nRaw response:\n$response"
                    }
                    isLoading = false
                },
                onFailure = { error ->
                    errorMessage = error.message ?: "Unknown error occurred"
                    isLoading = false
                }
            )
        }
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
                title = { Text("Check Work") },
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
                    Column {
                        Box(modifier = Modifier.weight(1f)) {
                            CameraCapture(
                                onImageCaptured = { bitmap ->
                                    processImage(bitmap)
                                },
                                onError = { error ->
                                    errorMessage = error.message
                                }
                            )
                        }
                        Text(
                            text = "Include both the problem AND your work",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        )
                    }
                }

                isLoading -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Checking your work...")
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
                        errorMessage?.let { error ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer
                                )
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = "Error",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = error,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        if (checkResult != null) {
                            // Problem card
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = "Problem",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(text = checkResult!!.problem)
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Steps
                            checkResult!!.steps.forEach { step ->
                                StepCard(step = step)
                                Spacer(modifier = Modifier.height(8.dp))
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Summary card
                            val isCorrect = checkResult!!.final_answer_correct
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isCorrect) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.errorContainer
                                    }
                                )
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isCorrect) {
                                                Icons.Default.Check
                                            } else {
                                                Icons.Default.Close
                                            },
                                            contentDescription = null,
                                            tint = if (isCorrect) {
                                                MaterialTheme.colorScheme.onPrimaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.onErrorContainer
                                            }
                                        )
                                        Text(
                                            text = if (isCorrect) {
                                                "Correct!"
                                            } else {
                                                "Needs Review"
                                            },
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isCorrect) {
                                                MaterialTheme.colorScheme.onPrimaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.onErrorContainer
                                            }
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = checkResult!!.summary,
                                        color = if (isCorrect) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onErrorContainer
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedButton(
                            onClick = {
                                showCamera = true
                                checkResult = null
                                errorMessage = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null
                            )
                            Spacer(modifier = Modifier.padding(4.dp))
                            Text("Check Another")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StepCard(step: StepResult) {
    val containerColor = if (step.correct) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.errorContainer
    }
    val contentColor = if (step.correct) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onErrorContainer
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Status indicator
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(
                        color = if (step.correct) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (step.correct) Icons.Default.Check else Icons.Default.Close,
                    contentDescription = if (step.correct) "Correct" else "Incorrect",
                    tint = if (step.correct) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onError
                    },
                    modifier = Modifier.size(16.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Step ${step.step}",
                    style = MaterialTheme.typography.labelMedium,
                    color = contentColor.copy(alpha = 0.7f)
                )
                Text(
                    text = step.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor
                )
                if (!step.correct && step.explanation.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = step.explanation,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
