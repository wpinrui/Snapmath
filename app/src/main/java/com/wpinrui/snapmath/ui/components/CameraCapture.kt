package com.wpinrui.snapmath.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors

private const val TAG = "Snapmath.Camera"
private const val CAPTURE_BUTTON_SIZE_DP = 80
private const val CAPTURE_BUTTON_INNER_SIZE_DP = 64
private const val CAPTURE_BUTTON_BORDER_WIDTH_DP = 4
private const val CAPTURE_BUTTON_BOTTOM_PADDING_DP = 32
private const val CAPTURE_BUTTON_PRESSED_SCALE = 0.9f
private const val YUV_COMPRESSION_QUALITY = 90

@Composable
fun CameraCapture(
    modifier: Modifier = Modifier,
    onImageCaptured: (Bitmap) -> Unit,
    onError: (Exception) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    var isCapturing by remember { mutableStateOf(false) }

    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setTargetRotation(android.view.Surface.ROTATION_0)
            .build()
    }

    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
        Log.d(TAG, "[CAMERA] Camera composable mounted")
        onDispose {
            Log.d(TAG, "[CAMERA] Camera composable disposed, shutting down executor")
            cameraExecutor.shutdown()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                Log.d(TAG, "[CAMERA] Creating PreviewView")
                val previewView = PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }

                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    Log.d(TAG, "[CAMERA] Camera provider obtained")

                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            imageCapture
                        )
                        Log.d(TAG, "[CAMERA] Camera bound to lifecycle successfully")
                    } catch (e: Exception) {
                        Log.e(TAG, "[CAMERA] Failed to bind camera: ${e.message}", e)
                        onError(e)
                    }
                }, ContextCompat.getMainExecutor(ctx))

                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        // Capture button with visual feedback
        val interactionSource = remember { MutableInteractionSource() }
        val isPressed by interactionSource.collectIsPressedAsState()

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = CAPTURE_BUTTON_BOTTOM_PADDING_DP.dp)
                .size(CAPTURE_BUTTON_SIZE_DP.dp)
                .scale(if (isPressed || isCapturing) CAPTURE_BUTTON_PRESSED_SCALE else 1f)
                .background(Color.White, CircleShape)
                .border(CAPTURE_BUTTON_BORDER_WIDTH_DP.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = !isCapturing
                ) {
                    Log.d(TAG, "[CAPTURE] Capture button pressed")
                    isCapturing = true
                    val captureStartTime = System.currentTimeMillis()

                    imageCapture.takePicture(
                        cameraExecutor,
                        object : ImageCapture.OnImageCapturedCallback() {
                            override fun onCaptureSuccess(image: ImageProxy) {
                                val captureTime = System.currentTimeMillis() - captureStartTime
                                Log.d(TAG, "[CAPTURE] Image captured in ${captureTime}ms")
                                Log.d(TAG, "[CAPTURE] Image format: ${image.format}, size: ${image.width}x${image.height}")

                                try {
                                    val convertStartTime = System.currentTimeMillis()
                                    val bitmap = imageProxyToBitmap(image)
                                    val convertTime = System.currentTimeMillis() - convertStartTime
                                    Log.d(TAG, "[CAPTURE] Image converted to bitmap in ${convertTime}ms, size: ${bitmap.width}x${bitmap.height}")

                                    // Post to main thread
                                    mainHandler.post {
                                        isCapturing = false
                                        Log.d(TAG, "[CAPTURE] Passing bitmap to callback")
                                        onImageCaptured(bitmap)
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "[CAPTURE] Failed to convert image: ${e.message}", e)
                                    mainHandler.post {
                                        isCapturing = false
                                        onError(e)
                                    }
                                } finally {
                                    image.close()
                                }
                            }

                            override fun onError(exception: ImageCaptureException) {
                                Log.e(TAG, "[CAPTURE] Capture failed: ${exception.message}", exception)
                                mainHandler.post {
                                    isCapturing = false
                                    onError(exception)
                                }
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(CAPTURE_BUTTON_INNER_SIZE_DP.dp)
                    .background(
                        if (isCapturing) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        else MaterialTheme.colorScheme.primary,
                        CircleShape
                    )
            )
        }
    }
}

private fun imageProxyToBitmap(image: ImageProxy): Bitmap {
    val format = image.format

    return when (format) {
        ImageFormat.JPEG -> {
            val buffer = image.planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            rotateBitmapIfNeeded(bitmap, image.imageInfo.rotationDegrees)
        }
        ImageFormat.YUV_420_888 -> {
            val yBuffer = image.planes[0].buffer
            val uBuffer = image.planes[1].buffer
            val vBuffer = image.planes[2].buffer

            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()

            val nv21 = ByteArray(ySize + uSize + vSize)
            yBuffer.get(nv21, 0, ySize)
            vBuffer.get(nv21, ySize, vSize)
            uBuffer.get(nv21, ySize + vSize, uSize)

            val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), YUV_COMPRESSION_QUALITY, out)
            val imageBytes = out.toByteArray()
            val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            rotateBitmapIfNeeded(bitmap, image.imageInfo.rotationDegrees)
        }
        else -> {
            // Fallback: try JPEG decoding
            val buffer = image.planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: throw IllegalStateException("Failed to decode image (format: $format)")
            rotateBitmapIfNeeded(bitmap, image.imageInfo.rotationDegrees)
        }
    }
}

private fun rotateBitmapIfNeeded(bitmap: Bitmap, rotationDegrees: Int): Bitmap {
    return if (rotationDegrees != 0) {
        val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    } else {
        bitmap
    }
}
