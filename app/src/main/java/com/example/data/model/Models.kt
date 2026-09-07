package com.example.data.model

enum class MessageType {
  TEXT,
  IMAGE,
  VIDEO,
  AUDIO_VOICE_NOTE,
  CALL_EVENT
}

enum class MessageStatus {
  SENDING,
  SENT,
  DELIVERED,
  READ
}

enum class CallType {
  VOICE,
  VIDEO
}

enum class CallDirection {
  INCOMING,
  OUTGOING,
  MISSED
}
