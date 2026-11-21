package com.example

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.auth.authStateFlow
import com.example.auth.signOutWithCleanup
import com.example.data.EdexPreferencesRepository
import com.example.data.FirestoreHudSyncRepository
import com.example.model.EdexUserPreferences
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.EdexDashboardScreen
import com.example.ui.theme.EdexTheme
import com.example.ui.viewmodel.EdexViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge(
      statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
      navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
    )
    setContent {
      val context = LocalContext.current
      val prefsRepo = remember(context) { EdexPreferencesRepository(context.applicationContext) }
      val cachedPrefs by
        prefsRepo.preferences.collectAsStateWithLifecycle(initialValue = EdexUserPreferences())
      EdexTheme(themeId = cachedPrefs.themeId) { AppContent() }
    }
  }
}

@Composable
private fun AppContent() {
  val currentUser by
    remember { Firebase.auth.authStateFlow() }
      .collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)

  val user = currentUser
  if (user == null) {
    AuthScreen(onAuthSuccess = {})
  } else {
    key(user.uid) {
      val context = LocalContext.current
      val app = context.applicationContext as Application
      val credentialManager = remember(context) { CredentialManager.create(context) }
      val scope = rememberCoroutineScope()
      val factory =
        remember(app, user.uid) {
          viewModelFactory {
            initializer {
              val databaseId = app.getString(R.string.firestore_database_id)
              val db = FirebaseFirestore.getInstance(databaseId)
              val syncRepo = FirestoreHudSyncRepository(db, Firebase.auth)
              val displayName =
                user.displayName?.takeIf { it.isNotBlank() }
                  ?: user.email?.substringBefore('@')?.takeIf { it.isNotBlank() }
                  ?: "OPERATOR"
              EdexViewModel(
                application = app,
                cloudSyncRepo = syncRepo,
                currentUserId = user.uid,
                operatorDisplayName = displayName,
              )
            }
          }
        }
      val viewModel: EdexViewModel = viewModel(key = user.uid, factory = factory)
      EdexDashboardScreen(
        viewModel = viewModel,
        onSignOut = {
          signOutWithCleanup(
            credentialManager = credentialManager,
            scope = scope,
            onBeforeSignOut = { viewModel.closeSettings() },
          )
        },
      )
    }
  }
}
