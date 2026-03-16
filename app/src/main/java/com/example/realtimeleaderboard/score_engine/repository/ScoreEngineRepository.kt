package com.example.realtimeleaderboard.score_engine.repository

import com.example.realtimeleaderboard.score_engine.model.Player
import com.example.realtimeleaderboard.score_engine.model.ScoreUpdate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface ScoreEngineRepository {
    val players: StateFlow<List<Player>>
    val scoreUpdates: Flow<ScoreUpdate>

    fun start()

    suspend fun stop()
}