package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.R
import com.example.auth.attemptAutoSignIn
import com.example.auth.onGoogleSignInClicked
import com.example.ui.components.EdexHudPanel
import com.example.ui.theme.LocalEdexPalette

@Composable
fun AuthScreen(
  onAuthSuccess: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val palette = LocalEdexPalette.current
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val credentialManager = remember(context) { CredentialManager.create(context) }

  var isLoading by remember { mutableStateOf(false) }
  var isAutoChecking by remember { mutableStateOf(true) }
  var authError by remember { mutableStateOf<String?>(null) }

  LaunchedEffect(Unit) {
    attemptAutoSignIn(
      context = context,
      credentialManager = credentialManager,
      onAuthSuccess = {
        isAutoChecking = false
        onAuthSuccess()
      },
      onUnauthenticated = { isAutoChecking = false },
      scope = scope,
    )
  }

  Scaffold(
    containerColor = palette.background,
    contentWindowInsets = WindowInsets(0, 0, 0, 0),
  ) { innerPadding ->
    Box(
      modifier =
        modifier
          .fillMaxSize()
          .padding(innerPadding)
          .statusBarsPadding()
          .background(
            Brush.radialGradient(
              colors = listOf(palette.surface.copy(alpha = 0.78f), palette.background)
            )
          )
          .padding(16.dp),
      contentAlignment = Alignment.Center,
    ) {
      EdexHudPanel(
        title = "eDEX-UI // SECURITY GATE",
        subtitle = if (isAutoChecking) "PROBING TOKEN" else "ZERO-TRUST AUTH",
        modifier = Modifier.fillMaxWidth().widthIn(max = 520.dp),
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Row(
            modifier =
              Modifier.fillMaxWidth()
                .background(palette.background.copy(alpha = 0.7f), CutCornerShape(6.dp))
                .border(1.dp, palette.gridLine, CutCornerShape(6.dp))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              imageVector = Icons.Filled.Security,
              contentDescription = null,
              tint = palette.primaryBright,
              modifier = Modifier.size(28.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = stringResource(R.string.auth_title),
                style = MaterialTheme.typography.titleSmall,
                color = palette.primaryBright,
              )
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = stringResource(R.string.auth_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = palette.textMuted,
              )
            }
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            AuthFeatureChip(
              icon = Icons.Filled.CloudSync,
              label = "FIRESTORE HUD SYNC",
              modifier = Modifier.weight(1f),
            )
            AuthFeatureChip(
              icon = Icons.Filled.Lock,
              label = "ISOLATED USER VAULT",
              modifier = Modifier.weight(1f),
            )
          }

          authError?.let { message ->
            Surface(
              color = palette.danger.copy(alpha = 0.14f),
              border = BorderStroke(1.dp, palette.danger),
              shape = CutCornerShape(6.dp),
              modifier = Modifier.fillMaxWidth().testTag("auth_error_banner"),
            ) {
              Text(
                text = "AUTH FAULT // $message",
                style = MaterialTheme.typography.bodySmall,
                color = palette.danger,
                modifier = Modifier.padding(10.dp),
              )
            }
          }

          Button(
            onClick = {
              authError = null
              isLoading = true
              onGoogleSignInClicked(
                context = context,
                credentialManager = credentialManager,
                onAuthSuccess = {
                  isLoading = false
                  onAuthSuccess()
                },
                onAuthError = { msg ->
                  isLoading = false
                  authError = msg
                },
                scope = scope,
                onAuthCancelled = { isLoading = false },
              )
            },
            enabled = !isLoading,
            colors =
              ButtonDefaults.buttonColors(
                containerColor = palette.primaryBright,
                contentColor = palette.background,
              ),
            shape = CutCornerShape(6.dp),
            modifier =
              Modifier.fillMaxWidth()
                .minimumInteractiveComponentSize()
                .testTag("google_sign_in_button"),
          ) {
            if (isLoading) {
              CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = palette.background,
                strokeWidth = 2.dp,
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = "AUTHENTICATING...",
                style = MaterialTheme.typography.labelMedium,
              )
            } else {
              Text(
                text = stringResource(R.string.auth_sign_in_google),
                style = MaterialTheme.typography.labelMedium,
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun AuthFeatureChip(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  label: String,
  modifier: Modifier = Modifier,
) {
  val palette = LocalEdexPalette.current
  Row(
    modifier =
      modifier
        .background(palette.surfaceElevated.copy(alpha = 0.7f), CutCornerShape(4.dp))
        .border(1.dp, palette.gridLine, CutCornerShape(4.dp))
        .padding(horizontal = 8.dp, vertical = 7.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(imageVector = icon, contentDescription = null, tint = palette.primary, modifier = Modifier.size(14.dp))
    Spacer(modifier = Modifier.width(6.dp))
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = palette.textPrimary,
      maxLines = 1,
    )
  }
}
