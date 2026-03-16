package com.example.realtimeleaderboard.leaderboard.repository

import com.example.realtimeleaderboard.leaderboard.model.LeaderboardEntry
import com.example.realtimeleaderboard.score_engine.model.Player
import com.example.realtimeleaderboard.score_engine.model.ScoreUpdate

/**
 * Pure domain object: computes ranked leaderboard entries from raw data.
 *
 * Lives in the domain/repository layer — NOT in ViewModel or UI.
 * This is the heart of the business logic and is 100% unit-testable
 * without any Android dependencies.
 *
 * Ranking rules:
 *  - Sorted by score DESC
 *  - Ties share the same rank
 *  - Ranks skip after a tie group (Olympic-style / dense: 1,1,3 not 1,1,2)
 */
object RankingEngine {

    /**
     * Applies a [ScoreUpdate] to the current score map and returns a new
     * sorted, ranked list of [LeaderboardEntry].
     *
     * @param currentScores Mutable snapshot of playerId → score
     * @param players       Full player roster (for display names / avatar seeds)
     * @param update        The incoming score event
     * @param previousEntries The ranked list from the prior tick (for rank-delta calculation)
     */
    fun applyUpdate(
        currentScores: MutableMap<String, Long>,
        players: Map<String, Player>,
        update: ScoreUpdate,
        previousEntries: List<LeaderboardEntry>,
    ): List<LeaderboardEntry> {
        // Guard: ignore updates that would decrease score
        val current = currentScores[update.playerId] ?: 0L
        if (update.newScore <= current) return previousEntries

        currentScores[update.playerId] = update.newScore

        val previousRankMap = previousEntries.associate { it.playerId to it.rank }

        return rank(currentScores, players, previousRankMap, updatedPlayerId = update.playerId, delta = update.delta)
    }

    /**
     * Builds a fully ranked leaderboard from scratch.
     * Called on initialization with all scores at 0.
     */
    fun buildInitial(
        players: List<Player>,
    ): List<LeaderboardEntry> {
        val scores = players.associate { it.id to 0L }.toMutableMap()
        val playerMap = players.associateBy { it.id }
        return rank(scores, playerMap, emptyMap(), updatedPlayerId = null, delta = 0L)
    }

    /**
     * Core ranking algorithm.
     * Time complexity: O(n log n) for the sort.
     *
     * Rank assignment uses "standard competition ranking" (1224, not 1223):
     * - Group by score
     * - Assign rank = position of first element in group (1-based)
     * - Next group's rank = previous group rank + group size
     */
    private fun rank(
        scores: Map<String, Long>,
        players: Map<String, Player>,
        previousRankMap: Map<String, Int>,
        updatedPlayerId: String?,
        delta: Long,
    ): List<LeaderboardEntry> {
        // Sort by score DESC, then by player ID ASC for stable ordering on ties
        val sorted = scores.entries
            .sortedWith(compareByDescending<Map.Entry<String, Long>> { it.value }
                .thenBy { it.key })

        val entries = mutableListOf<LeaderboardEntry>()
        var currentRank = 1

        var i = 0
        while (i < sorted.size) {
            val groupScore = sorted[i].value
            // Find all players with the same score (tie group)
            var j = i
            while (j < sorted.size && sorted[j].value == groupScore) j++

            // Everyone in [i, j) gets the same rank
            for (k in i until j) {
                val (id, score) = sorted[k]
                val player = players[id] ?: continue
                val prevRank = previousRankMap[id] ?: currentRank
                entries.add(
                    LeaderboardEntry(
                        playerId = id,
                        userName = player.userName,
                        score = score,
                        rank = currentRank,
                        previousRank = prevRank,
                        scoreDelta = if (id == updatedPlayerId) delta else 0L,
                    )
                )
            }
            currentRank += (j - i) // skip ranks for tie group
            i = j
        }
        entries.forEach {
            println(it)
        }
        println()
        return entries
    }
}
