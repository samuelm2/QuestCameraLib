package com.t34400.questcamera.core

import android.content.Context
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.util.Log
import com.t34400.questcamera.helper.CameraPermissionRequestActivity
import java.util.concurrent.Executors

class PassthroughCameraManager (
    val cameraMetaData: CameraMetadata,
    private val surfaceProviders: List<ISurfaceProvider>
): AutoCloseable {
    private var currentCamera: CameraDevice? = null
    private var session: CameraCaptureSession? = null

    override fun close() {
        Log.i(TAG, "Closing camera and cleaning up resources.")

        currentCamera?.close()
        currentCamera = null
        session?.close()
        session = null

        surfaceProviders.forEach { provider ->
            provider.close()
        }
        Log.i(TAG, "Resources released.")
    }

    fun isOpen(): Boolean {
        return currentCamera != null
    }

    fun openCamera(context: Context, cameraManager: CameraManager) {
        if (!CameraPermissionRequestActivity.checkSelfPermission(context)) {
            Log.w(TAG, "Camera permission not granted. Aborting camera open.")
            return
        }
        if (currentCamera != null) {
            Log.w(TAG, "Camera already open. Skipping openCamera call.")
            return
        }

        val cameraCallback = object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                Log.i(TAG, "Camera ${cameraMetaData.cameraId} opened successfully.")

                currentCamera = camera

                createCameraSession(camera)
            }

            override fun onDisconnected(camera: CameraDevice) {
                if (camera == currentCamera) {
                    Log.w(TAG, "Camera ${cameraMetaData.cameraId} disconnected unexpectedly.")

                    close()
                }
            }

            override fun onError(camera: CameraDevice, error: Int) {
                if (camera == currentCamera) {
                    Log.e(TAG, "Camera ${cameraMetaData.cameraId} encountered an error (code: $error).")
                    close()
                }
            }
        }

        try {
            Log.d(TAG, "Opening camera ${cameraMetaData.cameraId}...")
            cameraManager.openCamera(
                cameraMetaData.cameraId,
                cameraCallback,
                null
            )
        } catch (exception: Exception) {
            Log.e(TAG, "Failed to open camera ${cameraMetaData.cameraId}: ${exception.message}", exception)
        }
    }

    private fun createCameraSession(camera: CameraDevice) {
        val surfaces = surfaceProviders.map { it.getSurface() }
        val outputConfigs = surfaces.map { OutputConfiguration(it) }

        val sessionCallback = object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(session: CameraCaptureSession) {
                Log.i(TAG, "Capture session configured for camera ${cameraMetaData.cameraId}.")
                val requestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
                    surfaces.forEach { addTarget(it) }
                }

                try {
                    session.setRepeatingRequest(requestBuilder.build(), null, null)
                    this@PassthroughCameraManager.session = session
                    Log.i(TAG, "Repeating request started for camera ${cameraMetaData.cameraId}.")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start repeating request: ${e.message}", e)
                    close()
                }
            }

            override fun onConfigureFailed(session: CameraCaptureSession) {
                Log.e(TAG, "Failed to configure capture session for camera ${cameraMetaData.cameraId}.")
                close()
            }
        }

        val sessionConfig = SessionConfiguration(
            SessionConfiguration.SESSION_REGULAR,
            outputConfigs,
            Executors.newSingleThreadExecutor(),
            sessionCallback
        )

        try {
            Log.d(TAG, "Creating capture session for camera ${cameraMetaData.cameraId}...")
            camera.createCaptureSession(sessionConfig)
        } catch (e: Exception) {
            Log.e(TAG, "Exception during capture session creation: ${e.message}", e)
            close()
        }
    }

    companion object {
        private val TAG = PassthroughCameraManager::class.java.simpleName
    }
}