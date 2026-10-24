package com.example.smartcameraai

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {
    private lateinit var previewView: PreviewView
    private lateinit var statusText: TextView
    private lateinit var modeText: TextView
    private lateinit var captureButton: Button
    private lateinit var cameraExecutor: ExecutorService
    private var imageCapture: ImageCapture? = null
    private var activeCamera: Camera? = null
    @Volatile private var latestBrightness: Double = 0.5
    private var lastExposureIndex: Int? = null

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startCamera()
            else statusText.text = "Camera permission is required to use Smart Camera AI."
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraExecutor = Executors.newSingleThreadExecutor()
        buildUi()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 24, 20, 20)
            setBackgroundColor(android.graphics.Color.rgb(20, 22, 27))
        }
        val title = TextView(this).apply {
            text = "Smart Camera AI"
            textSize = 24f
            setTextColor(android.graphics.Color.WHITE)
            gravity = Gravity.CENTER
        }
        statusText = TextView(this).apply {
            text = "Checking scene and lighting…"
            textSize = 15f
            setTextColor(android.graphics.Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(8, 12, 8, 8)
        }
        previewView = PreviewView(this).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
        modeText = TextView(this).apply {
            text = "Mode: Auto"
            textSize = 16f
            setTextColor(android.graphics.Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(8, 12, 8, 12)
        }
        captureButton = Button(this).apply {
            text = "Capture photo"
            setOnClickListener { takePhoto() }
        }
        root.addView(title, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(statusText, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(previewView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(modeText, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(captureButton, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        setContentView(root)
    }

    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            val provider = providerFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .build()
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(cameraExecutor) { image ->
                try {
                    val plane = image.planes.firstOrNull()
                    if (plane != null) {
                        val buffer = plane.buffer
                        var total = 0L
                        var count = 0
                        while (buffer.hasRemaining()) {
                            total += (buffer.get().toInt() and 0xff)
                            count++
                        }
                        if (count > 0) {
                            latestBrightness = (total.toDouble() / count) / 255.0
                            runOnUiThread { updateRecommendation() }
                        }
                    }
                } catch (_: Exception) {
                    // Keep the last valid estimate if a frame cannot be analysed.
                } finally {
                    image.close()
                }
            }
            try {
                provider.unbindAll()
                activeCamera = provider.bindToLifecycle(
                    this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture, analysis
                )
                updateRecommendation()
            } catch (e: Exception) {
                statusText.text = "Unable to start camera: ${e.localizedMessage ?: "unknown error"}"
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun updateRecommendation() {
        val brightness = latestBrightness
        val advice: String
        val compensation: Int
        val mode: String
        when {
            brightness < 0.20 -> {
                advice = "Low light detected. Hold steady; use Night Sight in Google's Camera for best night processing."
                compensation = 0
                mode = "Low light · Auto exposure"
            }
            brightness > 0.78 -> {
                advice = "Bright scene detected. Protect highlights; tap the subject to focus if needed."
                compensation = -1
                mode = "Bright scene · Highlight protection"
            }
            else -> {
                advice = "Balanced light detected. Keep the subject in focus and use natural light."
                compensation = 0
                mode = "Everyday photo · Auto"
            }
        }
        statusText.text = advice
        modeText.text = "$mode  •  Light ${(brightness * 100).roundToInt()}%"
        val camera = activeCamera ?: return
        val range = camera.cameraInfo.exposureState.exposureCompensationRange
        val requested = compensation.coerceIn(range.lower, range.upper)
        if (lastExposureIndex != requested) {
            camera.cameraControl.setExposureCompensationIndex(requested)
            lastExposureIndex = requested
        }
    }

    private fun takePhoto() {
        val capture = imageCapture ?: run {
            Toast.makeText(this, "Camera is still starting", Toast.LENGTH_SHORT).show()
            return
        }
        val name = "SmartCamera_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.jpg"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/SmartCameraAI")
            }
        }
        val output = ImageCapture.OutputFileOptions.Builder(
            contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
        ).build()
        capture.takePicture(output, ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    Toast.makeText(this@MainActivity, "Photo saved to Pictures/SmartCameraAI", Toast.LENGTH_LONG).show()
                }
                override fun onError(exception: ImageCaptureException) {
                    Toast.makeText(this@MainActivity, "Capture failed: ${exception.message}", Toast.LENGTH_LONG).show()
                }
            })
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::cameraExecutor.isInitialized) cameraExecutor.shutdown()
    }
}
