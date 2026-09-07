package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.crypto.CryptoEngine
import com.example.data.entity.ConversationEntity
import com.example.data.entity.MessageEntity
import com.example.data.model.MessageType
import com.example.data.repository.SecureRepository
import com.example.ui.components.ContactAvatar
import com.example.ui.components.SafetyNumberDialog
import com.example.ui.components.SecurityBadge
import com.example.ui.theme.BorderDark
import com.example.ui.theme.BubbleIncoming
import com.example.ui.theme.BubbleOutgoing
import com.example.ui.theme.CardSurfaceDark
import com.example.ui.theme.CardSurfaceElevated
import com.example.ui.theme.CyberEmerald
import com.example.ui.theme.CyberEmeraldDark
import com.example.ui.theme.DangerRose
import com.example.ui.theme.EncryptionCyan
import com.example.ui.theme.ObsidianBackground
import com.example.ui.theme.SecurityAmber
import com.example.ui.theme.TextMutedDark
import com.example.ui.theme.TextPrimaryDark
import com.example.ui.theme.TextSecondaryDark
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatDetailScreen(
  conversationId: String,
  repository: SecureRepository,
  onBack: () -> Unit,
  onStartVoiceCall: (contactId: String, contactName: String, fingerprint: String) -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val conversation by repository.getConversation(conversationId).collectAsState(initial = null)
  val messages by repository.getMessages(conversationId).collectAsState(initial = emptyList())

  var textInput by remember { mutableStateOf("") }
  var showSafetyDialog by remember { mutableStateOf(false) }
  var showAttachmentDialog by remember { mutableStateOf(false) }
  var isRecordingVoiceNote by remember { mutableStateOf(false) }
  var recordingSeconds by remember { mutableIntStateOf(0) }

  val listState = rememberLazyListState()

  // Scroll to bottom when new message arrives
  LaunchedEffect(messages.size) {
    if (messages.isNotEmpty()) {
      listState.animateScrollToItem(messages.size - 1)
    }
  }

  // Voice note timer simulation
  LaunchedEffect(isRecordingVoiceNote) {
    if (isRecordingVoiceNote) {
      recordingSeconds = 0
      while (isRecordingVoiceNote) {
        delay(1000)
        recordingSeconds += 1
      }
    }
  }

  val contactName = conversation?.contactName ?: "Encrypted Chat"
  val fingerprint = conversation?.e2eeFingerprint ?: "SHA256:E2EE:FINGERPRINT"
  val isVerified = conversation?.isVerified ?: true
  val safetyNumber = conversation?.safetyNumber ?: CryptoEngine.generateSafetyNumber("me", conversationId)
  val sasWords = remember(conversationId) { CryptoEngine.generateSasWords("me", conversationId) }

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(ObsidianBackground)
      .statusBarsPadding()
      .navigationBarsPadding()
      .imePadding()
      .testTag("chat_detail_screen")
  ) {
    // Top Bar
    Surface(
      color = CardSurfaceDark,
      border = androidx.compose.foundation.BorderStroke(1.dp, BorderDark),
      shadowElevation = 4.dp
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        IconButton(
          onClick = onBack,
          modifier = Modifier.testTag("chat_back_button")
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
            tint = TextPrimaryDark
          )
        }

        ContactAvatar(
          name = contactName,
          isOnline = conversation?.isOnline ?: false,
          isVerified = isVerified,
          size = 40
        )

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = contactName,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimaryDark
          )
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Default.Lock,
              contentDescription = null,
              tint = CyberEmerald,
              modifier = Modifier.size(11.dp)
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
              text = if (isVerified) "E2EE AES-256 (Verified)" else "E2EE AES-256",
              fontSize = 11.sp,
              color = if (isVerified) CyberEmerald else SecurityAmber,
              fontWeight = FontWeight.Medium
            )
          }
        }

        // Safety number inspection button
        IconButton(
          onClick = { showSafetyDialog = true },
          modifier = Modifier.testTag("safety_number_button")
        ) {
          Icon(
            imageVector = Icons.Default.Shield,
            contentDescription = "Verify Safety Numbers",
            tint = if (isVerified) CyberEmerald else SecurityAmber
          )
        }

        // Voice Call Action
        IconButton(
          onClick = {
            onStartVoiceCall(conversationId, contactName, fingerprint)
          },
          modifier = Modifier.testTag("start_voice_call_button")
        ) {
          Icon(
            imageVector = Icons.Default.Phone,
            contentDescription = "Start Encrypted Voice Call",
            tint = CyberEmerald
          )
        }
      }
    }

    // Encryption Banner Notice
    Card(
      colors = CardDefaults.cardColors(containerColor = CardSurfaceElevated.copy(alpha = 0.6f)),
      shape = RoundedCornerShape(0.dp),
      border = androidx.compose.foundation.BorderStroke(0.5.dp, BorderDark),
      modifier = Modifier.fillMaxWidth()
    ) {
      Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
      ) {
        Icon(
          imageVector = Icons.Default.Lock,
          contentDescription = null,
          tint = CyberEmerald,
          modifier = Modifier.size(12.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
          text = "End-to-End Encrypted • Hardware KeyStore Master Key",
          fontSize = 10.sp,
          color = TextSecondaryDark,
          fontFamily = FontFamily.Monospace
        )
      }
    }

    // Message History List
    LazyColumn(
      state = listState,
      modifier = Modifier
        .weight(1f)
        .fillMaxWidth()
        .padding(horizontal = 12.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      item {
        Spacer(modifier = Modifier.height(8.dp))
        // Center encryption handshake indicator
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          Surface(
            color = ObsidianBackground,
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderDark)
          ) {
            Text(
              text = "🔒 Cryptographic session authenticated with $contactName\nSafety Number: ${safetyNumber.take(17)}...",
              fontSize = 11.sp,
              color = TextSecondaryDark,
              textAlign = TextAlign.Center,
              modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
              lineHeight = 15.sp
            )
          }
        }
      }

      items(messages, key = { it.id }) { msg ->
        ChatMessageBubble(message = msg)
      }

      item {
        Spacer(modifier = Modifier.height(8.dp))
      }
    }

    // Bottom Voice Recording Bar or Standard Input
    if (isRecordingVoiceNote) {
      Surface(
        color = CardSurfaceDark,
        border = androidx.compose.foundation.BorderStroke(1.dp, DangerRose.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth()
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
              modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(DangerRose)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "Recording Encrypted Voice Note: 00:%02d".format(recordingSeconds),
              fontSize = 13.sp,
              color = DangerRose,
              fontWeight = FontWeight.Bold
            )
          }

          Row {
            TextButton(
              onClick = { isRecordingVoiceNote = false },
              modifier = Modifier.testTag("cancel_voice_note")
            ) {
              Text("Cancel", color = TextSecondaryDark)
            }

            Spacer(modifier = Modifier.width(8.dp))

            FloatingActionButton(
              onClick = {
                val duration = recordingSeconds.coerceAtLeast(1)
                isRecordingVoiceNote = false
                scope.launch {
                  repository.sendEncryptedMediaMessage(
                    conversationId = conversationId,
                    type = MessageType.AUDIO_VOICE_NOTE,
                    mediaUriOrData = "cipher_audio_sample_${System.currentTimeMillis()}",
                    durationSeconds = duration
                  )
                }
              },
              containerColor = CyberEmerald,
              contentColor = ObsidianBackground,
              modifier = Modifier
                .size(44.dp)
                .testTag("send_voice_note")
            ) {
              Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send Voice Note")
            }
          }
        }
      }
    } else {
      // Standard Input Bar
      Surface(
        color = CardSurfaceDark,
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderDark),
        modifier = Modifier.fillMaxWidth()
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          // Attachment Button (Images / Videos)
          IconButton(
            onClick = { showAttachmentDialog = true },
            modifier = Modifier.testTag("chat_attach_button")
          ) {
            Icon(
              imageVector = Icons.Default.AttachFile,
              contentDescription = "Attach Media",
              tint = EncryptionCyan
            )
          }

          // Text Field
          OutlinedTextField(
            value = textInput,
            onValueChange = { textInput = it },
            placeholder = { Text("Encrypted message...", color = TextMutedDark, fontSize = 14.sp) },
            modifier = Modifier
              .weight(1f)
              .testTag("message_input_field"),
            shape = RoundedCornerShape(24.dp),
            colors = OutlinedTextFieldDefaults.colors(
              focusedContainerColor = CardSurfaceElevated,
              unfocusedContainerColor = CardSurfaceElevated,
              focusedBorderColor = CyberEmerald,
              unfocusedBorderColor = BorderDark,
              focusedTextColor = TextPrimaryDark,
              unfocusedTextColor = TextPrimaryDark
            ),
            maxLines = 4
          )

          Spacer(modifier = Modifier.width(6.dp))

          if (textInput.isNotBlank()) {
            // Send Text Button
            FloatingActionButton(
              onClick = {
                val msgToSend = textInput.trim()
                textInput = ""
                scope.launch {
                  repository.sendEncryptedTextMessage(conversationId, msgToSend)
                }
              },
              containerColor = CyberEmerald,
              contentColor = ObsidianBackground,
              modifier = Modifier
                .size(44.dp)
                .testTag("send_message_button")
            ) {
              Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send Message")
            }
          } else {
            // Record Audio Button
            IconButton(
              onClick = { isRecordingVoiceNote = true },
              modifier = Modifier.testTag("record_audio_button")
            ) {
              Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = "Record Voice Note",
                tint = CyberEmerald
              )
            }
          }
        }
      }
    }

    // Safety Number Dialog
    if (showSafetyDialog) {
      SafetyNumberDialog(
        contactName = contactName,
        safetyNumber = safetyNumber,
        sasWords = sasWords,
        fingerprint = fingerprint,
        isVerified = isVerified,
        onVerificationChanged = { verified ->
          scope.launch {
            repository.setSafetyVerification(conversationId, verified)
          }
        },
        onDismiss = { showSafetyDialog = false }
      )
    }

    // Attachment Chooser Dialog
    if (showAttachmentDialog) {
      AlertDialog(
        onDismissRequest = { showAttachmentDialog = false },
        containerColor = CardSurfaceDark,
        title = {
          Text(
            text = "Send End-to-End Encrypted Media",
            color = TextPrimaryDark,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
          )
        },
        text = {
          Column {
            Text(
              text = "Media files are encrypted on your device with AES-256 before transmission.",
              fontSize = 12.sp,
              color = TextSecondaryDark
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Photo attachment button
            Card(
              colors = CardDefaults.cardColors(containerColor = CardSurfaceElevated),
              shape = RoundedCornerShape(10.dp),
              modifier = Modifier
                .fillMaxWidth()
                .clickable {
                  showAttachmentDialog = false
                  scope.launch {
                    repository.sendEncryptedMediaMessage(
                      conversationId = conversationId,
                      type = MessageType.IMAGE,
                      mediaUriOrData = "sample_encrypted_photo_payload",
                      caption = "Encrypted Photographic Evidence"
                    )
                  }
                }
            ) {
              Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Icon(Icons.Default.Image, contentDescription = null, tint = EncryptionCyan)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                  Text("Encrypted Photo", color = TextPrimaryDark, fontWeight = FontWeight.SemiBold)
                  Text("Client-side AES-256 encrypted", color = TextMutedDark, fontSize = 11.sp)
                }
              }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Video attachment button
            Card(
              colors = CardDefaults.cardColors(containerColor = CardSurfaceElevated),
              shape = RoundedCornerShape(10.dp),
              modifier = Modifier
                .fillMaxWidth()
                .clickable {
                  showAttachmentDialog = false
                  scope.launch {
                    repository.sendEncryptedMediaMessage(
                      conversationId = conversationId,
                      type = MessageType.VIDEO,
                      mediaUriOrData = "sample_encrypted_video_stream",
                      durationSeconds = 42,
                      caption = "Encrypted High-Definition Video"
                    )
                  }
                }
            ) {
              Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Icon(Icons.Default.Videocam, contentDescription = null, tint = CyberEmerald)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                  Text("Encrypted Video", color = TextPrimaryDark, fontWeight = FontWeight.SemiBold)
                  Text("Stream sealed with Hardware Key", color = TextMutedDark, fontSize = 11.sp)
                }
              }
            }
          }
        },
        confirmButton = {
          TextButton(onClick = { showAttachmentDialog = false }) {
            Text("Cancel", color = TextSecondaryDark)
          }
        }
      )
    }
  }
}

@Composable
fun ChatMessageBubble(
  message: MessageEntity,
  modifier: Modifier = Modifier
) {
  val isOutgoing = message.isOutgoing
  val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
  val formattedTime = remember(message.timestamp) { timeFormat.format(Date(message.timestamp)) }

  // Decrypt content on-the-fly using CryptoEngine
  val decryptedText = remember(message.cipherText, message.iv) {
    CryptoEngine.decryptString(message.cipherText, message.iv)
  }

  val alignment = if (isOutgoing) Alignment.End else Alignment.Start
  val bubbleColor = if (isOutgoing) BubbleOutgoing else BubbleIncoming

  Column(
    modifier = modifier.fillMaxWidth(),
    horizontalAlignment = alignment
  ) {
    Card(
      colors = CardDefaults.cardColors(containerColor = bubbleColor),
      shape = RoundedCornerShape(
        topStart = 16.dp,
        topEnd = 16.dp,
        bottomStart = if (isOutgoing) 16.dp else 4.dp,
        bottomEnd = if (isOutgoing) 4.dp else 16.dp
      ),
      border = androidx.compose.foundation.BorderStroke(
        1.dp,
        if (isOutgoing) CyberEmerald.copy(alpha = 0.4f) else BorderDark
      ),
      modifier = Modifier
        .widthIn(max = 300.dp)
        .testTag("chat_bubble_${message.id}")
    ) {
      Column(modifier = Modifier.padding(10.dp)) {
        when (message.messageType) {
          MessageType.TEXT.name -> {
            Text(
              text = decryptedText,
              color = TextPrimaryDark,
              fontSize = 14.sp,
              lineHeight = 20.sp
            )
          }
          MessageType.IMAGE.name -> {
            Column {
              Box(
                modifier = Modifier
                  .fillMaxWidth()
                  .height(140.dp)
                  .clip(RoundedCornerShape(8.dp))
                  .background(
                    Brush.linearGradient(
                      listOf(Color(0xFF0F2A38), Color(0xFF063A33))
                    )
                  ),
                contentAlignment = Alignment.Center
              ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                  Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = CyberEmerald,
                    modifier = Modifier.size(28.dp)
                  )
                  Spacer(modifier = Modifier.height(4.dp))
                  Text(
                    text = "Encrypted Photo Sealed",
                    fontSize = 11.sp,
                    color = CyberEmerald,
                    fontWeight = FontWeight.Bold
                  )
                  Text(
                    text = "AES-256-GCM Hardware Cipher",
                    fontSize = 9.sp,
                    color = TextMutedDark,
                    fontFamily = FontFamily.Monospace
                  )
                }
              }
              Spacer(modifier = Modifier.height(6.dp))
              Text(
                text = decryptedText,
                color = TextPrimaryDark,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
              )
            }
          }
          MessageType.VIDEO.name -> {
            Column {
              Box(
                modifier = Modifier
                  .fillMaxWidth()
                  .height(130.dp)
                  .clip(RoundedCornerShape(8.dp))
                  .background(
                    Brush.linearGradient(
                      listOf(Color(0xFF131D33), Color(0xFF072728))
                    )
                  ),
                contentAlignment = Alignment.Center
              ) {
                Box(
                  modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(CyberEmeraldDark),
                  contentAlignment = Alignment.Center
                ) {
                  Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = CyberEmerald,
                    modifier = Modifier.size(28.dp)
                  )
                }
              }
              Spacer(modifier = Modifier.height(6.dp))
              Text(
                text = decryptedText,
                color = TextPrimaryDark,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
              )
            }
          }
          MessageType.AUDIO_VOICE_NOTE.name -> {
            var isPlaying by remember { mutableStateOf(false) }
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.padding(vertical = 4.dp)
            ) {
              Box(
                modifier = Modifier
                  .size(36.dp)
                  .clip(CircleShape)
                  .background(CyberEmerald)
                  .clickable { isPlaying = !isPlaying },
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                  contentDescription = "Play Audio",
                  tint = ObsidianBackground,
                  modifier = Modifier.size(20.dp)
                )
              }

              Spacer(modifier = Modifier.width(10.dp))

              Column(modifier = Modifier.weight(1f)) {
                // Waveform graphic
                Row(
                  horizontalArrangement = Arrangement.spacedBy(2.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  listOf(8, 16, 22, 14, 26, 18, 12, 20, 24, 14, 18, 10).forEach { barHeight ->
                    Box(
                      modifier = Modifier
                        .width(3.dp)
                        .height(barHeight.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(if (isPlaying) CyberEmerald else EncryptionCyan.copy(alpha = 0.7f))
                    )
                  }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                  text = "Voice Memo (${message.mediaDurationSeconds}s) • Encrypted",
                  fontSize = 10.sp,
                  color = TextMutedDark,
                  fontFamily = FontFamily.Monospace
                )
              }
            }
          }
          MessageType.CALL_EVENT.name -> {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.padding(vertical = 2.dp)
            ) {
              Icon(
                imageVector = Icons.Default.Phone,
                contentDescription = null,
                tint = CyberEmerald,
                modifier = Modifier.size(16.dp)
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = decryptedText,
                color = TextPrimaryDark,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Message footer with timestamp and lock icon
        Row(
          modifier = Modifier.align(Alignment.End),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = "Encrypted",
            tint = CyberEmerald.copy(alpha = 0.8f),
            modifier = Modifier.size(10.dp)
          )
          Spacer(modifier = Modifier.width(3.dp))
          Text(
            text = formattedTime,
            fontSize = 10.sp,
            color = TextMutedDark
          )
          if (isOutgoing) {
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
              imageVector = Icons.Default.DoneAll,
              contentDescription = "Delivered",
              tint = CyberEmerald,
              modifier = Modifier.size(12.dp)
            )
          }
        }
      }
    }
  }
}
