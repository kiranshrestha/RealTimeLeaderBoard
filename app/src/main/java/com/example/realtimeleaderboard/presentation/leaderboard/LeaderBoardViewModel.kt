package com.example.realtimeleaderboard.presentation.leaderboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.realtimeleaderboard.leaderboard.model.LeaderboardState
import com.example.realtimeleaderboard.leaderboard.repository.LeaderboardRepository
import com.example.realtimeleaderboard.leaderboard.usecase.ObserveLeaderboardUseCase
import com.example.realtimeleaderboard.score_engine.repository.ScoreEngineRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LeaderBoardViewModel @Inject constructor(
    private val leaderboardRepository: LeaderboardRepository,
    private val scoreEngine: ScoreEngineRepository,
    observeLeaderboardUseCase: ObserveLeaderboardUseCase,
) : ViewModel() {
    val uiState: StateFlow<LeaderboardState> = observeLeaderboardUseCase()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5000),
            initialValue = LeaderboardState.Loading
        )

    init {
        leaderboardRepository.startObserving()
        scoreEngine.start()
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch {
            scoreEngine.stop()
        }
        leaderboardRepository.stopObserving()
    }

}