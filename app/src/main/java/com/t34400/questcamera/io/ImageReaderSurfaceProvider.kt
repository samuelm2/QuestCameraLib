package com.t34400.questcamera.io

import android.graphics.ImageFormat
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import com.t34400.questcamera.core.ISurfaceProvider
import com.t34400.questcamera.json.toJson
import java.io.BufferedOutputStream
import java.io.FileOutputStream
import java.util.concurrent.Executors

class ImageReaderSurfaceProvider(
    private val dataDirectoryManager: DataDirectoryManager,
    width: Int,
    height: Int,
    private val imageFileNamePrefix: String,
    private val formatInfoFileName: String,
    private val bufferPoolSize: Int = 5
): ISurfaceProvider {
    private val handlerThread = HandlerThread("ImageReaderBackground").apply {
        start()
    }
    private val backgroundHandler = Handler(handlerThread.looper)

    private var imageFormatInfo: ImageFormatInfo? = null

    private var shouldSaveFrame = false
    private val saveExecutor = Executors.newSingleThreadExecutor()

    private val bufferPool = MutableList(bufferPoolSize) { ByteArray(width * height + width * height / 2) }
    private var latestBufferPoolIndex = 0

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
        return bufferPool[latestBufferPoolIndex].copyOf()
    }

    fun close() {
        imageReader.close()
        saveExecutor.shutdown()
    }

    private fun processImage(image: Image) {
        if (imageFormatInfo == null) {
            val info = extractImageFormatInfo(image)
            imageFormatInfo = info

            if (shouldSaveFrame) {
                val fileName = "$formatInfoFileName.json"
                val file = dataDirectoryManager.getFile(fileName)

                saveExecutor.execute {
                    try {
                        file?.bufferedWriter()?.use { it.write(info.toJson()) }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }

        val nextBufferPoolIndex = (latestBufferPoolIndex + 1) % bufferPoolSize
        val buffer = bufferPool[nextBufferPoolIndex]

        val data = dumpImageUnsafe(image, buffer)
        bufferPool[nextBufferPoolIndex] = data

        latestBufferPoolIndex = nextBufferPoolIndex

        if (shouldSaveFrame) {
            val fileName = "$imageFileNamePrefix${image.timestamp}.yuv"
            val file = dataDirectoryManager.getFile(fileName)

            saveExecutor.execute {
                try {
                    BufferedOutputStream(FileOutputStream(file)).use { it.write(data) }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    companion object {
        fun dumpImageUnsafe(image: Image, reusedBuffer: ByteArray): ByteArray {
            val requiredSize = calculateDumpBufferSize(image)

            val outputBuffer = if (reusedBuffer.size >= requiredSize) {
                reusedBuffer
            } else {
                ByteArray(requiredSize)
            }

            var offset = 0
            for (plane in image.planes) {
                val buffer = plane.buffer
                buffer.position(0)
                val size = buffer.remaining()
                buffer.get(outputBuffer, offset, size)
                offset += size
            }
            return outputBuffer
        }

        private fun calculateDumpBufferSize(image: Image): Int {
            return image.planes.sumOf { it.buffer.remaining() }
        }
    }
}