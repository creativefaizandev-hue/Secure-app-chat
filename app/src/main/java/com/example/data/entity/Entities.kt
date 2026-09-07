package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversations")
data class ConversationEntity(
  @PrimaryKey val id: String,
  val contactName: String,
  val contactEmail: String,
  val avatarSeed: String,
  val lastEncryptedMessage: String,
  val lastMessageTimestamp: Long,
  val unreadCount: Int = 0,
  val safetyNumber: String,
  val isVerified: Boolean = false,
  val e2eeFingerprint: String,
  val isOnline: Boolean = false
)

@Entity(tableName = "messages")
data class MessageEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val conversationId: String,
  val senderId: String,
  val senderName: String,
  val isOutgoing: Boolean,
  val cipherText: String,
  val iv: String,
  val encryptionAlgorithm: String = "AES-256-GCM",
  val messageType: String, // from MessageType enum name
  val mediaBase64OrUri: String? = null,
  val mediaDurationSeconds: Int = 0,
  val timestamp: Long = System.currentTimeMillis(),
  val status: String = "SENT", // from MessageStatus enum name
  val isDecryptedLocally: Boolean = true
)

@Entity(tableName = "call_logs")
data class CallLogEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val contactId: String,
  val contactName: String,
  val callType: String = "VOICE", // VOICE, VIDEO
  val direction: String, // INCOMING, OUTGOING, MISSED
  val durationSeconds: Int,
  val timestamp: Long = System.currentTimeMillis(),
  val e2eeFingerprint: String,
  val encryptionVerified: Boolean = true
)

@Entity(tableName = "user_profile")
data class UserProfileEntity(
  @PrimaryKey val userId: String,
  val email: String,
  val displayName: String,
  val photoUrl: String? = null,
  val e2eePublicKeyFingerprint: String,
  val isGoogleOAuthConnected: Boolean = true,
  val hardwareKeyStoreId: String = "HSM-TEE-HW-KEY-001",
  val createdAt: Long = System.currentTimeMillis()
)
