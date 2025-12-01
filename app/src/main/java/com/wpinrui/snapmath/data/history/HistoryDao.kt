package com.wpinrui.snapmath.data.history

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY timestamp DESC")
    fun getAllEntries(): Flow<List<HistoryEntry>>

    @Query("SELECT * FROM history WHERE type = :type ORDER BY timestamp DESC")
    fun getEntriesByType(type: EntryType): Flow<List<HistoryEntry>>

    @Query("SELECT * FROM history WHERE id = :id")
    suspend fun getEntryById(id: Long): HistoryEntry?

    @Insert
    suspend fun insert(entry: HistoryEntry): Long

    @Delete
    suspend fun delete(entry: HistoryEntry)

    @Query("DELETE FROM history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM history")
    suspend fun deleteAll()
}
