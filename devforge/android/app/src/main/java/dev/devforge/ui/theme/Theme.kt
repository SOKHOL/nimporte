package dev.devforge.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Light = lightColorScheme(
    primary = Color(0xFF3949AB),
    secondary = Color(0xFF00897B),
    tertiary = Color(0xFFF57C00),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF9FA8DA),
    secondary = Color(0xFF80CBC4),
    tertiary = Color(0xFFFFB74D),
)

/** Couleurs de statut et de code, lisibles en thème clair comme en thème sombre. */
object AppColors {
    val good = Color(0xFF2E7D32)
    val bad = Color(0xFFC62828)
    val warn = Color(0xFFF9A825)
    val goodBackground = Color(0x402E7D32)
    val badBackground = Color(0x40C62828)
    val warnBackground = Color(0x40F9A825)
    val selectedBackground = Color(0x553D5AFE)

    val codeBackground = Color(0xFF1E1F29)
    val codeText = Color(0xFFE6E6E6)
    val codeKeyword = Color(0xFFC792EA)
    val codeString = Color(0xFFC3E88D)
    val codeComment = Color(0xFF8A96A3)
    val codeNumber = Color(0xFFF78C6C)
    val codeAnnotation = Color(0xFFFFCB6B)
    val codeLineNumber = Color(0xFF6B7280)
}

@Composable
fun DevForgeTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> Dark
        else -> Light
    }
    MaterialTheme(colorScheme = colors, content = content)
}
