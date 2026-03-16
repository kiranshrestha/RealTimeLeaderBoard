package com.example.realtimeleaderboard.leaderboard.usecase

import com.example.realtimeleaderboard.leaderboard.model.LeaderboardState
import com.example.realtimeleaderboard.leaderboard.repository.LeaderboardRepository
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

class ObserveLeaderboardUseCase @Inject constructor(
    private val leaderboardRepository: LeaderboardRepository,
) {
    operator fun invoke(): StateFlow<LeaderboardState> {
        return leaderboardRepository.leaderBoardState
    }
}