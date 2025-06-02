package com.t34400.questcamera.core

import android.content.Context
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import com.t34400.questcamera.helper.CameraPermissionRequestActivity
import java.util.concurrent.Executors

class CameraSessionManager: AutoCloseable {
    private val cameraSurfaceProviders = hashSetOf<ISurfaceProvider>()

    private val handlerThread = HandlerThread("CameraCaptureBackground")
    private var currentCamera: CameraDevice? = null
    private var session: CameraCaptureSession? = null

    private var useCase: Int = CameraDevice.TEMPLATE_STILL_CAPTURE

    override fun close() {
        Log.i(TAG, "Closing camera and cleaning up resources.")

        currentCamera?.close()
        currentCamera = null
        session?.close()
        session = null
        handlerThread.quitSafely()
        try {
            handlerThread.join()
        } catch (e: InterruptedException) {
            Log.e("HandlerThread", "Interrupted while stopping the thread", e)
        }

        Log.i(TAG, "Resources released.")
    }

    fun isOpen(): Boolean {
        return currentCamera != null
    }

    fun registerSurfaceProvider(surfaceProvider: ISurfaceProvider) {
        cameraSurfaceProviders.add(surfaceProvider)
    }

    fun unregisterSurfaceProvider(surfaceProvider: ISurfaceProvider) {
        cameraSurfaceProviders.remove(surfaceProvider)
    }

    fun setCaptureTemplateFromString(mode: String) {
        useCase = when (mode.uppercase()) {
            "PREVIEW" -> CameraDevice.TEMPLATE_PREVIEW
            "STILL_CAPTURE" -> CameraDevice.TEMPLATE_STILL_CAPTURE
            "RECORD" -> CameraDevice.TEMPLATE_RECORD
            "VIDEO_SNAPSHOT" -> CameraDevice.TEMPLATE_VIDEO_SNAPSHOT
            "ZERO_SHUTTER_LAG" -> CameraDevice.TEMPLATE_ZERO_SHUTTER_LAG
            else -> CameraDevice.TEMPLATE_MANUAL
        }
    }

    fun openCamera(context: Context, cameraManager: CameraManager, cameraId: String) {
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
                Log.i(TAG, "Camera $cameraId opened successfully.")

                currentCamera = camera

                createCameraSession(camera, cameraId)
            }

            override fun onDisconnected(camera: CameraDevice) {
                if (camera == currentCamera) {
                    Log.w(TAG, "Camera $cameraId disconnected unexpectedly.")

                    close()
                }
            }

            override fun onError(camera: CameraDevice, error: Int) {
                if (camera == currentCamera) {
                    Log.e(TAG, "Camera $cameraId encountered an error (code: $error).")
                    close()
                }
            }
        }

        try {
            Log.d(TAG, "Opening camera $cameraId...")
            cameraManager.openCamera(
                cameraId,
                cameraCallback,
                Handler(handlerThread.apply { start() }.looper)
            )
        } catch (exception: Exception) {
            Log.e(TAG, "Failed to open camera $cameraId: ${exception.message}", exception)
        }
    }

    private fun createCameraSession(camera: CameraDevice, cameraId: String) {
        val targetSurfaceProviders = cameraSurfaceProviders.toList()

        val surfaces = targetSurfaceProviders.map { it.getSurface() }

        if (surfaces.isEmpty()) {
            Log.w(TAG, "No available surfaces found. Closing camera session for camera $cameraId.")
            close()
            return
        }

        val outputConfigs = surfaces.map { OutputConfiguration(it) }

        val sessionCallback = object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(session: CameraCaptureSession) {
                Log.i(TAG, "Capture session configured for camera $cameraId.")
                val requestBuilder = camera.createCaptureRequest(useCase).apply {
                    surfaces.forEach { addTarget(it) }
                }

                try {
                    session.setRepeatingRequest(requestBuilder.build(), null, null)
                    this@CameraSessionManager.session = session
                    Log.i(TAG, "Repeating request started for camera $cameraId.")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start repeating request: ${e.message}", e)
                    close()
                }
            }

            override fun onConfigureFailed(session: CameraCaptureSession) {
                Log.e(TAG, "Failed to configure capture session for camera $cameraId.")
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
            Log.d(TAG, "Creating capture session for camera $cameraId...")
            camera.createCaptureSession(sessionConfig)
        } catch (e: Exception) {
            Log.e(TAG, "Exception during capture session creation: ${e.message}", e)
            close()
        }
    }

    companion object {
        private val TAG = CameraSessionManager::class.java.simpleName
    }
}