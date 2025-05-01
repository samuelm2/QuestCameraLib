package com.t34400.questcamera.io

import android.graphics.ImageFormat
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import com.t34400.questcamera.core.ISurfaceProvider
import java.io.FileOutputStream

class ImageReaderSurfaceProvider(
    val dataDirectoryManager: DataDirectoryManager,
    width: Int,
    height: Int,
    private val fileNamePrefix: String
): ISurfaceProvider {
    private val handlerThread = HandlerThread("ImageReaderBackground").apply {
        start()
    }
    private val backgroundHandler = Handler(handlerThread.looper)

    private var shouldSaveFrame = false

    private var latestImageBytes: ByteArray = ByteArray(0)

    private val imageReader = ImageReader.newInstance(
        width,
        height,
        ImageFormat.YUV_420_888,
        2
    ).apply {
        setOnImageAvailableListener({ reader ->
            reader.acquireNextImage().use { image ->
                processImage(image)
            }
        }, backgroundHandler)
    }

    override fun getSurface(): Surface {
        return imageReader.surface
    }

    fun setShouldSaveFrame(shouldSaveFrame: Boolean) {
        this.shouldSaveFrame = shouldSaveFrame
    }

    fun getLatestImageBytes(): ByteArray {
        return latestImageBytes
    }

    fun close() {
        imageReader.close()
    }

    private fun processImage(image: Image) {
        val yPlane = image.planes[0].buffer
        val uPlane = image.planes[1].buffer
        val vPlane = image.planes[2].buffer

        val ySize = yPlane.remaining()
        val uSize = uPlane.remaining()
        val vSize = vPlane.remaining()

        val data = ByteArray(ySize + uSize + vSize)
        yPlane.get(data, 0, ySize)
        uPlane.get(data, ySize, uSize)
        vPlane.get(data, ySize + uSize, vSize)

        latestImageBytes = data

        if (shouldSaveFrame) {
            val fileName = "$fileNamePrefix${image.timestamp}.yuv"

            val file = dataDirectoryManager.getFile(fileName)
            FileOutputStream(file).use { it.write(data) }
        }
    }
}