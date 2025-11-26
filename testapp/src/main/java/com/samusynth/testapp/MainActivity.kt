package com.samusynth.testapp

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.SurfaceView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import com.samusynth.questcamera.core.CameraPermissionManager
import com.samusynth.questcamera.core.CameraSessionManager
import com.samusynth.questcamera.io.ImageReaderSurfaceProvider
import com.samusynth.testapp.ui.theme.QuestCameraTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var surfaceTop: SurfaceView? = null
    private var surfaceBottom: SurfaceView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        startCapturePassthroughCamera()

        enableEdgeToEdge()
        setContent {
            QuestCameraTheme {
                SurfaceViewLayout(
                    onSurfacesReady = { top, bottom ->
                        surfaceTop = top
                        surfaceBottom = bottom
                    }
                )
            }
        }
    }

    private fun startCapturePassthroughCamera() {
        val context: Context = this
        lifecycleScope.launch(Dispatchers.IO) {
            val permissionManager = CameraPermissionManager(context).apply {
                requestCameraPermissionIfNeeded()
            }

            while (!permissionManager.hasCameraManager()) {
                delay(10L)
            }

            val leftCameraSessionManager = CameraSessionManager()
            val rightCameraSessionManager = CameraSessionManager()
            
            var leftImageProvider: ImageReaderSurfaceProvider? = null
            var rightImageProvider: ImageReaderSurfaceProvider? = null

            permissionManager.getCameraManager()?.let { cameraManager ->
                permissionManager.leftCameraMetaData?.sensor?.pixelArraySize?.let { pixelSize ->
                    val imageReaderSurfaceProvider = ImageReaderSurfaceProvider(
                        pixelSize.width,
                        pixelSize.height,
                        "left_camera_",
                        "left_camera_image_format"
                    )
                    
                    leftImageProvider = imageReaderSurfaceProvider

                    leftCameraSessionManager.registerSurfaceProvider(imageReaderSurfaceProvider)

                    val leftCameraId = permissionManager.getLeftCameraId()

                    leftCameraSessionManager.openCamera(
                        context,
                        cameraManager,
                        leftCameraId
                    )
                }
                permissionManager.rightCameraMetaData?.sensor?.pixelArraySize?.let { pixelSize ->
                    val imageReaderSurfaceProvider = ImageReaderSurfaceProvider(
                        pixelSize.width,
                        pixelSize.height,
                        "right_camera_",
                        "right_camera_image_format"
                    )
                    
                    rightImageProvider = imageReaderSurfaceProvider
                    
                    rightCameraSessionManager.registerSurfaceProvider(imageReaderSurfaceProvider)

                    val rightCameraId = permissionManager.getRightCameraId()

                    rightCameraSessionManager.openCamera(
                        context,
                        cameraManager,
                        rightCameraId
                    )
                }
            }

            try {
                // Capture loop: trigger frame capture every 100ms for testing
                while (true) {
                    leftImageProvider?.captureNextFrame()
                    rightImageProvider?.captureNextFrame()
                    delay(100)
                }
            } catch (e: CancellationException) {
                leftImageProvider?.close()
                rightImageProvider?.close()
                leftCameraSessionManager.close()
                rightCameraSessionManager.close()

                Log.d("Test", "Canceled!")
            }
        }
    }
}

@Composable
fun SurfaceViewLayout(onSurfacesReady: (SurfaceView, SurfaceView) -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        val topViewState = remember { mutableStateOf<SurfaceView?>(null) }
        val bottomViewState = remember { mutableStateOf<SurfaceView?>(null) }

        AndroidView(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            factory = { context ->
                SurfaceView(context).also { topViewState.value = it }
            }
        )

        AndroidView(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            factory = { context ->
                SurfaceView(context).also { bottomViewState.value = it }
            }
        )

        LaunchedEffect(topViewState.value, bottomViewState.value) {
            val top = topViewState.value
            val bottom = bottomViewState.value
            if (top != null && bottom != null) {
                onSurfacesReady(top, bottom)
            }
        }
    }
}

