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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
        }
    }

    fun copyToClipboard(text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Solution", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    val showResults = !showCamera && (solutionResult.isNotEmpty() || errorMessage != null || (isLoading && solutionResult.isEmpty()))

    Box(modifier = Modifier.fillMaxSize()) {
        // Camera view layer
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

            showResults -> {
                // Results overlay - full screen, no swipe gestures
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        when {
                            isLoading && solutionResult.isEmpty() -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        CircularProgressIndicator()
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text("Analyzing problem...")
                                    }
                                }
                            }

                            else -> {
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
                                    Spacer(modifier = Modifier.height(8.dp))
                                }

                                if (solutionResult.isNotEmpty()) {
                                    FormattedSolution(
                                        solution = solutionResult,
                                        isStreaming = isStreaming,
                                        onCopy = { copyToClipboard(solutionResult) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Button(
                                    onClick = {
                                        showCamera = true
                                        solutionResult = ""
                                        errorMessage = null
                                    },
                                    enabled = !isStreaming,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 16.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = null
                                    )
                                    Spacer(modifier = Modifier.padding(4.dp))
                                    Text("Solve Another Problem")
                                }
                            }
                        }
                    }
                }
            }

            else -> {
                // Camera view
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
        }
    }
}

@Composable
private fun FormattedSolution(
    solution: String,
    isStreaming: Boolean,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sections = remember(solution) { parseSolution(solution) }

    // Extract problem separately - it stays at the top
    val problem = sections.filterIsInstance<SolutionSection.Problem>().firstOrNull()

    // Steps and answer go in the pager
    val stepPages = remember(sections) {
        buildList {
            addAll(sections.filterIsInstance<SolutionSection.Step>())
            sections.filterIsInstance<SolutionSection.Answer>().firstOrNull()?.let { add(it) }
        }
    }

    // Pager state - NO auto-advance, stays on step 1
    val pagerState = rememberPagerState(pageCount = { stepPages.size.coerceAtLeast(1) })

    Column(modifier = modifier.fillMaxSize()) {
        // Problem card at the top (fixed, taller)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Problem",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                Spacer(modifier = Modifier.height(8.dp))
                if (problem != null) {
                    MathText(
                        text = problem.content,
                        style = MaterialTheme.typography.bodyLarge,
                        textColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = "Loading problem...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Step indicator
        if (stepPages.isNotEmpty()) {
            Text(
                text = "${pagerState.currentPage + 1} / ${stepPages.size}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Steps pager at the bottom (swipeable)
        if (stepPages.isNotEmpty()) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) { pageIndex ->
                val page = stepPages[pageIndex]
                Card(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 4.dp),
                    colors = when (page) {
                        is SolutionSection.Answer -> CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                        else -> CardDefaults.cardColors()
                    }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        when (page) {
                            is SolutionSection.Step -> {
                                Text(
                                    text = "Step ${page.number}",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.secondary,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                MathText(
                                    text = page.content,
                                    style = MaterialTheme.typography.bodyLarge,
                                    textColor = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            is SolutionSection.Answer -> {
                                Text(
                                    text = "Answer",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                MathText(
                                    text = page.content,
                                    style = MaterialTheme.typography.headlineSmall,
                                    textColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            else -> {
                                MathText(
                                    text = (page as? SolutionSection.Text)?.content ?: "",
                                    style = MaterialTheme.typography.bodyLarge,
                                    textColor = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // Waiting for steps to load
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Loading solution steps...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
