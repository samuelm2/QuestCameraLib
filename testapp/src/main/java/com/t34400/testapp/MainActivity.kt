package com.t34400.testapp

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.t34400.questcamera.CameraPermissionRequestActivity
import com.t34400.testapp.ui.theme.QuestCameraTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContent {
            QuestCameraTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Greeting(
                        name = "Android",
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()

        val hasCameraPermission = CameraPermissionRequestActivity.checkSelfPermission(this)
        Log.d("Test", "Has Camera Permission = $hasCameraPermission")

        CameraPermissionRequestActivity.permissionResultDispatcher.addListener { result ->
            Log.d("Test", "Camera Permission Request Result = $result")
        }
        CameraPermissionRequestActivity.requestCameraPermissionIfNeeded(this)
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    QuestCameraTheme {
        Greeting("Android")
    }
}