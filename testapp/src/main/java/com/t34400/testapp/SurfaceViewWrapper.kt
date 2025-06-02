package com.t34400.testapp

import android.view.Surface
import android.view.SurfaceView
import com.t34400.questcamera.core.ISurfaceProvider

class SurfaceViewWrapper(private val surfaceView: SurfaceView): ISurfaceProvider {
    override fun getSurface(): Surface {
        return surfaceView.holder.surface
    }
}