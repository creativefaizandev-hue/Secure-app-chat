
package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.auth.GoogleAuthManager
import com.example.ui.theme.CardSurfaceDark
import com.example.ui.theme.CyberEmerald
import com.example.ui.theme.ObsidianBackground
import com.example.ui.theme.TextPrimaryDark
import com.example.ui.theme.TextSecondaryDark
import kotlinx.coroutines.launch

@Composable
fun UsernameSetupScreen(authManager: GoogleAuthManager) {
  var username by remember { mutableStateOf("") }
  val scope = rememberCoroutineScope()
  val context = LocalContext.current
  val valid = username.matches(Regex("^[A-Za-z0-9_]{3,20}$"))
  Column(
    modifier = Modifier.fillMaxSize().padding(24.dp),
    verticalArrangement = Arrangement.Center
  ) {
    Text("Choose your username", color = TextPrimaryDark, fontSize = 28.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
    Text("This is the name other users will search for. Your email stays private.", color = TextSecondaryDark, fontSize = 13.sp)
    Spacer(Modifier.height(20.dp))
    OutlinedTextField(
      value = username,
      onValueChange = { if (it.length <= 20) username = it.filter { c -> c.isLetterOrDigit() || c == '_' } },
      label = { Text("Username") }, singleLine = true,
      modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(8.dp))
    Text("3-20 characters • A-Z • 0-9 • _", color = TextSecondaryDark, fontSize = 12.sp)
    Spacer(Modifier.height(18.dp))
    Button(
      enabled = valid,
      onClick = {
        scope.launch {
          authManager.chooseUsername(username)
            .onFailure { Toast.makeText(context, it.message ?: "Username is unavailable", Toast.LENGTH_LONG).show() }
        }
      },
      colors = ButtonDefaults.buttonColors(containerColor = CyberEmerald),
      modifier = Modifier.fillMaxWidth()
    ) { Text("Continue", color = ObsidianBackground, fontWeight = FontWeight.Bold) }
  }
}

@Composable
fun SignInRequiredScreen(onSignIn: () -> Unit) {
  Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
    Text("CipherChat", color = TextPrimaryDark, fontSize = 30.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
    Text("Sign in with Google to continue.", color = TextSecondaryDark, fontSize = 14.sp)
    Spacer(Modifier.height(18.dp))
    Button(onClick = onSignIn, colors = ButtonDefaults.buttonColors(containerColor = CyberEmerald), modifier = Modifier.fillMaxWidth()) {
      Text("Sign in with Google", color = ObsidianBackground, fontWeight = FontWeight.Bold)
    }
  }
}
