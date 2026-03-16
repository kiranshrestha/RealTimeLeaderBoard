package com.example.realtimeleaderboard.score_engine.model

data class ScoreUpdate(
    val playerId: String,
    val newScore: Long,
    val delta: Long,
    val timestamp: Long = System.currentTimeMillis()
)