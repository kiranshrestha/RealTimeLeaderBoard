package com.example.realtimeleaderboard.leaderboard.model

sealed class LeaderboardState {
    data object Loading : LeaderboardState()
    data class Active(
        val entries: List<LeaderboardEntry>,
        val lastUpdatedAt: Long = System.currentTimeMillis(),
    ) : LeaderboardState()

    data class Error(val message: String) : LeaderboardState()
}
