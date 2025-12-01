package com.wpinrui.snapmath.data.history

import android.content.Context
import kotlinx.coroutines.flow.Flow

class HistoryRepository(context: Context) {
    private val dao = HistoryDatabase.getInstance(context).historyDao()

    fun getAllEntries(): Flow<List<HistoryEntry>> = dao.getAllEntries()

    fun getEntriesByType(type: EntryType): Flow<List<HistoryEntry>> = dao.getEntriesByType(type)

    suspend fun getEntryById(id: Long): HistoryEntry? = dao.getEntryById(id)

    suspend fun saveSolveEntry(problem: String, solution: String): Long {
        val entry = HistoryEntry(
            type = EntryType.SOLVE,
            problem = problem,
            result = solution
        )
        return dao.insert(entry)
    }

    suspend fun saveCheckEntry(problem: String, result: String, isCorrect: Boolean): Long {
        val entry = HistoryEntry(
            type = EntryType.CHECK,
            problem = problem,
            result = result,
            isCorrect = isCorrect
        )
        return dao.insert(entry)
    }

    suspend fun deleteEntry(id: Long) = dao.deleteById(id)

    suspend fun deleteAllEntries() = dao.deleteAll()
}
