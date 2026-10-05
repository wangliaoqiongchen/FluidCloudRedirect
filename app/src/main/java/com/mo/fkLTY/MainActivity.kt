package com.mo.fkLTY

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.mo.fkLTY.service.PrefsBridge
import com.mo.fkLTY.ui.picker.BrowserPickerScreen
import com.mo.fkLTY.ui.theme.流体云重定向Theme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        PrefsBridge.init()
        setContent {
            流体云重定向Theme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    BrowserPickerScreen()
                }
            }
        }
    }
}
