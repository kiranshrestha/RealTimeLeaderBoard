package com.example.realtimeleaderboard.leaderboard.repository

import com.example.realtimeleaderboard.leaderboard.model.LeaderboardState
import com.example.realtimeleaderboard.score_engine.model.Player
import com.example.realtimeleaderboard.score_engine.repository.ScoreEngineRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class LeaderboardRepositoryImpl(
    private val scoreEngine: ScoreEngineRepository,
    dispatcher: CoroutineDispatcher,
) : LeaderboardRepository {

    private val repoScope = CoroutineScope(SupervisorJob() + dispatcher)
    private var observerJob: Job? = null

    private val currentScores = mutableMapOf<String, Long>()
    private val playerMap = mutableMapOf<String, Player>()

    private val _leaderboardState = MutableStateFlow<LeaderboardState>(LeaderboardState.Loading)
    override val leaderBoardState: StateFlow<LeaderboardState> = _leaderboardState.asStateFlow()


    override fun startObserving() {
        if (observerJob != null) return
        observerJob = repoScope.launch {

            // 1. Wait for players to be available
            val players = scoreEngine.players.first { it.isNotEmpty() }
            players.forEach { player ->
                playerMap[player.id] = player
                currentScores[player.id] = 0L
            }

            // 2. Set initial state (all zeros, ranked alphabetically)
            val initial = RankingEngine.buildInitial(players)
            _leaderboardState.value = LeaderboardState.Active(entries = initial)

            // 3. Continuously consume score updates
            scoreEngine.scoreUpdates
                .catch { e ->
                    _leaderboardState.value = LeaderboardState.Error(
                        message = e.message ?: "Unknown engine error"
                    )
                }
                .collect { update ->
//                    Log.i("TAG", "startObserving: $update")
                    val currentState = _leaderboardState.value
                    val previousEntries = if (currentState is LeaderboardState.Active) {
                        currentState.entries
                    } else {
                        emptyList()
                    }

                    val newEntries = RankingEngine.applyUpdate(
                        currentScores = currentScores,
                        players = playerMap,
                        update = update,
                        previousEntries = previousEntries,
                    )

                    _leaderboardState.value = LeaderboardState.Active(
                        entries = newEntries,
                        lastUpdatedAt = update.timestamp,
                    )
                }
        }
    }

    override fun stopObserving() {
        observerJob?.cancel()
        observerJob = null
    }
}