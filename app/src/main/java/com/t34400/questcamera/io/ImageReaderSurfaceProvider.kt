package com.t34400.questcamera.io

import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import com.t34400.questcamera.core.ISurfaceProvider
import com.t34400.questcamera.json.toJson
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors

class ImageReaderSurfaceProvider(
    private val width: Int,
    private val height: Int,
    imageFileDirPath: String,
    private val formatInfoFilePath: String,
    private val bufferPoolSize: Int = 5
): ISurfaceProvider, AutoCloseable {
    private val handlerThread = HandlerThread("ImageReaderBackground").apply {
        start()
    }
    private val backgroundHandler = Handler(handlerThread.looper)

    private val directory: File = File(imageFileDirPath)

    private var imageFormatInfo: ImageFormatInfo? = null

    private var shouldSaveFrame = false
    private val saveExecutor = Executors.newSingleThreadExecutor()

    private val bufferPool = MutableList(bufferPoolSize) { ByteArray(width * height + width * height / 2) }
    private var latestBufferPoolIndex = 0

    private val baseMonoTimeNs: Long
    private val baseUnixTimeMs: Long

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

    init {
        if (!directory.exists()) {
            val created = directory.mkdirs()
            if (!created) {
                Log.e(TAG, "Failed to create image file directory.")
            }
        }

        baseMonoTimeNs = System.nanoTime()
        baseUnixTimeMs = System.currentTimeMillis()

        Log.d(TAG, "[Time Log] Base Mono Time (ns): $baseMonoTimeNs, Base Unix Time (ms): $baseUnixTimeMs")
    }

    override fun getSurface(): Surface {
        return imageReader.surface
    }

    fun setShouldSaveFrame(shouldSaveFrame: Boolean) {
        this.shouldSaveFrame = shouldSaveFrame
    }

    fun getLatestImageBytes(): ByteArray {
        val byteArray = bufferPool[latestBufferPoolIndex].copyOf()
        return nv12ByteArrayToJpeg(byteArray, width = width, height = height)
    }

    override fun close() {
        imageReader.close()
        saveExecutor.shutdown()
    }

    private fun processImage(image: Image) {
        if (imageFormatInfo == null) {
            val baseTime = BaseTime(
                baseMonoTimeNs = baseMonoTimeNs,
                baseUnixTimeMs = baseUnixTimeMs,
            )
            val info = extractImageFormatInfo(image, baseTime = baseTime)
            imageFormatInfo = info

            if (shouldSaveFrame) {
                val formatInfoFIle = File(formatInfoFilePath)

                saveExecutor.execute {
                    try {
                        formatInfoFIle.bufferedWriter().use { it.write(info.toJson()) }
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
            val fileName = "${computeUnixTime(image.timestamp)}.yuv"
            val file = File(directory, fileName)

            saveExecutor.execute {
                try {
                    BufferedOutputStream(FileOutputStream(file)).use { it.write(data) }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun computeUnixTime(imageTimestamp: Long): Long {
        val deltaNs = imageTimestamp - baseMonoTimeNs
        return baseUnixTimeMs + (deltaNs / 1_000_000)
    }

    companion object {
        private val TAG = ImageReaderSurfaceProvider::class.java.simpleName

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

        private fun convertNV12ToNV21(nv12: ByteArray, width: Int, height: Int): ByteArray {
            val ySize = width * height
            val uvSize = ySize / 2
            val nv21 = nv12.copyOf()

            var i = ySize
            while (i < ySize + uvSize - 1) {
                val u = nv12[i]
                nv21[i] = nv12[i + 1]
                nv21[i + 1] = u
                i += 2
            }

            return nv21
        }

        private fun nv12ByteArrayToJpeg(nv12: ByteArray, width: Int, height: Int): ByteArray {
            val nv21 = convertNV12ToNV21(nv12, width, height)
            val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
            val output = ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0, width, height), 95, output)
            return output.toByteArray()
        }

    }
}