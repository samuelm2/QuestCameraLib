package com.t34400.questcamera.core

import android.view.Surface

interface ISurfaceProvider : AutoCloseable {
    fun getSurface() : Surface
    override fun close()
}