package com.wpinrui.snapmath.data.history

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a history entry for a solved or checked math problem.
 */
@Entity(tableName = "history")
data class HistoryEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val type: EntryType,
    val problem: String,
    val result: String,
    val isCorrect: Boolean? = null, // Only for Check entries
    val timestamp: Long = System.currentTimeMillis()
)

enum class EntryType {
    SOLVE,
    CHECK
}
