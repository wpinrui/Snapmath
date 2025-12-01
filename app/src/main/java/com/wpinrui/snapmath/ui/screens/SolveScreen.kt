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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        if (solutionResult.isNotEmpty()) {
                            // Parse and display formatted solution with step navigation
                            FormattedSolution(
                                solution = solutionResult,
                                isStreaming = isStreaming,
                                onCopy = { copyToClipboard(solutionResult) },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        if (!isStreaming) {
                            OutlinedButton(
                                onClick = {
                                    showCamera = true
                                    solutionResult = ""
                                    errorMessage = null
                                },
                                modifier = Modifier.fillMaxWidth()
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
    }
}

@Composable
private fun FormattedSolution(
    solution: String,
    isStreaming: Boolean,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val sections = remember(solution) { parseSolution(solution) }

    // Create pages: Problem (if exists), each Step, Answer (if exists)
    val pages = remember(sections) {
        buildList {
            sections.filterIsInstance<SolutionSection.Problem>().firstOrNull()?.let { add(it) }
            addAll(sections.filterIsInstance<SolutionSection.Step>())
            sections.filterIsInstance<SolutionSection.Answer>().firstOrNull()?.let { add(it) }
        }
    }

    val pagerState = rememberPagerState(pageCount = { pages.size.coerceAtLeast(1) })

    // Auto-advance to new pages when streaming
    LaunchedEffect(pages.size) {
        if (isStreaming && pages.isNotEmpty()) {
            pagerState.animateScrollToPage(pages.size - 1)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Header with title, progress, and copy button
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (pages.size > 1) {
                            Text(
                                text = "${pagerState.currentPage + 1} / ${pages.size}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.padding(4.dp))
                        }
                        IconButton(onClick = onCopy) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy"
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Pager content
        if (pages.isNotEmpty()) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) { pageIndex ->
                val page = pages[pageIndex]
                Card(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 4.dp),
                    colors = when (page) {
                        is SolutionSection.Answer -> CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                        is SolutionSection.Problem -> CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
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
                            is SolutionSection.Problem -> {
                                Text(
                                    text = "Problem",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                MathText(
                                    text = page.content,
                                    style = MaterialTheme.typography.bodyLarge,
                                    textColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
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
                            is SolutionSection.Text -> {
                                MathText(
                                    text = page.content,
                                    style = MaterialTheme.typography.bodyLarge,
                                    textColor = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Navigation buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage - 1)
                        }
                    },
                    enabled = pagerState.currentPage > 0
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.padding(4.dp))
                    Text("Previous")
                }

                Button(
                    onClick = {
                        scope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    },
                    enabled = pagerState.currentPage < pages.size - 1
                ) {
                    Text("Next")
                    Spacer(modifier = Modifier.padding(4.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null
                    )
                }
            }
        } else {
            // Fallback for empty pages (streaming just started)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
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
