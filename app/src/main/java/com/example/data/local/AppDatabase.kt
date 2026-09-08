package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.dao.CallLogDao
import com.example.data.dao.ConversationDao
import com.example.data.dao.MessageDao
import com.example.data.dao.UserProfileDao
import com.example.data.entity.CallLogEntity
import com.example.data.entity.ConversationEntity
import com.example.data.entity.MessageEntity
import com.example.data.entity.UserProfileEntity

@Database(
  entities = [
    ConversationEntity::class,
    MessageEntity::class,
    CallLogEntity::class,
    UserProfileEntity::class
  ],
  // v2: the conversation contact identity fields changed to UID + username. Existing local
  // conversation rows cannot be losslessly mapped from email, so the existing
  // fallbackToDestructiveMigration policy is intentionally used for this schema change.
  version = 2,
  exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
  abstract fun conversationDao(): ConversationDao
  abstract fun messageDao(): MessageDao
  abstract fun callLogDao(): CallLogDao
  abstract fun userProfileDao(): UserProfileDao

  companion object {
    @Volatile
    private var INSTANCE: AppDatabase? = null

    fun getInstance(context: Context): AppDatabase {
      return INSTANCE ?: synchronized(this) {
        val instance = Room.databaseBuilder(
          context.applicationContext,
          AppDatabase::class.java,
          "cipher_chat_secure.db"
        )
          .fallbackToDestructiveMigration()
          .build()
        INSTANCE = instance
        instance
      }
    }
  }
}
