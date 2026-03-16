package com.example.realtimeleaderboard.leaderboard.repository

import com.example.realtimeleaderboard.leaderboard.model.LeaderboardState
import kotlinx.coroutines.flow.StateFlow

interface LeaderboardRepository {
    val leaderBoardState: StateFlow<LeaderboardState>

    fun startObserving()

    fun stopObserving()
}