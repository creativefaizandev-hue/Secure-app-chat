package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.BorderDark
import com.example.ui.theme.CardSurfaceDark
import com.example.ui.theme.CardSurfaceElevated
import com.example.ui.theme.CyberEmerald
import com.example.ui.theme.CyberEmeraldDark
import com.example.ui.theme.EncryptionCyan
import com.example.ui.theme.ObsidianBackground
import com.example.ui.theme.SecurityAmber
import com.example.ui.theme.TextMutedDark
import com.example.ui.theme.TextPrimaryDark
import com.example.ui.theme.TextSecondaryDark

@Composable
fun SecurityBadge(
  text: String = "AES-256-GCM Verified",
  isVerified: Boolean = true,
  modifier: Modifier = Modifier
) {
  Surface(
    modifier = modifier.clip(RoundedCornerShape(12.dp)),
    color = if (isVerified) CyberEmeraldDark.copy(alpha = 0.4f) else SecurityAmber.copy(alpha = 0.2f),
    shape = RoundedCornerShape(12.dp),
    border = androidx.compose.foundation.BorderStroke(
      1.dp,
      if (isVerified) CyberEmerald.copy(alpha = 0.6f) else SecurityAmber.copy(alpha = 0.5f)
    )
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Icon(
        imageVector = if (isVerified) Icons.Default.Lock else Icons.Default.Security,
        contentDescription = null,
        tint = if (isVerified) CyberEmerald else SecurityAmber,
        modifier = Modifier.size(12.dp)
      )
      Spacer(modifier = Modifier.width(4.dp))
      Text(
        text = text,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        color = if (isVerified) CyberEmerald else SecurityAmber,
        letterSpacing = 0.5.sp
      )
    }
  }
}

@Composable
fun ContactAvatar(
  name: String,
  modifier: Modifier = Modifier,
  isOnline: Boolean = false,
  isVerified: Boolean = true,
  size: Int = 48
) {
  val initials = name.split(" ")
    .mapNotNull { it.firstOrNull()?.toString() }
    .take(2)
    .joinToString("")
    .ifEmpty { "C" }

  // Generate a distinct cyber gradient per contact name
  val hue = (name.hashCode() and 0x7FFFFFFF) % 360
  val bgBrush = Brush.linearGradient(
    colors = listOf(
      Color(0xFF0F2A38),
      Color(0xFF0D3B36),
      Color(0xFF132F4C)
    )
  )

  Box(
    modifier = modifier.size(size.dp),
    contentAlignment = Alignment.Center
  ) {
    Box(
      modifier = Modifier
        .size(size.dp)
        .clip(CircleShape)
        .background(bgBrush)
        .border(1.5.dp, if (isVerified) CyberEmerald else BorderDark, CircleShape),
      contentAlignment = Alignment.Center
    ) {
      Text(
        text = initials,
        fontWeight = FontWeight.Bold,
        fontSize = (size * 0.38).sp,
        color = TextPrimaryDark
      )
    }

    // Status indicator dot
    if (isOnline) {
      Box(
        modifier = Modifier
          .size((size * 0.28).dp)
          .align(Alignment.BottomEnd)
          .clip(CircleShape)
          .background(CyberEmerald)
          .border(2.dp, ObsidianBackground, CircleShape)
      )
    } else if (isVerified) {
      Box(
        modifier = Modifier
          .size((size * 0.32).dp)
          .align(Alignment.BottomEnd)
          .clip(CircleShape)
          .background(EncryptionCyan)
          .border(2.dp, ObsidianBackground, CircleShape),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = Icons.Default.CheckCircle,
          contentDescription = "Verified",
          tint = ObsidianBackground,
          modifier = Modifier.size((size * 0.22).dp)
        )
      }
    }
  }
}

@Composable
fun SafetyNumberDialog(
  contactName: String,
  safetyNumber: String,
  sasWords: String,
  fingerprint: String,
  isVerified: Boolean,
  onVerificationChanged: (Boolean) -> Unit,
  onDismiss: () -> Unit
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    modifier = Modifier.testTag("safety_number_dialog"),
    containerColor = CardSurfaceDark,
    title = {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
          imageVector = Icons.Default.Shield,
          contentDescription = null,
          tint = CyberEmerald,
          modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = "Verify Safety Numbers",
          color = TextPrimaryDark,
          fontWeight = FontWeight.Bold,
          fontSize = 18.sp
        )
      }
    },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Text(
          text = "To guarantee zero eavesdropping or man-in-the-middle attacks, compare these 60 digits or the Short Authentication String with $contactName.",
          fontSize = 12.sp,
          color = TextSecondaryDark,
          textAlign = TextAlign.Center,
          lineHeight = 16.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        // SAS Words Box
        Card(
          colors = CardDefaults.cardColors(containerColor = CardSurfaceElevated),
          shape = RoundedCornerShape(12.dp),
          border = androidx.compose.foundation.BorderStroke(1.dp, EncryptionCyan.copy(alpha = 0.5f)),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
          ) {
            Text(
              text = "VOICE VERIFICATION SAS",
              fontSize = 10.sp,
              fontWeight = FontWeight.Bold,
              color = EncryptionCyan,
              letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = sasWords,
              fontSize = 13.sp,
              fontWeight = FontWeight.SemiBold,
              color = TextPrimaryDark,
              fontFamily = FontFamily.Monospace,
              textAlign = TextAlign.Center
            )
          }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 60 Digits Display
        Card(
          colors = CardDefaults.cardColors(containerColor = ObsidianBackground),
          shape = RoundedCornerShape(12.dp),
          border = androidx.compose.foundation.BorderStroke(1.dp, BorderDark),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.Center
            ) {
              Icon(
                imageVector = Icons.Default.QrCode2,
                contentDescription = null,
                tint = CyberEmerald,
                modifier = Modifier.size(16.dp)
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = "60-DIGIT SAFETY NUMBER",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = CyberEmerald,
                letterSpacing = 1.sp
              )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
              text = safetyNumber,
              fontSize = 13.sp,
              fontWeight = FontWeight.Medium,
              color = TextPrimaryDark,
              fontFamily = FontFamily.Monospace,
              textAlign = TextAlign.Center,
              lineHeight = 20.sp
            )
          }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
          text = "Key Fingerprint: $fingerprint",
          fontSize = 10.sp,
          color = TextMutedDark,
          fontFamily = FontFamily.Monospace
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Verification Switch
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(CardSurfaceElevated)
            .padding(horizontal = 12.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = if (isVerified) Icons.Default.VerifiedUser else Icons.Default.Security,
              contentDescription = null,
              tint = if (isVerified) CyberEmerald else SecurityAmber,
              modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = if (isVerified) "Marked as Verified" else "Not Yet Verified",
              fontSize = 13.sp,
              fontWeight = FontWeight.Medium,
              color = TextPrimaryDark
            )
          }

          Switch(
            checked = isVerified,
            onCheckedChange = { onVerificationChanged(it) },
            colors = SwitchDefaults.colors(
              checkedThumbColor = CyberEmerald,
              checkedTrackColor = CyberEmeraldDark
            ),
            modifier = Modifier.testTag("verify_switch")
          )
        }
      }
    },
    confirmButton = {
      Button(
        onClick = onDismiss,
        colors = ButtonDefaults.buttonColors(containerColor = CyberEmerald),
        modifier = Modifier.testTag("dismiss_safety_dialog")
      ) {
        Text("Done", color = ObsidianBackground, fontWeight = FontWeight.Bold)
      }
    }
  )
}
