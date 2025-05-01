package com.t34400.questcamera.core

import android.content.Context
import android.hardware.camera2.CameraManager
import android.util.Log
import androidx.core.content.getSystemService
import com.t34400.questcamera.helper.CameraPermissionRequestActivity
import com.t34400.questcamera.json.toJson

class CameraPermissionManager (
    private val context: Context
) {
    private var cameraManager: CameraManager? = null

    private var _leftCameraMetadata: CameraMetadata? = null
    private var _rightCameraMetadata: CameraMetadata? = null

    val leftCameraMetaData: CameraMetadata?
        get() = _leftCameraMetadata
    val rightCameraMetaData: CameraMetadata?
        get() = _rightCameraMetadata

    fun hasCameraManager(): Boolean = cameraManager != null

    fun hasCameraPermission(): Boolean {
        return CameraPermissionRequestActivity.checkSelfPermission(context)
    }

    fun getCameraManager(): CameraManager? {
        return cameraManager
    }

    fun getLeftCameraId(): String {
        return leftCameraMetaData?.cameraId ?: ""
    }
    fun getRightCameraId(): String {
        return rightCameraMetaData?.cameraId ?: ""
    }

    fun getLeftCameraMetaDataJson(): String {
        return leftCameraMetaData?.toJson() ?: ""
    }

    fun getRightCameraMetaDataJson(): String {
        return rightCameraMetaData?.toJson() ?: ""
    }

    fun requestCameraPermissionIfNeeded() {
        CameraPermissionRequestActivity.permissionResultDispatcher.addListener { result ->
            Log.d(TAG, "Camera Permission Request Result = $result")

            if (result) {
                context.getSystemService<CameraManager>()?.let { cameraManager ->
                    this.cameraManager = cameraManager

                    val cameraIdList = cameraManager.cameraIdList
                    Log.d(TAG, "Found ${cameraIdList.size} camera(s)")

                    for (id in cameraIdList) {
                        getCameraMetaData(cameraManager, id)?.let { metaData ->
                            if (metaData.isPassthroughCamera) {
                                val position = metaData.cameraPosition

                                if (position == CameraPosition.Left) {
                                    _leftCameraMetadata = metaData
                                }
                                else if (position == CameraPosition.Right) {
                                    _rightCameraMetadata = metaData
                                }
                            }
                        }
                    }
                } ?: {
                    Log.e(TAG, "Failed to obtain a CameraManager instance.")
                }
            }
        }

        CameraPermissionRequestActivity.requestCameraPermissionIfNeeded(context)
    }

    companion object {
        private val TAG = CameraPermissionManager::class.java.simpleName
    }
}