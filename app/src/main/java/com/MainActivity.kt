package com

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.MotionEvent
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import trex.runner.R
import trex.runner.databinding.ActivityMainBinding
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity(), FaceLandmarkerHelper.LandmarkerListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var faceLandmarkerHelper: FaceLandmarkerHelper
    private lateinit var cameraExecutor: ExecutorService
    private var camera: Camera? = null

    private var lastTriggerTime: Long = 0
    private val triggerCooldownMs = 500 // bekleme süresi

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWebView()

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        faceLandmarkerHelper = FaceLandmarkerHelper(
            context = this, faceLandmarkerHelperListener = this
        )
    }

    private fun setupWebView() {
        binding.webView.apply {
            settings.javaScriptEnabled = true
            settings.allowFileAccess = true
            webViewClient = WebViewClient()
            setBackgroundColor(Color.TRANSPARENT)
        }

        val gameDir = File(filesDir, "game")
        if (!gameDir.exists()) UnzipUtils.unzip(this, R.raw.trex, gameDir.absolutePath)

        val indexPath = "file://${gameDir.absolutePath}/t-rex-runner-minify/index.html"
        binding.webView.loadUrl(indexPath)
    }

    private fun startCamera() {
        cameraExecutor = Executors.newSingleThreadExecutor()
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setTargetRotation(binding.cameraPreview.display.rotation).build().also {
                    it.surfaceProvider = binding.cameraPreview.surfaceProvider
                }

            val imageAnalyzer = ImageAnalysis.Builder().setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setTargetRotation(binding.cameraPreview.display.rotation)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888).build().also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        faceLandmarkerHelper.detectLiveStream(imageProxy, true)
                    }
                }

            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                cameraProvider.unbindAll()
                camera =
                    cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalyzer)
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }


    override fun onResults(resultBundle: FaceLandmarkerHelper.ResultBundle) {
        runOnUiThread {
            if (resultBundle.result.faceBlendshapes().isPresent) {
                val faceBlendshapes = resultBundle.result.faceBlendshapes().get()
                val sortedCategories = faceBlendshapes[0].sortedByDescending { it.score() }
                sortedCategories.find {
                    it.categoryName() == "browInnerUp"
                }?.let {
                    if (it.score() > BROW_UP_THRESHOLD) {
                        triggerWebViewClick()
                    }
                }
            }
        }
    }

    private fun triggerWebViewClick() {
        val currentTime = SystemClock.uptimeMillis()
        if (currentTime - lastTriggerTime > triggerCooldownMs) {
            lastTriggerTime = currentTime
            val x = binding.webView.width / 2.0f
            val y = binding.webView.height / 2.0f
            val downEvent = MotionEvent.obtain(currentTime, currentTime, MotionEvent.ACTION_DOWN, x, y, 0)
            binding.webView.dispatchTouchEvent(downEvent)
        }
    }

    override fun onError(error: String, errorCode: Int) {
        runOnUiThread {
            Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onEmpty() {
        // Yüz algılanmadığında yapılacak bir işlem gerekirse buraya eklenebilir.
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this, "Permissions not granted by the user.", Toast.LENGTH_SHORT)
                    .show()
                finish()
            }
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        faceLandmarkerHelper.clearFaceLandmarker()
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
        private const val BROW_UP_THRESHOLD = 0.7f // Kaş kaldırma eşiği
    }
}
