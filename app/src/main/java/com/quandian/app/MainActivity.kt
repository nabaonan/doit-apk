package com.quandian.app

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quandian.app.ui.MainScreen
import com.quandian.app.ui.MainViewModel
import com.quandian.app.ui.theme.QuandianTheme
import com.quandian.app.ui.theme.resolveDark

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

            QuandianTheme(dark = dark) {
                val voiceLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.StartActivityForResult(),
                ) { result ->
                    if (result.resultCode == Activity.RESULT_OK) {
                        val heard = result.data
                            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                            ?.firstOrNull()
                        if (!heard.isNullOrBlank()) vm.consumeVoice(heard) else vm.say("没听清，再说一次？")
                    }
                }

                val onVoice: () -> Unit = remember(voiceLauncher) {
                    {
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(
                                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                            )
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
                            putExtra(RecognizerIntent.EXTRA_PROMPT, "说一件要做的事")
                        }
                        if (intent.resolveActivity(packageManager) == null) {
                            vm.say("这台设备没有语音识别服务")
                        } else {
                            runCatching { voiceLauncher.launch(intent) }
                                .onFailure { vm.say("语音输入打不开，换手动记一条？") }
                        }
                    }
                }

                MainScreen(vm = vm, onVoice = onVoice)
            }
        }
    }
}
