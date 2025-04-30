package com.t34400.testapp

import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.util.Log
import android.view.SurfaceView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.getSystemService
import com.t34400.questcamera.core.CameraPosition
import com.t34400.questcamera.core.PassthroughCameraManager
import com.t34400.questcamera.core.getCameraMetaData
import com.t34400.questcamera.helper.CameraPermissionRequestActivity
import com.t34400.questcamera.json.toJson
import com.t34400.testapp.ui.theme.QuestCameraTheme

class MainActivity : ComponentActivity() {
    private var surfaceTop: SurfaceView? = null
    private var surfaceBottom: SurfaceView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContent {
            QuestCameraTheme {
                SurfaceViewLayout(
                    onSurfacesReady = { top, bottom ->
                        surfaceTop = top
                        surfaceBottom = bottom
                        startCapturePassthroughCamera()
                    }
                )
            }
        }
    }

    fun startCapturePassthroughCamera() {
        val hasCameraPermission = CameraPermissionRequestActivity.checkSelfPermission(this)
        Log.d("Test", "Has Camera Permission = $hasCameraPermission")

        CameraPermissionRequestActivity.permissionResultDispatcher.addListener { result ->
            Log.d("Test", "Camera Permission Request Result = $result")

            if (result) {
                getSystemService<CameraManager>()?.let { cameraManager ->
                    val cameraIdList = cameraManager.cameraIdList
                    Log.d("CameraList", "Found ${cameraIdList.size} camera(s)")

                    for (cameraId in cameraIdList) {
                        getCameraMetaData(cameraManager, cameraId)?.let { metaData ->
                            Log.d("Test", "Camera Meta Data Json:\n${metaData.toJson()}")
                            Log.d("Test", "Is Passthrough Camera: ${metaData.isPassthroughCamera}, Camera Position: ${metaData.cameraPosition}")
                            if (metaData.isPassthroughCamera) {
                                val position = metaData.cameraPosition
                                if (position == CameraPosition.Left && surfaceTop != null) {
                                    val manager = PassthroughCameraManager(metaData, listOf(SurfaceViewWrapper(surfaceTop!!)))
                                    manager.openCamera(this, cameraManager)
                                } else if (position == CameraPosition.Right && surfaceBottom != null) {
                                    val manager = PassthroughCameraManager(metaData, listOf(SurfaceViewWrapper(surfaceBottom!!)))
                                    manager.openCamera(this, cameraManager)
                                }
                            }
                        }
                    }
                }
            }
        }
        CameraPermissionRequestActivity.requestCameraPermissionIfNeeded(this)
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

