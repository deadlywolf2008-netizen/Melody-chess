package com.example.chess.data

import kotlinx.coroutines.flow.Flow

class MatchRepository(private val matchRecordDao: MatchRecordDao) {
    val allMatches: Flow<List<MatchRecord>> = matchRecordDao.getAllMatches()

    suspend fun insertMatch(match: MatchRecord) {
        matchRecordDao.insertMatch(match)
    }

    suspend fun clearHistory() {
        matchRecordDao.clearAllMatches()
    }
}
