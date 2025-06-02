package com.t34400.questcamera.helper

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.t34400.questcamera.core.EventDispatcher

@SuppressLint("RestrictedApi")
class CameraPermissionRequestActivity : ComponentActivity() {

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val cameraGranted = permissions[CAMERA_PERMISSION] == true
            val headsetCameraGranted = permissions[HEADSET_CAMERA_PERMISSION] == true

            permissionResultDispatcher.dispatch(cameraGranted && headsetCameraGranted)
            finish()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPermissionLauncher.launch(
            arrayOf(
                CAMERA_PERMISSION,
                HEADSET_CAMERA_PERMISSION
            )
        )
    }

    companion object {
        private const val CAMERA_PERMISSION : String = Manifest.permission.CAMERA
        private const val HEADSET_CAMERA_PERMISSION : String = "horizonos.permission.HEADSET_CAMERA"

        val permissionResultDispatcher = EventDispatcher<Boolean>()

        fun checkSelfPermission(context: Context) : Boolean {
            return areAllPermissionsGranted(
                context,
                arrayOf(
                    CAMERA_PERMISSION,
                    HEADSET_CAMERA_PERMISSION
                )
            )
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

        fun areAllPermissionsGranted(context: Context, permissions: Array<String>): Boolean {
            return permissions.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
        }
    }
}