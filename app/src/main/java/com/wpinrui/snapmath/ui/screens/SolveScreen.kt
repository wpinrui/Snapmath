package com.wpinrui.snapmath.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wpinrui.snapmath.data.history.HistoryRepository
import com.wpinrui.snapmath.data.llm.OpenAiService
import com.wpinrui.snapmath.data.preferences.ApiKeyManager
import com.wpinrui.snapmath.ui.components.CameraCapture
import com.wpinrui.snapmath.ui.components.MathText
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

private const val SOLVE_PROMPT = "Look at this handwritten math problem. Solve it step-by-step.\n" +
    "Format your response as:\n" +
    "PROBLEM: [the recognized problem in LaTeX, e.g. \$x^2 + 2x = 0\$]\n\n" +
    "SOLUTION:\n" +
    "Step 1: [explanation with math in LaTeX using \$ delimiters]\n" +
    "Step 2: [explanation with math in LaTeX using \$ delimiters]\n" +
    "...\n\n" +
    "ANSWER: [final answer in LaTeX]\n\n" +
    "Use LaTeX notation for all mathematical expressions, wrapped in \$ delimiters.\n" +
    "Be clear and educational in your explanations."

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SolveScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var hasCameraPermission by remember { mutableStateOf(false) }
    var showCamera by remember { mutableStateOf(true) }
    var isLoading by remember { mutableStateOf(false) }
    var isStreaming by remember { mutableStateOf(false) }
    var solutionResult by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showExitWarning by remember { mutableStateOf(false) }

    val apiKeyManager = remember { ApiKeyManager(context) }
    val historyRepository = remember { HistoryRepository(context) }
    val scrollState = rememberScrollState()
    var userHasScrolled by remember { mutableStateOf(false) }
    var historySaved by remember { mutableStateOf(false) }

    // Track if user has manually scrolled up
    LaunchedEffect(scrollState.isScrollInProgress) {
        if (scrollState.isScrollInProgress && isStreaming) {
            // User is scrolling while streaming - they want to read earlier content
            if (scrollState.value < scrollState.maxValue - 100) {
                userHasScrolled = true
            }
        }
    }

    // Reset scroll tracking when starting new stream
    LaunchedEffect(isStreaming) {
        if (isStreaming) {
            userHasScrolled = false
        }
    }

    // Auto-scroll to bottom only if user hasn't scrolled up
    LaunchedEffect(solutionResult) {
        if (isStreaming && !userHasScrolled) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    // Prevent back navigation while loading
    BackHandler(enabled = isLoading || isStreaming) {
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
            isStreaming = true
            showCamera = false
            errorMessage = null
            solutionResult = ""
            historySaved = false

            val service = OpenAiService(apiKey)
            service.analyzeImageStreaming(bitmap, SOLVE_PROMPT)
                .catch { error ->
                    errorMessage = error.message ?: "Unknown error occurred"
                    isLoading = false
                    isStreaming = false
                }
                .collect { chunk ->
                    solutionResult += chunk
                    isLoading = false
                }

            isStreaming = false

            // Save to history if we have a result
            if (solutionResult.isNotEmpty() && errorMessage == null && !historySaved) {
                val problem = extractProblem(solutionResult)
                historyRepository.saveSolveEntry(problem, solutionResult)
                historySaved = true
            }
        }
    }

    fun copyToClipboard(text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Solution", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    fun handleBackNavigation() {
        if (isLoading || isStreaming) {
            showExitWarning = true
        } else {
            onNavigateBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Solve Problem") },
                navigationIcon = {
                    IconButton(
                        onClick = { handleBackNavigation() },
                        enabled = !(isLoading || isStreaming) || showExitWarning
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = if (isLoading || isStreaming) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
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
                            text = "Point at a math problem",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        )
                    }
                }

                isLoading && solutionResult.isEmpty() -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Analyzing problem...")
                    }
                }

                else -> {
                    // Results view
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                            .verticalScroll(scrollState)
                    ) {
                        if (errorMessage != null) {
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
                                        text = errorMessage!!,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }

                        if (solutionResult.isNotEmpty()) {
                            // Parse and display formatted solution
                            FormattedSolution(
                                solution = solutionResult,
                                isStreaming = isStreaming,
                                onCopy = { copyToClipboard(solutionResult) }
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        if (!isStreaming) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        showCamera = true
                                        solutionResult = ""
                                        errorMessage = null
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = null
                                    )
                                    Spacer(modifier = Modifier.padding(4.dp))
                                    Text("New Problem")
                                }

                                if (solutionResult.isNotEmpty()) {
                                    Button(
                                        onClick = { copyToClipboard(solutionResult) },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = null
                                        )
                                        Spacer(modifier = Modifier.padding(4.dp))
                                        Text("Copy")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FormattedSolution(
    solution: String,
    isStreaming: Boolean,
    onCopy: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Solution",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (isStreaming) {
                        Spacer(modifier = Modifier.padding(8.dp))
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }
                IconButton(onClick = onCopy) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy"
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Parse sections from the solution
            val sections = parseSolution(solution)

            sections.forEach { section ->
                when (section) {
                    is SolutionSection.Problem -> {
                        Text(
                            text = "Problem",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            MathText(
                                text = section.content,
                                style = MaterialTheme.typography.bodyLarge,
                                textColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    is SolutionSection.Step -> {
                        Text(
                            text = "Step ${section.number}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        MathText(
                            text = section.content,
                            style = MaterialTheme.typography.bodyMedium,
                            textColor = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    is SolutionSection.Answer -> {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "Answer",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                MathText(
                                    text = section.content,
                                    style = MaterialTheme.typography.titleMedium,
                                    textColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                    is SolutionSection.Text -> {
                        MathText(
                            text = section.content,
                            style = MaterialTheme.typography.bodyMedium,
                            textColor = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

private sealed class SolutionSection {
    data class Problem(val content: String) : SolutionSection()
    data class Step(val number: Int, val content: String) : SolutionSection()
    data class Answer(val content: String) : SolutionSection()
    data class Text(val content: String) : SolutionSection()
}

private fun parseSolution(solution: String): List<SolutionSection> {
    val sections = mutableListOf<SolutionSection>()
    val lines = solution.lines()

    var currentSection: String? = null
    val currentContent = StringBuilder()
    var stepNumber = 0

    fun flushSection() {
        val content = currentContent.toString().trim()
        if (content.isNotEmpty()) {
            when {
                currentSection == "problem" -> sections.add(SolutionSection.Problem(content))
                currentSection == "step" -> sections.add(SolutionSection.Step(stepNumber, content))
                currentSection == "answer" -> sections.add(SolutionSection.Answer(content))
                currentSection == "text" -> sections.add(SolutionSection.Text(content))
            }
        }
        currentContent.clear()
    }

    for (line in lines) {
        val trimmed = line.trim()
        when {
            trimmed.startsWith("PROBLEM:", ignoreCase = true) -> {
                flushSection()
                currentSection = "problem"
                currentContent.append(trimmed.removePrefix("PROBLEM:").removePrefix("problem:").trim())
            }
            trimmed.startsWith("SOLUTION:", ignoreCase = true) -> {
                flushSection()
                currentSection = "text"
            }
            trimmed.matches(Regex("^Step\\s*\\d+:.*", RegexOption.IGNORE_CASE)) -> {
                flushSection()
                currentSection = "step"
                stepNumber = trimmed.substringAfter("Step").substringBefore(":").trim().toIntOrNull() ?: (stepNumber + 1)
                currentContent.append(trimmed.substringAfter(":").trim())
            }
            trimmed.startsWith("ANSWER:", ignoreCase = true) -> {
                flushSection()
                currentSection = "answer"
                currentContent.append(trimmed.removePrefix("ANSWER:").removePrefix("answer:").trim())
            }
            else -> {
                if (currentSection == null && trimmed.isNotEmpty()) {
                    currentSection = "text"
                }
                if (currentContent.isNotEmpty()) {
                    currentContent.append("\n")
                }
                currentContent.append(trimmed)
            }
        }
    }

    flushSection()

    return sections.ifEmpty { listOf(SolutionSection.Text(solution)) }
}

/**
 * Extracts the problem statement from a solution response.
 */
private fun extractProblem(solution: String): String {
    val lines = solution.lines()
    for (line in lines) {
        val trimmed = line.trim()
        if (trimmed.startsWith("PROBLEM:", ignoreCase = true)) {
            return trimmed.removePrefix("PROBLEM:").removePrefix("problem:").trim()
        }
    }
    // Fallback: return first non-empty line or truncated solution
    return lines.firstOrNull { it.isNotBlank() }?.take(100) ?: solution.take(100)
}
