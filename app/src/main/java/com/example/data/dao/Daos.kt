package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.entity.CallLogEntity
import com.example.data.entity.ConversationEntity
import com.example.data.entity.MessageEntity
import com.example.data.entity.UserProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {
  @Query("SELECT * FROM conversations ORDER BY lastMessageTimestamp DESC")
  fun getAllConversations(): Flow<List<ConversationEntity>>

  @Query("SELECT * FROM conversations WHERE id = :id LIMIT 1")
  fun getConversationById(id: String): Flow<ConversationEntity?>

  @Query("SELECT * FROM conversations WHERE id = :id LIMIT 1")
  suspend fun getConversationSync(id: String): ConversationEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertOrUpdate(conversation: ConversationEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertAll(conversations: List<ConversationEntity>)

  @Update
  suspend fun update(conversation: ConversationEntity)

  @Query("UPDATE conversations SET isVerified = :isVerified WHERE id = :id")
  suspend fun updateVerification(id: String, isVerified: Boolean)

  @Query("DELETE FROM conversations WHERE id = :id")
  suspend fun deleteById(id: String)
}

@Dao
interface MessageDao {
  @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
  fun getMessagesForConversation(conversationId: String): Flow<List<MessageEntity>>

  @Query("SELECT COUNT(*) FROM messages")
  fun getTotalMessageCount(): Flow<Int>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insert(message: MessageEntity): Long

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertAll(messages: List<MessageEntity>)

  @Query("DELETE FROM messages WHERE conversationId = :conversationId")
  suspend fun clearMessagesForConversation(conversationId: String)

  @Query("DELETE FROM messages")
  suspend fun clearAllMessages()
}

@Dao
interface CallLogDao {
  @Query("SELECT * FROM call_logs ORDER BY timestamp DESC")
  fun getAllCallLogs(): Flow<List<CallLogEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insert(callLog: CallLogEntity): Long

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertAll(callLogs: List<CallLogEntity>)

  @Query("DELETE FROM call_logs")
  suspend fun clearAll()
}

@Dao
interface UserProfileDao {
  @Query("SELECT * FROM user_profile LIMIT 1")
  fun getProfile(): Flow<UserProfileEntity?>

  @Query("SELECT * FROM user_profile LIMIT 1")
  suspend fun getProfileSync(): UserProfileEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertOrUpdate(profile: UserProfileEntity)
}
