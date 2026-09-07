package com.example.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.call.CallState
import com.example.crypto.CryptoEngine
import com.example.ui.components.ContactAvatar
import com.example.ui.components.SafetyNumberDialog
import com.example.ui.theme.CardSurfaceDark
import com.example.ui.theme.CardSurfaceElevated
import com.example.ui.theme.CyberEmerald
import com.example.ui.theme.CyberEmeraldDark
import com.example.ui.theme.DangerRose
import com.example.ui.theme.EncryptionCyan
import com.example.ui.theme.ObsidianBackground
import com.example.ui.theme.TextMutedDark
import com.example.ui.theme.TextPrimaryDark
import com.example.ui.theme.TextSecondaryDark

@Composable
fun VoiceCallScreen(
  callState: CallState,
  onMuteToggle: () -> Unit,
  onSpeakerToggle: () -> Unit,
  onEndCall: () -> Unit,
  onVerificationToggled: (String, Boolean) -> Unit,
  modifier: Modifier = Modifier
) {
  var showSafetyDialog by remember { mutableStateOf(false) }

  val contactName = when (callState) {
    is CallState.Connected -> callState.contactName
    is CallState.Dialing -> callState.contactName
    is CallState.Incoming -> callState.contactName
    is CallState.Ended -> callState.contactName
    else -> "Encrypted Call"
  }

  val contactId = when (callState) {
    is CallState.Connected -> callState.contactId
    is CallState.Dialing -> callState.contactId
    is CallState.Incoming -> callState.contactId
    is CallState.Ended -> callState.contactId
    else -> ""
  }

  val fingerprint = when (callState) {
    is CallState.Connected -> callState.fingerprint
    is CallState.Dialing -> callState.fingerprint
    is CallState.Incoming -> callState.fingerprint
    else -> "SHA256:E2EE:SESSION"
  }

  val isSafetyVerified = when (callState) {
    is CallState.Connected -> callState.isSafetyVerified
    else -> true
  }

  val sasWords = when (callState) {
    is CallState.Connected -> callState.sasMnemonic
    else -> CryptoEngine.generateSasWords("me", contactId)
  }

  val safetyNumber = remember(contactId) {
    CryptoEngine.generateSafetyNumber("me", contactId)
  }

  // Pulsing animation for audio activity
  val infiniteTransition = rememberInfiniteTransition(label = "pulse")
  val pulseScale by infiniteTransition.animateFloat(
    initialValue = 1.0f,
    targetValue = 1.15f,
    animationSpec = infiniteRepeatable(
      animation = tween(1200, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "avatarPulse"
  )

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(
        Brush.verticalGradient(
          colors = listOf(
            ObsidianBackground,
            Color(0xFF07141E),
            Color(0xFF031E1E),
            ObsidianBackground
          )
        )
      )
      .statusBarsPadding()
      .navigationBarsPadding()
      .testTag("voice_call_screen")
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.SpaceBetween
    ) {
      // Top Security Status Banner
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
          color = CyberEmeraldDark.copy(alpha = 0.5f),
          shape = RoundedCornerShape(20.dp),
          border = androidx.compose.foundation.BorderStroke(1.dp, CyberEmerald.copy(alpha = 0.6f))
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(
              imageVector = Icons.Default.Lock,
              contentDescription = "Encrypted",
              tint = CyberEmerald,
              modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = "END-TO-END ENCRYPTED VOICE CALL",
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold,
              color = CyberEmerald,
              letterSpacing = 0.8.sp
            )
          }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
          text = "Zero-Interception Guarantee • Hardware KeyStore",
          fontSize = 11.sp,
          color = TextSecondaryDark
        )
      }

      // Middle: Avatar & Calling State
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
      ) {
        Box(
          contentAlignment = Alignment.Center,
          modifier = Modifier.size(160.dp)
        ) {
          // Glowing Pulse Rings
          Box(
            modifier = Modifier
              .size(150.dp)
              .scale(if (callState is CallState.Connected) pulseScale else 1f)
              .clip(CircleShape)
              .background(CyberEmerald.copy(alpha = 0.12f))
          )
          Box(
            modifier = Modifier
              .size(130.dp)
              .clip(CircleShape)
              .background(EncryptionCyan.copy(alpha = 0.15f))
          )

          ContactAvatar(
            name = contactName,
            isOnline = true,
            isVerified = isSafetyVerified,
            size = 100
          )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
          text = contactName,
          fontSize = 26.sp,
          fontWeight = FontWeight.Bold,
          color = TextPrimaryDark
        )

        Spacer(modifier = Modifier.height(6.dp))

        when (callState) {
          is CallState.Dialing -> {
            Text(
              text = "Exchanging Ephemeral Cryptographic Keys...",
              fontSize = 14.sp,
              color = EncryptionCyan
            )
          }
          is CallState.Incoming -> {
            Text(
              text = "Encrypted Call Request...",
              fontSize = 14.sp,
              color = CyberEmerald
            )
          }
          is CallState.Connected -> {
            val minutes = callState.durationSeconds / 60
            val seconds = callState.durationSeconds % 60
            Text(
              text = "%02d:%02d".format(minutes, seconds),
              fontSize = 18.sp,
              fontWeight = FontWeight.SemiBold,
              color = CyberEmerald,
              fontFamily = FontFamily.Monospace
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Waveform Audio Bars
            Row(
              horizontalArrangement = Arrangement.Center,
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier
                .height(36.dp)
                .fillMaxWidth()
            ) {
              callState.audioWaveLevels.forEach { level ->
                val barHeight = (level * 32).coerceIn(6f, 32f).dp
                Box(
                  modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .width(4.dp)
                    .height(barHeight)
                    .clip(RoundedCornerShape(2.dp))
                    .background(CyberEmerald)
                )
              }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Audio Packet Telemetry Card
            Card(
              colors = CardDefaults.cardColors(containerColor = CardSurfaceDark),
              shape = RoundedCornerShape(12.dp),
              border = androidx.compose.foundation.BorderStroke(1.dp, CardSurfaceElevated)
            ) {
              Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
              ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Text(
                    text = "SAS CODE: ",
                    fontSize = 11.sp,
                    color = EncryptionCyan,
                    fontWeight = FontWeight.Bold
                  )
                  Text(
                    text = callState.sasMnemonic,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = TextPrimaryDark
                  )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                  text = "Encrypted Audio Frames: ${callState.encryptedPacketsTransmitted} (AES-256-GCM)",
                  fontSize = 10.sp,
                  color = TextMutedDark,
                  fontFamily = FontFamily.Monospace
                )
              }
            }
          }
          is CallState.Ended -> {
            Text(
              text = "Call Ended • Encrypted History Logged",
              fontSize = 14.sp,
              color = DangerRose
            )
          }
          else -> {}
        }
      }

      // Bottom Call Controls
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
      ) {
        // SAS Verification prompt
        if (callState is CallState.Connected) {
          TextButton(
            onClick = { showSafetyDialog = true },
            modifier = Modifier.testTag("verify_call_button")
          ) {
            Icon(
              imageVector = Icons.Default.Shield,
              contentDescription = null,
              tint = CyberEmerald,
              modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = "Verify Safety Numbers",
              fontSize = 13.sp,
              color = CyberEmerald,
              fontWeight = FontWeight.Medium
            )
          }
          Spacer(modifier = Modifier.height(12.dp))
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceEvenly,
          verticalAlignment = Alignment.CenterVertically
        ) {
          if (callState is CallState.Connected) {
            // Mute Button
            FilledIconButton(
              onClick = onMuteToggle,
              colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = if (callState.isMuted) DangerRose else CardSurfaceElevated,
                contentColor = TextPrimaryDark
              ),
              modifier = Modifier
                .size(56.dp)
                .testTag("mute_call_button")
            ) {
              Icon(
                imageVector = if (callState.isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                contentDescription = "Mute Microphone"
              )
            }

            // End Call Button
            FilledIconButton(
              onClick = onEndCall,
              colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = DangerRose,
                contentColor = Color.White
              ),
              modifier = Modifier
                .size(68.dp)
                .testTag("end_call_button")
            ) {
              Icon(
                imageVector = Icons.Default.CallEnd,
                contentDescription = "End Call",
                modifier = Modifier.size(32.dp)
              )
            }

            // Speakerphone Button
            FilledIconButton(
              onClick = onSpeakerToggle,
              colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = if (callState.isSpeakerOn) CyberEmerald else CardSurfaceElevated,
                contentColor = if (callState.isSpeakerOn) ObsidianBackground else TextPrimaryDark
              ),
              modifier = Modifier
                .size(56.dp)
                .testTag("speaker_call_button")
            ) {
              Icon(
                imageVector = if (callState.isSpeakerOn) Icons.Default.VolumeUp else Icons.Default.VolumeDown,
                contentDescription = "Speakerphone"
              )
            }
          } else {
            // Cancel / End call button for Dialing or Ended
            FilledIconButton(
              onClick = onEndCall,
              colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = DangerRose,
                contentColor = Color.White
              ),
              modifier = Modifier
                .size(68.dp)
                .testTag("end_call_button")
            ) {
              Icon(
                imageVector = Icons.Default.CallEnd,
                contentDescription = "End Call",
                modifier = Modifier.size(32.dp)
              )
            }
          }
        }
      }
    }

    if (showSafetyDialog) {
      SafetyNumberDialog(
        contactName = contactName,
        safetyNumber = safetyNumber,
        sasWords = sasWords,
        fingerprint = fingerprint,
        isVerified = isSafetyVerified,
        onVerificationChanged = { verified ->
          onVerificationToggled(contactId, verified)
        },
        onDismiss = { showSafetyDialog = false }
      )
    }
  }
}
