package com.example.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.example.crypto.CryptoEngine
import com.example.data.entity.UserProfileEntity
import com.example.data.repository.SecureRepository
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class AuthUserState(
  val isAuthenticated: Boolean = false,
  val email: String = "",
  val username: String? = null,
  val identityResolved: Boolean = false,
  val displayName: String = "",
  val photoUrl: String? = null,
  val authProvider: String = "Not Signed In",
  val keyFingerprint: String = "",
  val isHardwareKeystoreBound: Boolean = false
)

class GoogleAuthManager(
  private val context: Context,
  private val repository: SecureRepository
) {
  private val credentialManager = CredentialManager.create(context)
  private val firebaseAuth by lazy { FirebaseAuth.getInstance() }

  private val _authState = MutableStateFlow(AuthUserState())
  val authState: StateFlow<AuthUserState> = _authState.asStateFlow()

  init {
    try {
      firebaseAuth.currentUser?.let { user ->
        val state = AuthUserState(
          isAuthenticated = true,
          email = user.email ?: "",
          displayName = user.displayName ?: "Firebase User",
          photoUrl = user.photoUrl?.toString(),
          authProvider = "Google OAuth 2.0 (Firebase)",
          keyFingerprint = CryptoEngine.computeKeyFingerprint(user.email ?: user.uid),
          isHardwareKeystoreBound = true
        )
        _authState.value = state.copy(identityResolved = false)
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main.immediate).launch {
          val username = repository.getUsername(user.uid)
          _authState.value = state.copy(username = username, identityResolved = true)
          if (username != null) repository.startFirestoreSync()
        }
      }
    } catch (e: Exception) {
      // Firebase not initialized yet
      e.printStackTrace()
    }
  }

  suspend fun signInWithGoogle(webClientId: String): Result<AuthUserState> {
    return try {
      val googleIdOption = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(false)
        .setServerClientId(webClientId)
        .setAutoSelectEnabled(false)
        .build()

      val request = GetCredentialRequest.Builder()
        .addCredentialOption(googleIdOption)
        .build()

      val result = credentialManager.getCredential(context, request)
      val credential = result.credential

      if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
        val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
        
        // Sign in with Firebase
        val firebaseCredential = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
        val authResult = firebaseAuth.signInWithCredential(firebaseCredential).await()
        val firebaseUser = authResult.user
        
        if (firebaseUser != null) {
          val user = AuthUserState(
            isAuthenticated = true,
            email = firebaseUser.email ?: googleIdTokenCredential.id,
            displayName = firebaseUser.displayName ?: googleIdTokenCredential.displayName ?: "Google User",
            photoUrl = firebaseUser.photoUrl?.toString() ?: googleIdTokenCredential.profilePictureUri?.toString(),
            authProvider = "Google OAuth 2.0 (Firebase)",
            keyFingerprint = CryptoEngine.computeKeyFingerprint(firebaseUser.email ?: firebaseUser.uid),
            isHardwareKeystoreBound = true
          )
          saveProfile(user)
          val username = repository.getUsername(firebaseUser.uid)
          _authState.value = user.copy(username = username, identityResolved = true)
          if (username != null) repository.startFirestoreSync()
          Result.success(user.copy(username = username, identityResolved = true))
        } else {
          Result.failure(Exception("Firebase Auth returned null user"))
        }
      } else {
        Result.failure(Exception("Invalid credential type"))
      }
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  suspend fun switchGoogleAccount(newEmail: String, newName: String) {
      // Google account switching remains a sign-out + fresh credential flow.
      signOut()
  }

  suspend fun chooseUsername(username: String): Result<String> {
      val result = repository.setUsername(username)
      result.onSuccess { chosen ->
          _authState.value = _authState.value.copy(username = chosen, identityResolved = true)
          repository.startFirestoreSync()
      }
      return result
  }

  suspend fun changeUsername(username: String): Result<String> {
      val result = repository.changeUsername(username)
      result.onSuccess { chosen ->
          _authState.value = _authState.value.copy(username = chosen, identityResolved = true)
      }
      return result
  }

  fun signOut() {
    try {
      firebaseAuth.signOut()
    } catch (e: Exception) {
      e.printStackTrace()
    }
    _authState.value = AuthUserState(
      isAuthenticated = false,
      email = "",
      username = null,
      identityResolved = true,
      displayName = "",
      photoUrl = null,
      authProvider = "Not Signed In",
      keyFingerprint = "",
      isHardwareKeystoreBound = false
    )
  }

  private suspend fun saveProfile(user: AuthUserState) {
    val uid = try { firebaseAuth.currentUser?.uid ?: "google_user_current" } catch (e: Exception) { "google_user_current" }
    val profile = UserProfileEntity(
      userId = uid,
      email = user.email,
      displayName = user.displayName,
      photoUrl = user.photoUrl,
      e2eePublicKeyFingerprint = user.keyFingerprint,
      isGoogleOAuthConnected = true
    )
    repository.updateUserProfile(profile)
  }
}
