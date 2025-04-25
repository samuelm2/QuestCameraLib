package com.t34400.questcamera

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

@SuppressLint("RestrictedApi")
class CameraPermissionRequestActivity : ComponentActivity() {

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            permissionResultDispatcher.dispatch(isGranted)
            finish()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPermissionLauncher.launch(CAMERA_PERMISSION)
    }

    companion object {
        private const val CAMERA_PERMISSION : String = Manifest.permission.CAMERA

        val permissionResultDispatcher = EventDispatcher<Boolean>()

        fun checkSelfPermission(context: Context) : Boolean {
            return ContextCompat.checkSelfPermission(
                context,
                CAMERA_PERMISSION
            ) == PackageManager.PERMISSION_GRANTED
        }

        fun requestCameraPermissionIfNeeded(context: Context) {
            if (checkSelfPermission(context)) {
                permissionResultDispatcher.dispatch(true)
                return
            }

            context.startActivity(
                Intent(context, CameraPermissionRequestActivity::class.java)
            )
        }
    }
}