package com.t34400.questcamera.io

import com.t34400.questcamera.core.CameraPermissionManager
import com.t34400.questcamera.json.toJson

class MetaDataWriter {
    companion object {
        fun writeMetaData(
            dataDirectoryManager: DataDirectoryManager,
            cameraPermissionManager: CameraPermissionManager,
            leftCameraMetaDataJsonName: String = "left_camera_characteristic.json",
            rightCameraMetaDataJsonName: String = "right_camera_characteristic.json",
        ) {
            cameraPermissionManager.leftCameraMetaData?.let { metaData ->
                dataDirectoryManager.getFile(leftCameraMetaDataJsonName)
                    ?.writeText(metaData.toJson())
            }
            cameraPermissionManager.rightCameraMetaData?.let { metaData ->
                dataDirectoryManager.getFile(rightCameraMetaDataJsonName)
                    ?.writeText(metaData.toJson())
            }
        }
    }
}