package com.samusynth.questcamera.io

import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import com.github.luben.zstd.Zstd
import com.github.luben.zstd.ZstdCompressCtx
import com.samusynth.questcamera.core.ISurfaceProvider
import com.samusynth.questcamera.json.toJson
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Captures YUV_420_888 frames from an ImageReader and writes them to disk on request.
 *
 * Frames are written either raw (`<unixMs>.yuv`) or, with [compressionEnabled], as a zstd frame of exactly
 * the same bytes (`<unixMs>.yuv.zst`). Compression runs on the single-threaded save executor, never on the
 * camera thread, so frame timing is unaffected. Every file is first written as `<name>.tmp` in the same
 * directory and renamed when complete, so a concurrent `adb pull` never sees a partial frame.
 */
class ImageReaderSurfaceProvider @JvmOverloads constructor(
    private val width: Int,
    private val height: Int,
    imageFileDirPath: String,
    formatInfoFilePath: String,
    private val bufferPoolSize: Int = 5,
    compressionEnabled: Boolean = false,
    compressionLevel: Int = DEFAULT_COMPRESSION_LEVEL
): ISurfaceProvider, AutoCloseable {
    /** Whether new frames are zstd-compressed. Can be changed at runtime; applies to frames captured afterwards. */
    @Volatile var compressionEnabled: Boolean = compressionEnabled
        private set

    /** zstd level for new frames (1..19). Falls back to 1 automatically when level 3 is too slow for this device. */
    @Volatile var compressionLevel: Int = compressionLevel.coerceIn(MIN_COMPRESSION_LEVEL, MAX_COMPRESSION_LEVEL)
        private set

    // Compression state: only ever touched on saveExecutor's single thread.
    private var compressCtx: ZstdCompressCtx? = null
    private var compressBuffer: ByteArray = ByteArray(0)
    private var statFrames = 0L
    private var statWindowFrames = 0
    private var statWindowNs = 0L
    private var statRawBytes = 0L
    private var statCompressedBytes = 0L
    private var statNs = 0L
    private var autoFallbackDone = false
    private val handlerThread = HandlerThread("ImageReaderBackground").apply {
        start()
    }
    private val backgroundHandler = Handler(handlerThread.looper)

    private var directory: File = File(imageFileDirPath)
    private var formatInfoFilePath: String = formatInfoFilePath

    private var imageFormatInfo: ImageFormatInfo? = null

    // One-shot flag: capture next available frame
    // AtomicBoolean ensures thread-safe compare-and-set operation (prevents race conditions)
    private val shouldCaptureNextFrame = AtomicBoolean(false)
    
    private val saveExecutor = Executors.newSingleThreadExecutor()

    private val bufferPool = MutableList(bufferPoolSize) { ByteArray(width * height + width * height / 2) }
    private var latestBufferPoolIndex = 0

    private var baseMonoTimeNs: Long
    private var baseUnixTimeMs: Long

    private val imageReader = ImageReader.newInstance(
        width,
        height,
        ImageFormat.YUV_420_888,
        2
    ).apply {
        setOnImageAvailableListener({ reader ->
            // Use acquireLatestImage() to get the most recent frame, not the oldest
            // This prevents capturing old buffered frames when capture starts
            reader.acquireLatestImage()?.use { image ->
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

    /**
     * Resets the base time for timestamp calculations.
     * Called when recording starts to sync with depth system's base time.
     */
    fun resetBaseTime() {
        baseMonoTimeNs = System.nanoTime()
        baseUnixTimeMs = System.currentTimeMillis()
        Log.d(TAG, "Base time reset: baseMonoTimeNs=$baseMonoTimeNs, baseUnixTimeMs=$baseUnixTimeMs")
    }

    /**
     * Updates the output directory paths for a new recording session.
     * Called when starting a new recording session with a new timestamped directory.
     * Also resets format info cache to ensure it's written to the new location.
     */
    fun updateDirectoryPaths(newImageDir: String, newFormatInfoPath: String) {
        directory = File(newImageDir)
        formatInfoFilePath = newFormatInfoPath
        imageFormatInfo = null  // Force re-write to new location
        
        // Create directory if it doesn't exist
        if (!directory.exists()) {
            directory.mkdirs()
        }
        
        Log.d(TAG, "Updated paths: imageDir=$newImageDir, formatInfo=$newFormatInfoPath")
    }

    /**
     * Signals the camera to capture the next available frame.
     * Called by Unity when CaptureCoordinator determines a frame should be captured.
     * Flag auto-resets after capture.
     */
    fun captureNextFrame() {
        shouldCaptureNextFrame.set(true)
        Log.d(TAG, "Capture signal received - will save next frame")
    }

    fun getLatestImageBytes(): ByteArray {
        val byteArray = bufferPool[latestBufferPoolIndex].copyOf()
        return nv12ByteArrayToJpeg(byteArray, width = width, height = height)
    }

    /**
     * Turns frame compression on or off and sets the zstd level for frames captured from now on.
     * Note that the image-format JSON records the setting that was active at the first captured frame
     * of a session; the file extension (.yuv vs .yuv.zst) is authoritative per frame.
     */
    fun setCompression(enabled: Boolean, level: Int) {
        compressionEnabled = enabled
        compressionLevel = level.coerceIn(MIN_COMPRESSION_LEVEL, MAX_COMPRESSION_LEVEL)
        autoFallbackDone = false
        Log.i(TAG, "Frame compression ${if (enabled) "on" else "off"}, zstd level $compressionLevel")
    }

    private fun compressionName(): String = if (compressionEnabled) "zstd" else "none"

    override fun close() {
        imageReader.close()
        saveExecutor.execute {
            compressCtx?.close()
            compressCtx = null
        }
        saveExecutor.shutdown()
    }

    private fun processImage(image: Image) {
        // Atomic compare-and-set: only proceed if flag is true, and atomically set it to false
        // This prevents race conditions where multiple frames might see the flag as true
        // compareAndSet returns true only for the FIRST frame that sees true
        if (!shouldCaptureNextFrame.compareAndSet(true, false)) {
            return  // Either flag was false, or another thread already captured
        }

        Log.d(TAG, "Capturing frame in response to signal")

        if (imageFormatInfo == null) {
            val baseTime = BaseTime(
                baseMonoTimeNs = baseMonoTimeNs,
                baseUnixTimeMs = baseUnixTimeMs,
            )
            val info = extractImageFormatInfo(image, baseTime = baseTime, compression = compressionName())
            imageFormatInfo = info

            // Save format info on first capture (temp file + rename so a puller never sees a partial JSON)
            val formatInfoFile = File(formatInfoFilePath)
            saveExecutor.execute {
                try {
                    val tmp = File(formatInfoFile.path + TEMP_SUFFIX)
                    tmp.bufferedWriter().use { it.write(info.toJson()) }
                    renameInto(tmp, formatInfoFile)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        val nextBufferPoolIndex = (latestBufferPoolIndex + 1) % bufferPoolSize
        val buffer = bufferPool[nextBufferPoolIndex]

        val data = dumpImageUnsafe(image, buffer)
        bufferPool[nextBufferPoolIndex] = data

        latestBufferPoolIndex = nextBufferPoolIndex

        // Save image data (we only reach here if capture was signaled)
        val unixTime = computeUnixTime(image.timestamp)
        val compress = compressionEnabled
        val level = compressionLevel
        val fileName = if (compress) "$unixTime.yuv.zst" else "$unixTime.yuv"
        val file = File(directory, fileName)

        saveExecutor.execute {
            try {
                val tmp = File(directory, fileName + TEMP_SUFFIX)
                if (compress) {
                    writeCompressed(data, tmp, level)
                } else {
                    BufferedOutputStream(FileOutputStream(tmp)).use { it.write(data) }
                }
                renameInto(tmp, file)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /** Runs on saveExecutor. Compresses the exact bytes that would otherwise be written raw. */
    private fun writeCompressed(data: ByteArray, tmp: File, level: Int) {
        val ctx = compressCtx ?: ZstdCompressCtx().also { compressCtx = it }
        ctx.setLevel(level)

        val bound = Zstd.compressBound(data.size.toLong()).toInt()
        if (compressBuffer.size < bound) {
            compressBuffer = ByteArray(bound)
        }

        val start = System.nanoTime()
        val compressedSize = ctx.compressByteArray(compressBuffer, 0, compressBuffer.size, data, 0, data.size)
        val elapsed = System.nanoTime() - start

        FileOutputStream(tmp).use { it.write(compressBuffer, 0, compressedSize) }

        recordStats(level, data.size, compressedSize, elapsed)
    }

    /** Runs on saveExecutor. Logs mean compress time and ratio every [STAT_LOG_INTERVAL] frames; drops to level 1 if too slow. */
    private fun recordStats(level: Int, rawSize: Int, compressedSize: Int, elapsedNs: Long) {
        statFrames++
        statNs += elapsedNs
        statRawBytes += rawSize
        statCompressedBytes += compressedSize
        statWindowFrames++
        statWindowNs += elapsedNs

        if (statWindowFrames >= FALLBACK_WINDOW_FRAMES) {
            val windowMeanMs = statWindowNs / 1e6 / statWindowFrames
            if (!autoFallbackDone && level > MIN_COMPRESSION_LEVEL && windowMeanMs > FALLBACK_MEAN_MS) {
                autoFallbackDone = true
                compressionLevel = MIN_COMPRESSION_LEVEL
                Log.w(TAG, "zstd level $level averaged %.1f ms per frame over $statWindowFrames frames (> $FALLBACK_MEAN_MS ms); falling back to level $MIN_COMPRESSION_LEVEL".format(windowMeanMs))
            }
            statWindowFrames = 0
            statWindowNs = 0L
        }

        if (statFrames % STAT_LOG_INTERVAL == 0L) {
            val meanMs = statNs / 1e6 / statFrames
            val ratio = statCompressedBytes.toDouble() / statRawBytes.toDouble()
            Log.i(TAG, "zstd stats: $statFrames frames, level $compressionLevel, mean %.1f ms/frame, %.1f%% of raw (%.2f MB -> %.2f MB per frame)".format(
                meanMs, ratio * 100.0, rawSize / 1e6, compressedSize / 1e6))
        }
    }

    /** Atomically publishes [tmp] as [target]; falls back to copy+delete if the rename fails. */
    private fun renameInto(tmp: File, target: File) {
        if (target.exists()) target.delete()
        if (!tmp.renameTo(target)) {
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
            Log.w(TAG, "rename failed, copied instead: ${target.name}")
        }
    }

    private fun computeUnixTime(imageTimestamp: Long): Long {
        val deltaNs = imageTimestamp - baseMonoTimeNs
        return baseUnixTimeMs + (deltaNs / 1_000_000)
    }

    companion object {
        private val TAG = ImageReaderSurfaceProvider::class.java.simpleName

        const val DEFAULT_COMPRESSION_LEVEL = 3
        const val MIN_COMPRESSION_LEVEL = 1
        const val MAX_COMPRESSION_LEVEL = 19
        /** Files are written under this suffix and renamed when complete. */
        const val TEMP_SUFFIX = ".tmp"
        private const val STAT_LOG_INTERVAL = 100L
        private const val FALLBACK_WINDOW_FRAMES = 20
        private const val FALLBACK_MEAN_MS = 100.0

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