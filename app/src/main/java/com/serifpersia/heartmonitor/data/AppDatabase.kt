package com.serifpersia.heartmonitor.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "heart_rate_sessions")
data class HeartRateSession(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long,
    val durationSeconds: Int,
    val avgBpm: Int,
    val minBpm: Int,
    val maxBpm: Int
)

@Dao
interface HeartRateDao {
    @Insert
    suspend fun insertSession(session: HeartRateSession)

    @Query("SELECT * FROM heart_rate_sessions ORDER BY timestamp DESC")
    fun getAllSessions(): Flow<List<HeartRateSession>>

    @Query("DELETE FROM heart_rate_sessions WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: Int)
}

@Database(entities = [HeartRateSession::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun heartRateDao(): HeartRateDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "heart_monitor_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}