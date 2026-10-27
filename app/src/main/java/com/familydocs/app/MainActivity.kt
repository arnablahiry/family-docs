package com.familydocs.app

import android.os.Bundle
import android.view.WindowManager
import android.graphics.Color
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import com.familydocs.app.ui.components.LocalThemeReveal
import com.familydocs.app.ui.components.ThemeReveal
import com.familydocs.app.ui.components.ThemeRevealOverlay
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.familydocs.app.security.ThemeMode
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.familydocs.app.ui.components.LocalContainer
import com.familydocs.app.ui.nav.AppRoot
import com.familydocs.app.ui.theme.FamilyDocsTheme
import kotlinx.coroutines.launch

/** FragmentActivity (not plain ComponentActivity) because BiometricPrompt needs it. */
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as FamilyDocsApp).container

        // FLAG_SECURE: no screenshots, blank thumbnail in Recents.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                container.settings.state.collect { s ->
                    if (s.blockScreenshots) window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
                    else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }
        }

        setContent {
            val settings by container.settings.state.collectAsStateWithLifecycle()
            val dark = when (settings.theme) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            // Keep status/navigation bar icons readable in the chosen theme.
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                    navigationBarStyle = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                )
                onDispose {}
            }
            val reveal = remember { ThemeReveal() }
            // Everything the user sees is recorded into this layer so the theme switch can freeze a picture of it.
            val screen = rememberGraphicsLayer()
            reveal.capture = { screen.toImageBitmap() }
            CompositionLocalProvider(LocalContainer provides container, LocalThemeReveal provides reveal) {
                FamilyDocsTheme(dark = dark) {
                    Box(Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxSize().drawWithContent {
                            screen.record { this@drawWithContent.drawContent() }
                            drawLayer(screen)
                        }) { AppRoot(this@MainActivity) }
                        ThemeRevealOverlay(reveal)
                    }
                }
            }
        }
    }
}
