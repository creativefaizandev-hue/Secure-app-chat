package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.auth.GoogleAuthManager
import com.example.call.CallState
import com.example.call.VoiceCallManager
import com.example.data.repository.SecureRepository
import com.example.ui.components.IncomingCallDialog
import com.example.ui.screens.ChatDetailScreen
import com.example.ui.screens.MainHomeScreen
import com.example.ui.screens.UsernameSetupScreen
import com.example.ui.screens.VoiceCallScreen
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme(darkTheme = true) {
        val context = this
        val repository = remember { SecureRepository(context) }
        val authManager = remember { GoogleAuthManager(context, repository) }
        val callManager = remember { VoiceCallManager(context, repository) }
        val authState by authManager.authState.collectAsState()
        androidx.compose.runtime.LaunchedEffect(authState.isAuthenticated) {
            if (authState.isAuthenticated) callManager.listenForIncomingCalls()
        }

        val callState by callManager.callState.collectAsState()
        val uiScope = androidx.compose.runtime.rememberCoroutineScope()
        var activeConversationId by remember { mutableStateOf<String?>(null) }

        Box(modifier = Modifier.fillMaxSize()) {
          // Screen Routing
          if (callState is CallState.Connected || callState is CallState.Dialing || callState is CallState.Ended) {
            VoiceCallScreen(
              callState = callState,
              onMuteToggle = { callManager.toggleMute() },
              onSpeakerToggle = { callManager.toggleSpeaker() },
              onEndCall = { callManager.endCall() },
              onVerificationToggled = { _, _ -> }
            )
          } else if (!authState.isAuthenticated) {
            com.example.ui.screens.SignInRequiredScreen(
              onSignIn = {
                uiScope.launch {
                  authManager.signInWithGoogle(BuildConfig.GOOGLE_WEB_CLIENT_ID)
                }
              }
            )
          } else if (!authState.identityResolved) {
            androidx.compose.material3.CircularProgressIndicator()
          } else if (authState.username == null) {
            UsernameSetupScreen(authManager = authManager)
          } else {
            AnimatedContent(
              targetState = activeConversationId,
              transitionSpec = { fadeIn() togetherWith fadeOut() },
              label = "screen_transition"
            ) { convId ->
              if (convId != null) {
                ChatDetailScreen(
                  conversationId = convId,
                  repository = repository,
                  onBack = { activeConversationId = null },
                  onStartVoiceCall = { contactId, contactName, fingerprint ->
                    callManager.startOutgoingCall(contactId, contactName, fingerprint)
                  }
                )
              } else {
                MainHomeScreen(
                  repository = repository,
                  authManager = authManager,
                  onOpenConversation = { id -> activeConversationId = id },
                  onStartVoiceCall = { contactId, contactName, fingerprint ->
                    callManager.startOutgoingCall(contactId, contactName, fingerprint)
                  },
                  onSimulateIncomingCall = { contactId, contactName, fingerprint ->
                    callManager.triggerIncomingCall(contactId, contactName, fingerprint)
                  }
                )
              }
            }
          }

          // Incoming Call Modal Overlay
          (callState as? CallState.Incoming)?.let { incoming ->
            IncomingCallDialog(
              callState = incoming,
              onAccept = { callManager.acceptIncomingCall() },
              onDecline = { callManager.endCall() }
            )
          }
        }
      }
    }
  }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
  Text(text = "CipherChat Secure E2EE: $name", modifier = modifier)
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
  MyApplicationTheme { Greeting("Android") }
}

