package dev.devforge

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.devforge.ui.DevForgeApp
import dev.devforge.ui.theme.DevForgeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as DevForgeApplication).container
        setContent {
            DevForgeTheme {
                DevForgeApp(container)
            }
        }
    }
}
