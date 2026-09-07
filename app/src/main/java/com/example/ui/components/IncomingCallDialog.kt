package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.call.CallState
import com.example.ui.theme.BorderDark
import com.example.ui.theme.CardSurfaceDark
import com.example.ui.theme.CyberEmerald
import com.example.ui.theme.CyberEmeraldDark
import com.example.ui.theme.DangerRose
import com.example.ui.theme.ObsidianBackground
import com.example.ui.theme.TextMutedDark
import com.example.ui.theme.TextPrimaryDark
import com.example.ui.theme.TextSecondaryDark

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IncomingCallDialog(
  callState: CallState.Incoming,
  onAccept: () -> Unit,
  onDecline: () -> Unit
) {
  BasicAlertDialog(
    onDismissRequest = onDecline,
    modifier = Modifier.testTag("incoming_call_dialog")
  ) {
    Card(
      colors = CardDefaults.cardColors(containerColor = CardSurfaceDark),
      shape = RoundedCornerShape(20.dp),
      border = androidx.compose.foundation.BorderStroke(1.5.dp, CyberEmerald),
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp)
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Surface(
          color = CyberEmeraldDark.copy(alpha = 0.5f),
          shape = RoundedCornerShape(14.dp),
          border = androidx.compose.foundation.BorderStroke(1.dp, CyberEmerald)
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(Icons.Default.Lock, contentDescription = null, tint = CyberEmerald, modifier = Modifier.size(12.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("INCOMING ENCRYPTED CALL", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CyberEmerald)
          }
        }

        Spacer(modifier = Modifier.height(16.dp))

        ContactAvatar(name = callState.contactName, isOnline = true, size = 72)

        Spacer(modifier = Modifier.height(12.dp))

        Text(
          text = callState.contactName,
          fontSize = 20.sp,
          fontWeight = FontWeight.Bold,
          color = TextPrimaryDark,
          textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
          text = "E2EE Verified: ${callState.fingerprint}",
          fontSize = 10.sp,
          color = TextMutedDark,
          fontFamily = FontFamily.Monospace,
          textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Call Action Buttons (Accept / Decline)
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceEvenly,
          verticalAlignment = Alignment.CenterVertically
        ) {
          // Decline
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            FilledIconButton(
              onClick = onDecline,
              colors = IconButtonDefaults.filledIconButtonColors(containerColor = DangerRose),
              modifier = Modifier
                .size(56.dp)
                .testTag("decline_call_button")
            ) {
              Icon(Icons.Default.CallEnd, contentDescription = "Decline Call", tint = Color.White)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text("Decline", fontSize = 11.sp, color = DangerRose)
          }

          // Accept
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            FilledIconButton(
              onClick = onAccept,
              colors = IconButtonDefaults.filledIconButtonColors(containerColor = CyberEmerald),
              modifier = Modifier
                .size(56.dp)
                .testTag("accept_call_button")
            ) {
              Icon(Icons.Default.Call, contentDescription = "Accept Call", tint = ObsidianBackground)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text("Accept", fontSize = 11.sp, color = CyberEmerald)
          }
        }
      }
    }
  }
}
