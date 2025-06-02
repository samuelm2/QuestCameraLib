package com.t34400.questcamera.json

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.t34400.questcamera.core.CameraMetadata
import com.t34400.questcamera.io.ImageFormatInfo

fun CameraMetadata.toJson(pretty: Boolean = false): String {
    val json = if (pretty) {
        Json { prettyPrint = true }
    } else {
        Json
    }
    return json.encodeToString(this)
}

fun ImageFormatInfo.toJson(pretty: Boolean = false): String {
    val json = if (pretty) {
        Json { prettyPrint = true }
    } else {
        Json
    }
    return json.encodeToString(this)
}