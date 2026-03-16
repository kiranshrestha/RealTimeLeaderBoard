package com.example.realtimeleaderboard.score_engine.repository

import com.example.realtimeleaderboard.score_engine.model.Player
import com.example.realtimeleaderboard.score_engine.model.ScoreUpdate
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

class ScoreEngineRepositoryImpl(
    dispatcher: CoroutineDispatcher,
    playerCount: Int = DEFAULT_PLAYER_COUNT,
    private val minIntervalMs: Long = MIN_INTERVAL_MS,
    private val maxIntervalMs: Long = MAX_INTERVAL_MS,
) : ScoreEngineRepository {

    companion object {
        private const val DEFAULT_PLAYER_COUNT = 20
        private const val MIN_INTERVAL_MS = 500L
        private const val MAX_INTERVAL_MS = 2000L
        private const val MIN_SCORE_DELTA = 10L
        private const val MAX_SCORE_DELTA = 500L
    }

    private val _players = MutableStateFlow<List<Player>>(emptyList())
    override val players: StateFlow<List<Player>>
        get() = _players.asStateFlow()

    private val _scoreUpdate = MutableSharedFlow<ScoreUpdate>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val scoreUpdates: Flow<ScoreUpdate>
        get() = _scoreUpdate.asSharedFlow()


    private val engineScope = CoroutineScope(SupervisorJob() + dispatcher)
    private var engineJob: Job? = null

    private val currentScores = mutableMapOf<String, Long>()


    init {
        //Build the players list at construction time
        val roster = buildPlayerRoster(playerCount)
        roster.forEach {
            currentScores[it.id] = 0L
        }
        _players.value = roster
    }

    private fun buildPlayerRoster(playerCount: Int): List<Player> {
        val names = listOf(
            "ColdBlood", "Hunter", "PixelKnight", "StormRider", "CryptoWolf",
            "BlazePeak", "VoidHunter", "IronClad", "ThunderStrike", "SilverArrow",
            "DarkMatter", "NovaPulse", "FrostByte", "QuantumLeap", "GhostProtocol",
            "InfernoKing", "CyberHawk", "ArcLight", "SteelPhoenix", "OmegaForce"
        )
        val playerList = (0 until playerCount).map { index ->
            val name = names.getOrElse(index) { "Player_${index + 1}" }
            Player(
                id = "player_$index",
                userName = name,
                score = 0L,
            )
        }
        return playerList
    }

    override fun start() {
        if (engineJob != null) return
        engineJob = engineScope.launch {
            val playerList = _players.value
            playerList.forEach { player ->
                launch {
                    runPlayerScoreLoop(player)
                }
            }
        }
    }

    private suspend fun runPlayerScoreLoop(player: Player) {
        val random = Random(player.id.hashCode().toLong())
        while (true) {
            val delay = random.nextLong(minIntervalMs, maxIntervalMs)
            delay(delay)
            val delta = random.nextLong(MIN_SCORE_DELTA, MAX_SCORE_DELTA)
            val newScore = synchronized(currentScores) {
                val updated = (currentScores[player.id] ?: 0L) + delta
                currentScores[player.id] = updated
                updated
            }
            val scoreUpdate = ScoreUpdate(
                playerId = player.id,
                newScore = newScore,
                delta = delta
            )
            _scoreUpdate.emit(
                scoreUpdate
            )
            println("New Score $scoreUpdate")
        }
    }

    override suspend fun stop() {
        engineJob?.cancelAndJoin()
        engineJob = null
    }
}