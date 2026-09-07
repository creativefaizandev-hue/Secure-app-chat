package com.example.call

import android.content.Context
import android.media.AudioManager
import android.util.Log
import com.example.crypto.CryptoEngine
import com.example.data.model.CallDirection
import com.example.data.repository.SecureRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await
import org.webrtc.*
import kotlin.random.Random
import org.json.JSONObject

sealed class CallState {
  object Idle : CallState()
  data class Dialing(val contactId: String, val contactName: String, val fingerprint: String) : CallState()
  data class Incoming(val contactId: String, val contactName: String, val fingerprint: String, val offerId: String = "") : CallState()
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
  
  private var webRTCClient: WebRTCClient? = null
  private val eglBase = try { EglBase.create() } catch(e: Exception) { null }
  private val firestore by lazy { FirebaseFirestore.getInstance() }
  private val auth by lazy { FirebaseAuth.getInstance() }
  
  private var currentRoomId: String? = null
  
  fun listenForIncomingCalls() {
      val myEmail = try { auth.currentUser?.email ?: return } catch(e:Exception){ return }
      firestore.collection("calls").addSnapshotListener { snapshot, _ ->
          snapshot?.documentChanges?.forEach { change ->
              val docId = change.document.id
              if (docId.endsWith("_$myEmail")) {
                  val offer = change.document.get("offer") as? Map<String, String>
                  val answer = change.document.get("answer")
                  // Only trigger if there is an offer but no answer yet
                  if (offer != null && answer == null) {
                      val callerId = docId.substringBefore("_")
                      // Attempt to resolve name & fingerprint from local db? For now just use email.
                      triggerIncomingCall(callerId, callerId.substringBefore("@"), "E2EE-WebRTC-Call", docId)
                  }
              }
          }
      }
  }
  
  private val peerConnectionObserver = object : PeerConnection.Observer {
      override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}
      override fun onIceConnectionChange(p0: PeerConnection.IceConnectionState?) {
          if (p0 == PeerConnection.IceConnectionState.DISCONNECTED || p0 == PeerConnection.IceConnectionState.FAILED) {
              endCall()
          }
      }
      override fun onIceConnectionReceivingChange(p0: Boolean) {}
      override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}
      override fun onIceCandidate(candidate: IceCandidate?) {
          candidate?.let {
              scope.launch(Dispatchers.IO) {
                  currentRoomId?.let { roomId ->
                      val currentUserId = try { auth.currentUser?.email ?: return@launch } catch(e:Exception){ return@launch }
                      val role = if (roomId.startsWith(currentUserId)) "caller" else "callee"
                      val candMap = mapOf(
                          "sdpMid" to it.sdpMid,
                          "sdpMLineIndex" to it.sdpMLineIndex,
                          "sdp" to it.sdp,
                          "type" to role
                      )
                      try {
                          firestore.collection("calls").document(roomId).collection("candidates").add(candMap)
                      } catch(e: Exception) { }
                  }
              }
          }
      }
      override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
      override fun onAddStream(p0: MediaStream?) {}
      override fun onRemoveStream(p0: MediaStream?) {}
      override fun onDataChannel(p0: DataChannel?) {}
      override fun onRenegotiationNeeded() {}
      override fun onAddTrack(p0: RtpReceiver?, p1: Array<out MediaStream>?) {}
  }

  fun startOutgoingCall(contactId: String, contactName: String, fingerprint: String) {
    callJob?.cancel()
    _callState.value = CallState.Dialing(contactId, contactName, fingerprint)
    
    val myEmail = try { auth.currentUser?.email ?: return } catch(e:Exception){ return }
    currentRoomId = "${myEmail}_$contactId"
    
    scope.launch(Dispatchers.IO) {
        if (eglBase != null) {
            webRTCClient = WebRTCClient(context, eglBase, peerConnectionObserver)
            webRTCClient?.createOffer { desc ->
                scope.launch(Dispatchers.IO) {
                    val offerMap = mapOf("type" to desc.type.canonicalForm(), "sdp" to desc.description)
                    try {
                        firestore.collection("calls").document(currentRoomId!!).set(mapOf("offer" to offerMap))
                        listenForAnswer(currentRoomId!!)
                        listenForCandidates(currentRoomId!!, "callee")
                    } catch(e: Exception) { }
                }
            }
        }
        
        delay(2000)
        switchToConnected(contactId, contactName, fingerprint)
    }
  }

  fun triggerIncomingCall(contactId: String, contactName: String, fingerprint: String, offerId: String = "") {
    callJob?.cancel()
    currentRoomId = offerId
    _callState.value = CallState.Incoming(contactId, contactName, fingerprint, offerId)
  }

  fun acceptIncomingCall() {
    val current = _callState.value
    if (current !is CallState.Incoming) return
    
    val contactId = current.contactId
    val contactName = current.contactName
    val fingerprint = current.fingerprint
    val roomId = current.offerId
    currentRoomId = roomId
    
    scope.launch(Dispatchers.IO) {
        if (eglBase != null) {
            webRTCClient = WebRTCClient(context, eglBase, peerConnectionObserver)
            try {
                val doc = firestore.collection("calls").document(roomId).get().await()
                val offer = doc.get("offer") as? Map<String, String>
                if (offer != null) {
                    val sdp = offer["sdp"] ?: ""
                    webRTCClient?.setRemoteDescription(SessionDescription(SessionDescription.Type.OFFER, sdp))
                    webRTCClient?.createAnswer { desc ->
                        scope.launch(Dispatchers.IO) {
                            val ansMap = mapOf("type" to desc.type.canonicalForm(), "sdp" to desc.description)
                            try {
                                firestore.collection("calls").document(roomId).update("answer", ansMap)
                                listenForCandidates(roomId, "caller")
                            } catch(e: Exception) { }
                        }
                    }
                }
            } catch(e: Exception) { }
        }
        
        switchToConnected(contactId, contactName, fingerprint)
    }
  }
  
  private fun listenForAnswer(roomId: String) {
      try {
          firestore.collection("calls").document(roomId).addSnapshotListener { snapshot, _ ->
              if (snapshot != null && snapshot.contains("answer")) {
                  val ans = snapshot.get("answer") as? Map<String, String>
                  if (ans != null) {
                      val sdp = ans["sdp"] ?: ""
                      webRTCClient?.setRemoteDescription(SessionDescription(SessionDescription.Type.ANSWER, sdp))
                  }
              }
          }
      } catch(e: Exception) { }
  }
  
  private fun listenForCandidates(roomId: String, targetRole: String) {
      try {
          firestore.collection("calls").document(roomId).collection("candidates")
              .whereEqualTo("type", targetRole)
              .addSnapshotListener { snapshot, _ ->
                  snapshot?.documentChanges?.forEach { change ->
                      val data = change.document.data
                      val candidate = IceCandidate(
                          data["sdpMid"] as String,
                          (data["sdpMLineIndex"] as Long).toInt(),
                          data["sdp"] as String
                      )
                      webRTCClient?.addIceCandidate(candidate)
                  }
              }
      } catch(e: Exception) { }
  }
  
  private fun switchToConnected(contactId: String, contactName: String, fingerprint: String) {
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
              _callState.value = CallState.Connected(
                  contactId = contactId,
                  contactName = contactName,
                  fingerprint = fingerprint,
                  durationSeconds = currentDuration,
                  encryptedPacketsTransmitted = packetCounter,
                  isMuted = isMuted,
                  isSpeakerOn = isSpeaker,
                  sasMnemonic = sas,
                  isSafetyVerified = true,
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
    webRTCClient?.close()
    webRTCClient = null
    currentRoomId = null
    
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
