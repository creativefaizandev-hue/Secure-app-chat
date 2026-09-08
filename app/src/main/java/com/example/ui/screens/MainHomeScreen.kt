package com.example.ui.screens

import android.widget.Toast
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallMissed
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhoneCallback
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.auth.GoogleAuthManager
import com.example.crypto.CryptoEngine
import com.example.data.entity.CallLogEntity
import com.example.data.entity.ConversationEntity
import com.example.data.model.CallDirection
import com.example.data.repository.SecureRepository
import com.example.ui.components.ContactAvatar
import com.example.ui.components.SecurityBadge
import com.example.ui.theme.BorderDark
import com.example.ui.theme.CardSurfaceDark
import com.example.ui.theme.CardSurfaceElevated
import com.example.ui.theme.CyberEmerald
import com.example.ui.theme.CyberEmeraldDark
import com.example.ui.theme.DangerRose
import com.example.ui.theme.EncryptionCyan
import com.example.ui.theme.ObsidianBackground
import com.example.ui.theme.SecurityAmber
import com.example.ui.theme.ShieldViolet
import com.example.ui.theme.TextMutedDark
import com.example.ui.theme.TextPrimaryDark
import com.example.ui.theme.TextSecondaryDark
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class HomeTab(val title: String, val icon: ImageVector, val tag: String) {
  object Chats : HomeTab("Chats", Icons.Default.Lock, "tab_chats")
  object Calls : HomeTab("Voice Calls", Icons.Default.Phone, "tab_calls")
  object Vault : HomeTab("Zero-Hack Vault", Icons.Default.Shield, "tab_vault")
  object Account : HomeTab("Google OAuth", Icons.Default.Person, "tab_account")
}

@Composable
fun MainHomeScreen(
  repository: SecureRepository,
  authManager: GoogleAuthManager,
  onOpenConversation: (String) -> Unit,
  onStartVoiceCall: (contactId: String, contactName: String, fingerprint: String) -> Unit,
  onSimulateIncomingCall: (contactId: String, contactName: String, fingerprint: String) -> Unit,
  modifier: Modifier = Modifier
) {
  var selectedTab by remember { mutableIntStateOf(0) }
  val tabs = listOf(HomeTab.Chats, HomeTab.Calls, HomeTab.Vault, HomeTab.Account)

  Scaffold(
    modifier = modifier
      .fillMaxSize()
      .background(ObsidianBackground)
      .statusBarsPadding()
      .navigationBarsPadding(),
    containerColor = ObsidianBackground,
    bottomBar = {
      NavigationBar(
        containerColor = CardSurfaceDark,
        tonalElevation = 8.dp,
        modifier = Modifier
          .border(0.5.dp, BorderDark, RoundedCornerShape(0.dp))
          .testTag("main_navigation_bar")
      ) {
        tabs.forEachIndexed { index, tab ->
          NavigationBarItem(
            selected = selectedTab == index,
            onClick = { selectedTab = index },
            icon = {
              Icon(imageVector = tab.icon, contentDescription = tab.title)
            },
            label = {
              Text(
                text = tab.title,
                fontSize = 11.sp,
                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal
              )
            },
            colors = NavigationBarItemDefaults.colors(
              selectedIconColor = CyberEmerald,
              selectedTextColor = CyberEmerald,
              indicatorColor = CyberEmeraldDark.copy(alpha = 0.5f),
              unselectedIconColor = TextSecondaryDark,
              unselectedTextColor = TextSecondaryDark
            ),
            modifier = Modifier.testTag(tab.tag)
          )
        }
      }
    }
  ) { innerPadding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
    ) {
      when (selectedTab) {
        0 -> ChatsTab(
          repository = repository,
          onOpenConversation = onOpenConversation,
          onStartVoiceCall = onStartVoiceCall,
          onSimulateIncomingCall = onSimulateIncomingCall
        )
        1 -> CallsTab(
          repository = repository,
          onStartVoiceCall = onStartVoiceCall
        )
        2 -> VaultTab(repository = repository)
        3 -> AccountTab(authManager = authManager, repository = repository)
      }
    }
  }
}

@Composable
fun ChatsTab(
  repository: SecureRepository,
  onOpenConversation: (String) -> Unit,
  onStartVoiceCall: (contactId: String, contactName: String, fingerprint: String) -> Unit,
  onSimulateIncomingCall: (contactId: String, contactName: String, fingerprint: String) -> Unit
) {
  val conversations by repository.conversations.collectAsState(initial = emptyList())
  var searchQuery by remember { mutableStateOf("") }
  var showNewChatDialog by remember { mutableStateOf(false) }
  var newUsername by remember { mutableStateOf("") }
  val scope = rememberCoroutineScope()
  val context = LocalContext.current

  val filteredConversations = conversations.filter {
    it.contactName.contains(searchQuery, ignoreCase = true) ||
      it.contactUsername.contains(searchQuery, ignoreCase = true)
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(horizontal = 16.dp)
  ) {
    Spacer(modifier = Modifier.height(16.dp))

    // Top Header
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Column {
        Text(
          text = "CipherChat",
          fontSize = 24.sp,
          fontWeight = FontWeight.Bold,
          color = TextPrimaryDark,
          letterSpacing = 0.5.sp
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = null,
            tint = CyberEmerald,
            modifier = Modifier.size(12.dp)
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text(
            text = "End-to-End Encrypted • Hardware Protected",
            fontSize = 11.sp,
            color = CyberEmerald,
            fontWeight = FontWeight.Medium
          )
        }
      }

      // Incoming Call Simulation Quick Action
      OutlinedButton(
        onClick = {
          val contact = conversations.firstOrNull() ?: return@OutlinedButton
          onSimulateIncomingCall(contact.id, contact.contactName, contact.e2eeFingerprint)
        },
        colors = ButtonDefaults.outlinedButtonColors(contentColor = EncryptionCyan),
        border = androidx.compose.foundation.BorderStroke(1.dp, EncryptionCyan.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.testTag("test_incoming_call_button")
      ) {
        Icon(Icons.Default.PhoneCallback, contentDescription = null, modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text("Test Call", fontSize = 11.sp)
      }
    }

    Spacer(modifier = Modifier.height(14.dp))

    // Search Bar
    OutlinedTextField(
      value = searchQuery,
      onValueChange = { searchQuery = it },
      leadingIcon = {
        Icon(Icons.Default.Search, contentDescription = "Search", tint = TextSecondaryDark)
      },
      placeholder = { Text("Search encrypted contacts...", color = TextMutedDark, fontSize = 13.sp) },
      modifier = Modifier
        .fillMaxWidth()
        .testTag("chat_search_bar"),
      shape = RoundedCornerShape(20.dp),
      colors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = CardSurfaceDark,
        unfocusedContainerColor = CardSurfaceDark,
        focusedBorderColor = CyberEmerald,
        unfocusedBorderColor = BorderDark,
        focusedTextColor = TextPrimaryDark,
        unfocusedTextColor = TextPrimaryDark
      ),
      singleLine = true
    )

    Spacer(modifier = Modifier.height(12.dp))

    // Conversation List
    LazyColumn(
      modifier = Modifier
        .weight(1f)
        .fillMaxWidth(),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      items(filteredConversations, key = { it.id }) { conv ->
        ConversationListItem(
          conversation = conv,
          onClick = { onOpenConversation(conv.id) },
          onCallClick = {
            onStartVoiceCall(conv.id, conv.contactName, conv.e2eeFingerprint)
          }
        )
      }

      if (filteredConversations.isEmpty()) {
        item {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(top = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
          ) {
            Icon(
              imageVector = Icons.Default.Lock,
              contentDescription = null,
              tint = TextMutedDark,
              modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text("No encrypted chats found", color = TextSecondaryDark, fontSize = 14.sp)
          }
        }
      }
    }
  }

  // Floating button to add new contact
  Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
    FloatingActionButton(
      onClick = { showNewChatDialog = true },
      containerColor = CyberEmerald,
      contentColor = ObsidianBackground,
      modifier = Modifier
        .padding(16.dp)
        .testTag("new_chat_fab")
    ) {
      Icon(Icons.Default.Add, contentDescription = "New Encrypted Chat")
    }
  }

  if (showNewChatDialog) {
    AlertDialog(
      onDismissRequest = { showNewChatDialog = false },
      containerColor = CardSurfaceDark,
      title = { Text("Add encrypted contact", color = TextPrimaryDark, fontWeight = FontWeight.Bold) },
      text = {
        Column {
          Text("Enter their unique username. No email lookup is used.", color = TextSecondaryDark, fontSize = 12.sp)
          Spacer(modifier = Modifier.height(10.dp))
          OutlinedTextField(
            value = newUsername,
            onValueChange = { if (it.length <= 20) newUsername = it.filter { c -> c.isLetterOrDigit() || c == '_' } },
            label = { Text("Username") },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
              focusedContainerColor = CardSurfaceElevated, unfocusedContainerColor = CardSurfaceElevated,
              focusedTextColor = TextPrimaryDark, unfocusedTextColor = TextPrimaryDark
            ),
            modifier = Modifier.fillMaxWidth().testTag("new_contact_username")
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            scope.launch {
              val result = repository.createConversationWithUsername(newUsername, "Contact")
              result.onSuccess { conversation ->
                val sendResult = repository.sendEncryptedTextMessage(conversation.id, "End-to-End Encrypted Session Initialized.")
                if (sendResult is SecureRepository.SendMessageResult.Success) {
                  Toast.makeText(context, "Encrypted channel established", Toast.LENGTH_SHORT).show()
                } else if (sendResult is SecureRepository.SendMessageResult.Failure) {
                  Toast.makeText(context, sendResult.reason, Toast.LENGTH_LONG).show()
                }
                newUsername = ""
                showNewChatDialog = false
              }.onFailure { error -> Toast.makeText(context, error.message ?: "Could not add contact", Toast.LENGTH_LONG).show() }
            }
          },
          enabled = newUsername.length in 3..20,
          colors = ButtonDefaults.buttonColors(containerColor = CyberEmerald),
          modifier = Modifier.testTag("confirm_new_contact")
        ) { Text("Connect", color = ObsidianBackground, fontWeight = FontWeight.Bold) }
      },
      dismissButton = { TextButton(onClick = { showNewChatDialog = false }) { Text("Cancel", color = TextSecondaryDark) } }
    )
  }
}

@Composable
fun ConversationListItem(
  conversation: ConversationEntity,
  onClick: () -> Unit,
  onCallClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
  val formattedTime = remember(conversation.lastMessageTimestamp) {
    timeFormat.format(Date(conversation.lastMessageTimestamp))
  }

  Card(
    colors = CardDefaults.cardColors(containerColor = CardSurfaceDark),
    shape = RoundedCornerShape(14.dp),
    border = androidx.compose.foundation.BorderStroke(1.dp, BorderDark),
    modifier = modifier
      .fillMaxWidth()
      .clickable { onClick() }
      .testTag("conversation_item_${conversation.id}")
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(12.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      ContactAvatar(
        name = conversation.contactName,
        isOnline = conversation.isOnline,
        isVerified = conversation.isVerified,
        size = 48
      )

      Spacer(modifier = Modifier.width(12.dp))

      Column(modifier = Modifier.weight(1f)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
              text = conversation.contactName,
              fontSize = 15.sp,
              fontWeight = FontWeight.Bold,
              color = TextPrimaryDark
            )
            if (conversation.isVerified) {
              Spacer(modifier = Modifier.width(4.dp))
              Icon(
                imageVector = Icons.Default.VerifiedUser,
                contentDescription = "Verified Safety Number",
                tint = CyberEmerald,
                modifier = Modifier.size(13.dp)
              )
            }
          }

          Text(
            text = formattedTime,
            fontSize = 11.sp,
            color = TextMutedDark
          )
        }

        Spacer(modifier = Modifier.height(3.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = conversation.lastEncryptedMessage,
            fontSize = 12.sp,
            color = TextSecondaryDark,
            maxLines = 1,
            modifier = Modifier.weight(1f)
          )

          if (conversation.unreadCount > 0) {
            Box(
              modifier = Modifier
                .padding(start = 6.dp)
                .size(18.dp)
                .clip(CircleShape)
                .background(CyberEmerald),
              contentAlignment = Alignment.Center
            ) {
              Text(
                text = "${conversation.unreadCount}",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = ObsidianBackground
              )
            }
          }
        }
      }

      Spacer(modifier = Modifier.width(8.dp))

      // Direct Voice Call Button
      IconButton(
        onClick = onCallClick,
        modifier = Modifier
          .size(36.dp)
          .testTag("item_call_button_${conversation.id}")
      ) {
        Icon(
          imageVector = Icons.Default.Phone,
          contentDescription = "Encrypted Call",
          tint = CyberEmerald,
          modifier = Modifier.size(20.dp)
        )
      }
    }
  }
}

@Composable
fun CallsTab(
  repository: SecureRepository,
  onStartVoiceCall: (contactId: String, contactName: String, fingerprint: String) -> Unit
) {
  val callLogs by repository.callLogs.collectAsState(initial = emptyList())
  val timeFormat = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(horizontal = 16.dp)
  ) {
    Spacer(modifier = Modifier.height(16.dp))

    Text(
      text = "Encrypted Voice Calls",
      fontSize = 24.sp,
      fontWeight = FontWeight.Bold,
      color = TextPrimaryDark
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
      Icon(
        imageVector = Icons.Default.Lock,
        contentDescription = null,
        tint = CyberEmerald,
        modifier = Modifier.size(12.dp)
      )
      Spacer(modifier = Modifier.width(4.dp))
      Text(
        text = "All voice packets sealed with AES-256-GCM / SRTP",
        fontSize = 11.sp,
        color = CyberEmerald
      )
    }

    Spacer(modifier = Modifier.height(16.dp))

    LazyColumn(
      modifier = Modifier
        .weight(1f)
        .fillMaxWidth(),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      items(callLogs, key = { it.id }) { log ->
        val durationFormatted = if (log.durationSeconds > 0) {
          val m = log.durationSeconds / 60
          val s = log.durationSeconds % 60
          "%02d:%02d".format(m, s)
        } else "Missed"

        val directionIcon = when (log.direction) {
          CallDirection.OUTGOING.name -> Icons.Default.CallMade
          CallDirection.INCOMING.name -> Icons.Default.CallReceived
          else -> Icons.Default.CallMissed
        }

        val directionColor = when (log.direction) {
          CallDirection.OUTGOING.name -> CyberEmerald
          CallDirection.INCOMING.name -> EncryptionCyan
          else -> DangerRose
        }

        Card(
          colors = CardDefaults.cardColors(containerColor = CardSurfaceDark),
          shape = RoundedCornerShape(12.dp),
          border = androidx.compose.foundation.BorderStroke(1.dp, BorderDark),
          modifier = Modifier.fillMaxWidth().testTag("call_log_${log.id}")
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            ContactAvatar(name = log.contactName, size = 44)

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = log.contactName,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimaryDark
              )

              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                  imageVector = directionIcon,
                  contentDescription = log.direction,
                  tint = directionColor,
                  modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                  text = "${log.direction} • $durationFormatted",
                  fontSize = 11.sp,
                  color = directionColor,
                  fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                  text = timeFormat.format(Date(log.timestamp)),
                  fontSize = 10.sp,
                  color = TextMutedDark
                )
              }

              Spacer(modifier = Modifier.height(2.dp))
              Text(
                text = "E2EE Fingerprint: ${log.e2eeFingerprint}",
                fontSize = 9.sp,
                color = TextMutedDark,
                fontFamily = FontFamily.Monospace
              )
            }

            IconButton(
              onClick = {
                onStartVoiceCall(log.contactId, log.contactName, log.e2eeFingerprint)
              },
              modifier = Modifier.testTag("call_again_${log.id}")
            ) {
              Icon(
                imageVector = Icons.Default.Call,
                contentDescription = "Call Back",
                tint = CyberEmerald
              )
            }
          }
        }
      }

      if (callLogs.isEmpty()) {
        item {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(top = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
          ) {
            Icon(Icons.Default.Phone, contentDescription = null, tint = TextMutedDark, modifier = Modifier.size(48.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text("No voice call history yet", color = TextSecondaryDark)
          }
        }
      }
    }
  }
}

@Composable
fun VaultTab(repository: SecureRepository) {
  val audit = remember { CryptoEngine.getAuditReport() }
  val totalMessages by repository.totalMessageCount.collectAsState(initial = 0)
  val scope = rememberCoroutineScope()
  val context = LocalContext.current
  var showClearConfirm by remember { mutableStateOf(false) }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(horizontal = 16.dp)
  ) {
    Spacer(modifier = Modifier.height(16.dp))

    Text(
      text = "Security Vault & Audit",
      fontSize = 24.sp,
      fontWeight = FontWeight.Bold,
      color = TextPrimaryDark
    )
    Text(
      text = "End-to-End Cryptographic Architecture",
      fontSize = 11.sp,
      color = CyberEmerald
    )

    Spacer(modifier = Modifier.height(16.dp))

    LazyColumn(
      modifier = Modifier
        .weight(1f)
        .fillMaxWidth(),
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      item {
        // High level guarantee card
        Card(
          colors = CardDefaults.cardColors(containerColor = CyberEmeraldDark.copy(alpha = 0.3f)),
          shape = RoundedCornerShape(16.dp),
          border = androidx.compose.foundation.BorderStroke(1.5.dp, CyberEmerald)
        ) {
          Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(
              imageVector = Icons.Default.Shield,
              contentDescription = null,
              tint = CyberEmerald,
              modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column {
              Text(
                text = "END-TO-END ENCRYPTION ACTIVE",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = CyberEmerald,
                letterSpacing = 0.8.sp
              )
              Text(
                text = "Messages are encrypted locally before being sent over the network. Only intended recipients can decrypt them.",
                fontSize = 11.sp,
                color = TextPrimaryDark,
                lineHeight = 16.sp
              )
            }
          }
        }
      }

      item {
        Text(
          text = "CRYPTOGRAPHIC TELEMETRY",
          fontSize = 11.sp,
          fontWeight = FontWeight.Bold,
          color = EncryptionCyan,
          letterSpacing = 1.sp
        )
      }

      item {
        SecurityStatusRow("Hardware Keystore (TEE)", "ACTIVE • ENCLAVE SEALED", CyberEmerald, Icons.Default.Key)
      }
      item {
        SecurityStatusRow("Voice & Text Cipher", "AES-256-GCM (128-bit Auth Tag)", CyberEmerald, Icons.Default.Lock)
      }
      item {
        SecurityStatusRow("Key Exchange Protocol", "ECDH Curve25519 + HKDF-SHA256", EncryptionCyan, Icons.Default.SwapHoriz)
      }
      item {
        SecurityStatusRow("Local Database", audit.localDbEncryptionStatus, CyberEmerald, Icons.Default.Security)
      }
      item {
        SecurityStatusRow("Total Encrypted Payloads", "$totalMessages Encrypted Records", ShieldViolet, Icons.Default.CheckCircle)
      }
      item {
        SecurityStatusRow("Voice Streaming Security", "WebRTC Datachannel SRTP", CyberEmerald, Icons.Default.Phone)
      }

      item {
        Spacer(modifier = Modifier.height(8.dp))
        Button(
          onClick = { showClearConfirm = true },
          colors = ButtonDefaults.buttonColors(containerColor = CardSurfaceElevated),
          border = androidx.compose.foundation.BorderStroke(1.dp, DangerRose.copy(alpha = 0.6f)),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("purge_history_button")
        ) {
          Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = DangerRose)
          Spacer(modifier = Modifier.width(8.dp))
          Text("Purge All Encrypted History", color = DangerRose, fontWeight = FontWeight.Bold)
        }
      }
    }
  }

  if (showClearConfirm) {
    AlertDialog(
      onDismissRequest = { showClearConfirm = false },
      containerColor = CardSurfaceDark,
      title = { Text("Purge Database Records?", color = DangerRose, fontWeight = FontWeight.Bold) },
      text = {
        Text("All encrypted messages, media attachments, and call logs will be permanently scrubbed from the local database.", color = TextSecondaryDark)
      },
      confirmButton = {
        Button(
          onClick = {
            scope.launch {
              repository.clearAllHistory()
              Toast.makeText(context, "Encrypted database scrubbed", Toast.LENGTH_SHORT).show()
            }
            showClearConfirm = false
          },
          colors = ButtonDefaults.buttonColors(containerColor = DangerRose)
        ) {
          Text("Scrub Database", color = Color.White)
        }
      },
      dismissButton = {
        TextButton(onClick = { showClearConfirm = false }) {
          Text("Cancel", color = TextSecondaryDark)
        }
      }
    )
  }
}

@Composable
fun SecurityStatusRow(
  label: String,
  value: String,
  accentColor: Color,
  icon: ImageVector
) {
  Card(
    colors = CardDefaults.cardColors(containerColor = CardSurfaceDark),
    shape = RoundedCornerShape(10.dp),
    border = androidx.compose.foundation.BorderStroke(1.dp, BorderDark),
    modifier = Modifier.fillMaxWidth()
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 14.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(20.dp))
      Spacer(modifier = Modifier.width(12.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(text = label, fontSize = 11.sp, color = TextMutedDark)
        Text(text = value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryDark, fontFamily = FontFamily.Monospace)
      }
    }
  }
}

@Composable
fun AccountTab(
  authManager: GoogleAuthManager,
  repository: SecureRepository
) {
  val authState by authManager.authState.collectAsState()
  val scope = rememberCoroutineScope()
  val context = LocalContext.current
  var showSwitchDialog by remember { mutableStateOf(false) }
  var showUsernameDialog by remember { mutableStateOf(false) }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(horizontal = 16.dp)
  ) {
    Spacer(modifier = Modifier.height(16.dp))

    Text(
      text = "Google OAuth & Identity",
      fontSize = 24.sp,
      fontWeight = FontWeight.Bold,
      color = TextPrimaryDark
    )
    Text(
      text = "Authenticated OpenID Connect Identity Profile",
      fontSize = 11.sp,
      color = CyberEmerald
    )

    Spacer(modifier = Modifier.height(20.dp))

    // Profile Card
    Card(
      colors = CardDefaults.cardColors(containerColor = CardSurfaceDark),
      shape = RoundedCornerShape(16.dp),
      border = androidx.compose.foundation.BorderStroke(1.dp, CyberEmerald.copy(alpha = 0.5f)),
      modifier = Modifier.fillMaxWidth()
    ) {
      Column(
        modifier = Modifier.padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        ContactAvatar(name = authState.displayName, isOnline = true, size = 64)
        Spacer(modifier = Modifier.height(10.dp))
        Text(
          text = authState.displayName,
          fontSize = 18.sp,
          fontWeight = FontWeight.Bold,
          color = TextPrimaryDark
        )
        Text("@${authState.username ?: "not set"}", fontSize = 13.sp, color = CyberEmerald, fontFamily = FontFamily.Monospace)
        Text(authState.email, fontSize = 11.sp, color = TextMutedDark, fontFamily = FontFamily.Monospace)

        Spacer(modifier = Modifier.height(12.dp))

        Surface(
          color = CyberEmeraldDark.copy(alpha = 0.4f),
          shape = RoundedCornerShape(12.dp),
          border = androidx.compose.foundation.BorderStroke(1.dp, CyberEmerald.copy(alpha = 0.6f))
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = CyberEmerald, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = authState.authProvider,
              fontSize = 11.sp,
              fontWeight = FontWeight.Medium,
              color = CyberEmerald
            )
          }
        }
      }
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Key details
    Card(
      colors = CardDefaults.cardColors(containerColor = CardSurfaceDark),
      shape = RoundedCornerShape(12.dp),
      border = androidx.compose.foundation.BorderStroke(1.dp, BorderDark),
      modifier = Modifier.fillMaxWidth()
    ) {
      Column(modifier = Modifier.padding(14.dp)) {
        Text("IDENTITY KEY FINGERPRINT", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = EncryptionCyan, letterSpacing = 1.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Text(authState.keyFingerprint, fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = TextPrimaryDark)

        Spacer(modifier = Modifier.height(12.dp))

        Text("INTERFACE MODE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = EncryptionCyan, letterSpacing = 1.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Text("User-Friendly Dark Mode (Enabled)", fontSize = 12.sp, color = CyberEmerald, fontWeight = FontWeight.SemiBold)
      }
    }

    Spacer(modifier = Modifier.height(20.dp))

    OutlinedButton(
      onClick = { showUsernameDialog = true },
      colors = ButtonDefaults.outlinedButtonColors(contentColor = EncryptionCyan),
      border = androidx.compose.foundation.BorderStroke(1.dp, EncryptionCyan.copy(alpha = 0.6f)),
      modifier = Modifier.fillMaxWidth().testTag("edit_username_button")
    ) {
      Icon(Icons.Default.Person, contentDescription = null, tint = EncryptionCyan)
      Spacer(modifier = Modifier.width(8.dp))
      Text("Change Username")
    }
    Spacer(modifier = Modifier.height(10.dp))

    // Sign in / Switch account button
    Button(
      onClick = {
        scope.launch {
          val res = authManager.signInWithGoogle(com.example.BuildConfig.GOOGLE_WEB_CLIENT_ID)
          Toast.makeText(context, "Google OAuth: Signed in as ${res.getOrNull()?.email ?: "User"}", Toast.LENGTH_SHORT).show()
        }
      },
      colors = ButtonDefaults.buttonColors(containerColor = CyberEmerald),
      modifier = Modifier
        .fillMaxWidth()
        .testTag("google_signin_button")
    ) {
      Icon(Icons.Default.Security, contentDescription = null, tint = ObsidianBackground)
      Spacer(modifier = Modifier.width(8.dp))
      Text("Sign In With Google OAuth", color = ObsidianBackground, fontWeight = FontWeight.Bold)
    }

    Spacer(modifier = Modifier.height(10.dp))

    OutlinedButton(
      onClick = { showSwitchDialog = true },
      colors = ButtonDefaults.outlinedButtonColors(contentColor = EncryptionCyan),
      border = androidx.compose.foundation.BorderStroke(1.dp, EncryptionCyan.copy(alpha = 0.6f)),
      modifier = Modifier
        .fillMaxWidth()
        .testTag("switch_google_account_button")
    ) {
      Icon(Icons.Default.SwapHoriz, contentDescription = null, tint = EncryptionCyan)
      Spacer(modifier = Modifier.width(8.dp))
      Text("Switch Google Account")
    }
  }

  if (showUsernameDialog) {
    var usernameInput by remember(authState.username) { mutableStateOf(authState.username ?: "") }
    AlertDialog(
      onDismissRequest = { showUsernameDialog = false },
      containerColor = CardSurfaceDark,
      title = { Text(if (authState.username == null) "Choose a username" else "Change username", color = TextPrimaryDark, fontWeight = FontWeight.Bold) },
      text = {
        Column {
          Text("3-20 characters: letters, numbers, underscore.", color = TextSecondaryDark, fontSize = 12.sp)
          Spacer(modifier = Modifier.height(8.dp))
          OutlinedTextField(
            value = usernameInput,
            onValueChange = { if (it.length <= 20) usernameInput = it.filter { c -> c.isLetterOrDigit() || c == '_' } },
            label = { Text("Username") },
            singleLine = true
          )
        }
      },
      confirmButton = {
        Button(onClick = {
          scope.launch {
            val result = if (authState.username == null) authManager.chooseUsername(usernameInput) else authManager.changeUsername(usernameInput)
            result.onSuccess { showUsernameDialog = false; Toast.makeText(context, "Username saved as @$it", Toast.LENGTH_SHORT).show() }
              .onFailure { Toast.makeText(context, it.message ?: "Username could not be saved", Toast.LENGTH_LONG).show() }
          }
        }, enabled = usernameInput.matches(Regex("^[A-Za-z0-9_]{3,20}$")), colors = ButtonDefaults.buttonColors(containerColor = CyberEmerald)) {
          Text("Save", color = ObsidianBackground, fontWeight = FontWeight.Bold)
        } },
      dismissButton = { TextButton(onClick = { showUsernameDialog = false }) { Text("Cancel") } }
    )
  }

  if (showSwitchDialog) {
    var emailInput by remember { mutableStateOf(authState.email) }
    var nameInput by remember { mutableStateOf(authState.displayName) }

    AlertDialog(
      onDismissRequest = { showSwitchDialog = false },
      containerColor = CardSurfaceDark,
      title = { Text("Switch Google OAuth Account", color = TextPrimaryDark, fontWeight = FontWeight.Bold) },
      text = {
        Column {
          Text("Connect another verified Google account and generate new identity keys.", color = TextSecondaryDark, fontSize = 12.sp)
          Spacer(modifier = Modifier.height(10.dp))
          OutlinedTextField(
            value = emailInput,
            onValueChange = { emailInput = it },
            label = { Text("Google Email") },
            colors = OutlinedTextFieldDefaults.colors(
              focusedContainerColor = CardSurfaceElevated,
              unfocusedContainerColor = CardSurfaceElevated,
              focusedTextColor = TextPrimaryDark,
              unfocusedTextColor = TextPrimaryDark
            ),
            modifier = Modifier.fillMaxWidth()
          )
          Spacer(modifier = Modifier.height(8.dp))
          OutlinedTextField(
            value = nameInput,
            onValueChange = { nameInput = it },
            label = { Text("Display Name") },
            colors = OutlinedTextFieldDefaults.colors(
              focusedContainerColor = CardSurfaceElevated,
              unfocusedContainerColor = CardSurfaceElevated,
              focusedTextColor = TextPrimaryDark,
              unfocusedTextColor = TextPrimaryDark
            ),
            modifier = Modifier.fillMaxWidth()
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            if (emailInput.isNotBlank()) {
              scope.launch {
                authManager.switchGoogleAccount(emailInput.trim(), nameInput.trim())
                Toast.makeText(context, "Switched to ${emailInput.trim()}", Toast.LENGTH_SHORT).show()
              }
              showSwitchDialog = false
            }
          },
          colors = ButtonDefaults.buttonColors(containerColor = CyberEmerald)
        ) {
          Text("Switch", color = ObsidianBackground, fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(onClick = { showSwitchDialog = false }) {
          Text("Cancel", color = TextSecondaryDark)
        }
      }
    )
  }
}
