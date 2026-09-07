package com.example.call

import android.content.Context
import android.media.AudioManager
import com.example.crypto.CryptoEngine
import com.example.data.model.CallDirection
import com.example.data.repository.SecureRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

sealed class CallState {
  object Idle : CallState()
  data class Dialing(val contactId: String, val contactName: String, val fingerprint: String) : CallState()
  data class Incoming(val contactId: String, val contactName: String, val fingerprint: String) : CallState()
  data class Connected(
    val contactId: String,
    val contactName: String,
    val fingerprint: String,
    val durationSeconds: Int,
    val encryptedPacketsTransmitted: Long,
    val isMuted: Boolean,
    val isSpeakerOn: Boolean,
    val sasMnemonic: String,
    val isSafetyVerified: Boolean,
    val audioWaveLevels: List<Float>
  ) : CallState()
  data class Ended(val contactId: String, val contactName: String, val durationSeconds: Int) : CallState()
}

class VoiceCallManager(
  private val context: Context,
  private val repository: SecureRepository
) {
  private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
  private val scope = CoroutineScope(Dispatchers.Default)

  private val _callState = MutableStateFlow<CallState>(CallState.Idle)
  val callState: StateFlow<CallState> = _callState.asStateFlow()

  private var callJob: Job? = null
  private var currentDuration = 0
  private var packetCounter = 0L

  fun startOutgoingCall(contactId: String, contactName: String, fingerprint: String) {
    callJob?.cancel()
    _callState.value = CallState.Dialing(contactId, contactName, fingerprint)

    callJob = scope.launch {
      // Dialing for 2 seconds
      delay(2000)
      if (!isActive) return@launch

      // Connected!
      currentDuration = 0
      packetCounter = 0L
      val sas = CryptoEngine.generateSasWords("me", contactId)

      val initialConnected = CallState.Connected(
        contactId = contactId,
        contactName = contactName,
        fingerprint = fingerprint,
        durationSeconds = 0,
        encryptedPacketsTransmitted = 0,
        isMuted = false,
        isSpeakerOn = false,
        sasMnemonic = sas,
        isSafetyVerified = true,
        audioWaveLevels = listOf(0.2f, 0.4f, 0.6f, 0.8f, 0.5f, 0.3f)
      )
      _callState.value = initialConnected

      var muted = false
      var speaker = false
      var verified = true

      while (isActive) {
        delay(1000)
        currentDuration += 1
        packetCounter += 50 // 50 audio packets per second (20ms frames)

        // Generate dynamic audio waveform fluctuation
        val randomWaves = List(8) { Random.nextFloat() * 0.8f + 0.15f }

        _callState.value = CallState.Connected(
          contactId = contactId,
          contactName = contactName,
          fingerprint = fingerprint,
          durationSeconds = currentDuration,
          encryptedPacketsTransmitted = packetCounter,
          isMuted = muted,
          isSpeakerOn = speaker,
          sasMnemonic = sas,
          isSafetyVerified = verified,
          audioWaveLevels = randomWaves
        )
      }
    }
  }

  fun triggerIncomingCall(contactId: String, contactName: String, fingerprint: String) {
    callJob?.cancel()
    _callState.value = CallState.Incoming(contactId, contactName, fingerprint)
  }

  fun acceptIncomingCall() {
    val current = _callState.value
    if (current !is CallState.Incoming) return

    val contactId = current.contactId
    val contactName = current.contactName
    val fingerprint = current.fingerprint

    callJob?.cancel()
    callJob = scope.launch {
      currentDuration = 0
      packetCounter = 0L
      val sas = CryptoEngine.generateSasWords("me", contactId)

      while (isActive) {
        delay(1000)
        currentDuration += 1
        packetCounter += 50
        val randomWaves = List(8) { Random.nextFloat() * 0.8f + 0.15f }

        val currentState = _callState.value as? CallState.Connected
        val isMuted = currentState?.isMuted ?: false
        val isSpeaker = currentState?.isSpeakerOn ?: false
        val isVerified = currentState?.isSafetyVerified ?: true

        _callState.value = CallState.Connected(
          contactId = contactId,
          contactName = contactName,
          fingerprint = fingerprint,
          durationSeconds = currentDuration,
          encryptedPacketsTransmitted = packetCounter,
          isMuted = isMuted,
          isSpeakerOn = isSpeaker,
          sasMnemonic = sas,
          isSafetyVerified = isVerified,
          audioWaveLevels = randomWaves
        )
      }
    }
  }

  fun toggleMute() {
    val current = _callState.value
    if (current is CallState.Connected) {
      _callState.value = current.copy(isMuted = !current.isMuted)
    }
  }

  fun toggleSpeaker() {
    val current = _callState.value
    if (current is CallState.Connected) {
      val newSpeakerState = !current.isSpeakerOn
      audioManager?.isSpeakerphoneOn = newSpeakerState
      _callState.value = current.copy(isSpeakerOn = newSpeakerState)
    }
  }

  fun endCall() {
    val current = _callState.value
    callJob?.cancel()

    when (current) {
      is CallState.Connected -> {
        scope.launch {
          repository.logEncryptedVoiceCall(
            contactId = current.contactId,
            contactName = current.contactName,
            direction = CallDirection.OUTGOING,
            durationSeconds = current.durationSeconds,
            fingerprint = current.fingerprint
          )
        }
        _callState.value = CallState.Ended(current.contactId, current.contactName, current.durationSeconds)
      }
      is CallState.Dialing -> {
        _callState.value = CallState.Ended(current.contactId, current.contactName, 0)
      }
      is CallState.Incoming -> {
        scope.launch {
          repository.logEncryptedVoiceCall(
            contactId = current.contactId,
            contactName = current.contactName,
            direction = CallDirection.MISSED,
            durationSeconds = 0,
            fingerprint = current.fingerprint
          )
        }
        _callState.value = CallState.Ended(current.contactId, current.contactName, 0)
      }
      else -> {
        _callState.value = CallState.Idle
      }
    }

    scope.launch {
      delay(1500)
      _callState.value = CallState.Idle
    }
  }
}
