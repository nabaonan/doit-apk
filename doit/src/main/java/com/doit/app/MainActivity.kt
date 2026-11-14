package com.doit.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.doit.app.ui.MainScreen
import com.doit.app.ui.MainViewModel
import com.doit.app.ui.theme.DoitTheme
import com.doit.app.ui.theme.resolveDark

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        setContent {
            val vm = viewModel
            // 主题模式存在 DataStore 里：跟随系统 / 浅色 / 深色
            val state by vm.uiState.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()
            val dark = resolveDark(state.settings.themeMode, systemDark)

            // 深色下把系统栏手势条也调成浅色图标（enableEdgeToEdge 的 auto 只认系统状态，
            // 用户在应用里强制深色时得再纠一次）
            LaunchedEffect(dark) {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }

            DoitTheme(dark = dark) {
                MainScreen(vm = vm)
            }
        }
    }
}
