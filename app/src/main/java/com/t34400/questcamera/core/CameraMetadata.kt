package com.t34400.questcamera.core

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import kotlinx.serialization.Serializable

enum class CameraPosition {
    Left,
    Right,
    Unknown
}

@Serializable
data class CameraMetadata(
    val cameraId: String,
    val cameraSource: Int?,
    val cameraPositionId: Int?,
    val lensFacing: String,
    val hardwareLevel: String,
    val pose: Pose? = null,
    val intrinsics: Intrinsics? = null,
    val sensor: Sensor? = null
) {
    val isPassthroughCamera: Boolean
        get() = cameraSource == 0
    val cameraPosition: CameraPosition
        get() = when (cameraPositionId) {
            0 -> CameraPosition.Left
            1 -> CameraPosition.Right
            else -> CameraPosition.Unknown
        }
}

@Serializable
data class Pose(
    val translation: List<Float>,
    val rotation: List<Float>
)

@Serializable
data class Intrinsics(
    val fx: Float,
    val fy: Float,
    val cx: Float,
    val cy: Float,
    val skew: Float,
)

@Serializable
data class IntSize(
    val width: Int,
    val height: Int,
)

@Serializable
data class FloatSize(
    val width: Float,
    val height: Float,
)

@Serializable
data class IntRect(
    val top: Int,
    val left: Int,
    val bottom: Int,
    val right: Int,
)

@Serializable
data class Sensor(
    val availableFocalLengths: List<Float>?,
    val physicalSize: FloatSize?,
    val pixelArraySize: IntSize?,
    val activeArraySize: IntRect?
)

fun extractPose(
    characteristics: CameraCharacteristics
): Pose? {
    return characteristics.get(CameraCharacteristics.LENS_POSE_TRANSLATION)?.let { lensTranslation ->
        characteristics.get(CameraCharacteristics.LENS_POSE_ROTATION)?.let { lensRotation ->
            Pose(
                translation = lensTranslation.toList(),
                rotation = lensRotation.toList()
            )
        }
    }
}

fun extractIntrinsics(
    characteristics: CameraCharacteristics
): Intrinsics? {
    return characteristics.get(CameraCharacteristics.LENS_INTRINSIC_CALIBRATION)?.let { intrinsics ->
        if (intrinsics.size < 5) {
            return@let null
        }

        Intrinsics(
            fx = intrinsics[0],
            fy = intrinsics[1],
            cx = intrinsics[2],
            cy = intrinsics[3],
            skew = intrinsics[4],
        )
    }
}

fun extractSensor(
    characteristics: CameraCharacteristics
): Sensor {
    val availableFocalLengths =
        characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
    val sensorPhysicalSize =
        characteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)?.let { size ->
            FloatSize(size.width, size.height)
        }
    val sensorPixelArraySize =
        characteristics.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)?.let { size ->
            IntSize(size.width, size.height)
        }
    val sensorActiveArraySize =
        characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE )?.let { rect ->
            IntRect(rect.top, rect.left, rect.bottom, rect.right)
        }
    return Sensor(
        availableFocalLengths = availableFocalLengths?.toList(),
        physicalSize = sensorPhysicalSize,
        pixelArraySize = sensorPixelArraySize,
        activeArraySize = sensorActiveArraySize,
    )
}

fun getCameraMetaData(
    cameraManager: CameraManager,
    cameraId: String
): CameraMetadata? {
    if (!cameraManager.cameraIdList.contains(cameraId)) {
        return null
    }

    val characteristics = cameraManager.getCameraCharacteristics(cameraId)

    val lensFacing = when (characteristics.get(CameraCharacteristics.LENS_FACING)) {
        CameraCharacteristics.LENS_FACING_FRONT -> "FRONT"
        CameraCharacteristics.LENS_FACING_BACK -> "BACK"
        CameraCharacteristics.LENS_FACING_EXTERNAL -> "EXTERNAL"
        else -> "UNKNOWN"
    }

    val hardwareLevel =
        when (characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)) {
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> "LEGACY"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "LIMITED"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "FULL"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "LEVEL_3"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL -> "EXTERNAL"
            else -> "UNKNOWN"
        }

    val cameraSourceKey =
        characteristics.keys.firstOrNull { it.name == "com.meta.extra_metadata.camera_source" }
    val cameraSourceValue = cameraSourceKey?.let { characteristics.get(it) as? ByteArray }

    val cameraSource = if (cameraSourceValue != null && cameraSourceValue.isNotEmpty()) {
        cameraSourceValue[0].toInt()
    } else null

    val positionKey =
        characteristics.keys.firstOrNull { it.name == "com.meta.extra_metadata.position" }
    val positionValue = positionKey?.let { characteristics.get(it) as? ByteArray }

    val cameraPosition = if (positionValue != null && positionValue.isNotEmpty()) {
        positionValue[0].toInt()
    } else null

    val pose = extractPose(characteristics)
    val intrinsics = extractIntrinsics(characteristics)
    val sensor = extractSensor(characteristics)

    return CameraMetadata(
        cameraId = cameraId,
        cameraSource = cameraSource,
        cameraPositionId = cameraPosition,
        lensFacing = lensFacing,
        hardwareLevel = hardwareLevel,
        pose = pose,
        intrinsics = intrinsics,
        sensor = sensor
    )
}