package com.t34400.testapp

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
import com.t34400.questcamera.core.CameraPermissionManager
import com.t34400.questcamera.core.CameraSessionManager
import com.t34400.questcamera.io.DataDirectoryManager
import com.t34400.questcamera.io.ImageReaderSurfaceProvider
import com.t34400.questcamera.io.MetaDataWriter
import com.t34400.testapp.ui.theme.QuestCameraTheme
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

            val dataDirectory = DataDirectoryManager(context)
            MetaDataWriter.writeMetaData(dataDirectory, permissionManager)

            val leftCameraSessionManager = CameraSessionManager()
            val rightCameraSessionManager = CameraSessionManager()

            permissionManager.getCameraManager()?.let { cameraManager ->
                permissionManager.leftCameraMetaData?.sensor?.pixelArraySize?.let { pixelSize ->
                    val imageReaderSurfaceProvider = ImageReaderSurfaceProvider(
                        dataDirectory,
                        pixelSize.width,
                        pixelSize.height,
                        "left_camera_"
                    ).apply {
                        setShouldSaveFrame(true)
                    }

                    leftCameraSessionManager.registerSurfaceProvider(imageReaderSurfaceProvider)

                    val leftCameraId = permissionManager.getLeftCameraId()

                    leftCameraSessionManager.openCamera(
                        context,
                        cameraManager,
                        leftCameraId
                    )
                }
                permissionManager.leftCameraMetaData?.sensor?.pixelArraySize?.let { pixelSize ->
                    val imageReaderSurfaceProvider = ImageReaderSurfaceProvider(
                        dataDirectory,
                        pixelSize.width,
                        pixelSize.height,
                        "right_camera_"
                    ).apply {
                        setShouldSaveFrame(true)
                    }
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

                while (true) {
                    delay(100)
                }
            } catch (e: CancellationException) {
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

