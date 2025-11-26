package com.samusynth.questcamera.io

import android.graphics.ImageFormat
import android.media.Image
import kotlinx.serialization.Serializable

@Serializable
data class ImageFormatInfo(
    val width: Int,
    val height: Int,
    val format: String,
    val planes: List<PlaneFormat>,
    val baseTime: BaseTime
)

@Serializable
data class PlaneFormat(
    val rowStride: Int,
    val pixelStride: Int,
    val bufferSize: Int
)

@Serializable
data class BaseTime(
    val baseMonoTimeNs: Long,
    val baseUnixTimeMs: Long
)

fun imageFormatToString(format: Int): String = when (format) {
    ImageFormat.YUV_420_888 -> "YUV_420_888"
    ImageFormat.NV21 -> "NV21"
    ImageFormat.JPEG -> "JPEG"
    ImageFormat.YUV_422_888 -> "YUV_422_888"
    ImageFormat.RAW_SENSOR -> "RAW_SENSOR"
    ImageFormat.RGB_565 -> "RGB_565"
    else -> "UNKNOWN($format)"
}

fun extractImageFormatInfo(image: Image, baseTime: BaseTime): ImageFormatInfo {
    return ImageFormatInfo(
        width = image.width,
        height = image.height,
        format = imageFormatToString(image.format),
        planes = image.planes.map {
            PlaneFormat(
                rowStride = it.rowStride,
                pixelStride = it.pixelStride,
                bufferSize = it.buffer.remaining()
            )
        },
        baseTime = baseTime
    )
}