package com.example.chess.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "match_records")
data class MatchRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val opponentName: String,
    val opponentRating: Int,
    val result: String, // "WIN", "LOSS", "DRAW"
    val gameMode: String, // "ONLINE", "COMPUTER", "LOCAL"
    val finalMovesCount: Int,
    val playerRatingAfter: Int,
    val timestamp: Long = System.currentTimeMillis()
)
