package com.samusynth.testapp

import android.view.Surface
import android.view.SurfaceView
import com.samusynth.questcamera.core.ISurfaceProvider

class SurfaceViewWrapper(private val surfaceView: SurfaceView): ISurfaceProvider {
    override fun getSurface(): Surface {
        return surfaceView.holder.surface
    }
}