package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.screens.EdexDashboardScreen
import com.example.ui.theme.EdexTheme
import com.example.ui.viewmodel.EdexViewModel

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge(
      statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
      navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
    )
    setContent {
      val viewModel: EdexViewModel = viewModel()
      val prefs by viewModel.preferences.collectAsStateWithLifecycle()
      EdexTheme(themeId = prefs.themeId) { EdexDashboardScreen(viewModel = viewModel) }
    }
  }
}
