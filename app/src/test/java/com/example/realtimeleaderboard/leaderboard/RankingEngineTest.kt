package com.example.realtimeleaderboard.leaderboard


import com.example.realtimeleaderboard.leaderboard.model.LeaderboardEntry
import com.example.realtimeleaderboard.leaderboard.repository.RankingEngine
import com.example.realtimeleaderboard.score_engine.model.Player
import com.example.realtimeleaderboard.score_engine.model.ScoreUpdate
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [RankingEngine].
 *
 * No Android dependencies — runs on JVM only.
 * Tests are the executable specification of the ranking business rules.
 */
class RankingEngineTest {

    private lateinit var players: List<Player>
    private lateinit var playerMap: Map<String, Player>

    @Before
    fun setUp() {
        players = listOf(
            Player(id = "p1", userName = "Alpha"),
            Player(id = "p2", userName = "Beta"),
            Player(id = "p3", userName = "Gamma"),
            Player(id = "p4", userName = "Delta"),
        )
        playerMap = players.associateBy { it.id }
    }

    // ── buildInitial ──────────────────────────────────────────────────────────

    @Test
    fun `buildInitial assigns rank 1 to all players when scores are zero`() {
        val entries = RankingEngine.buildInitial(players)
        entries.forEach { entry ->
            assertEquals("All tied at 0, expected rank 1 for ${entry.userName}", 1, entry.rank)
        }
    }

    @Test
    fun `buildInitial returns correct number of entries`() {
        val entries = RankingEngine.buildInitial(players)
        assertEquals(players.size, entries.size)
    }

    // ── applyUpdate — basic sorting ───────────────────────────────────────────

    @Test
    fun `highest score gets rank 1`() {
        val scores = mutableMapOf("p1" to 100L, "p2" to 200L, "p3" to 50L, "p4" to 150L)
        val update = ScoreUpdate(playerId = "p2", newScore = 250L, delta = 250L)
        val previous = RankingEngine.buildInitial(players)
        previous.forEach {
            println(it)
        }
        val entries = RankingEngine.applyUpdate(scores, playerMap, update, previous)
        val top = entries.first()
        println("top $top")
        assertEquals("p2", top.playerId)
        assertEquals(1, top.rank)
    }

    @Test
    fun `entries are sorted by score descending`() {
        val scores = mutableMapOf("p1" to 100L, "p2" to 400L, "p3" to 300L, "p4" to 200L)
        val update = ScoreUpdate(playerId = "p2", newScore = 400L, delta = 400L)
        val entries = RankingEngine.applyUpdate(scores, playerMap, update, emptyList())

        val scoreList = entries.map { it.score }
        assertEquals(scoreList.sortedDescending(), scoreList)
    }

    // ── Tie-breaking (Olympic / standard competition ranking) ─────────────────

    @Test
    fun `tied scores share the same rank`() {
        val scores = mutableMapOf("p1" to 500L, "p2" to 400L, "p3" to 300L, "p4" to 100L)
        val update = ScoreUpdate(playerId = "p2", newScore = 500L, delta = 500L)
        val prevList = listOf(
            LeaderboardEntry(
                playerId = "p1",
                userName = "Alpha",
                score = 500,
                rank = 1,
                previousRank = 1,
                scoreDelta = 0
            ),
            LeaderboardEntry(
                playerId = "p2",
                userName = "Beta",
                score = 500,
                rank = 2,
                previousRank = 2,
                scoreDelta = 0
            ),
            LeaderboardEntry(
                playerId = "p3",
                userName = "Gamma",
                score = 300,
                rank = 3,
                previousRank = 3,
                scoreDelta = 0
            ),
            LeaderboardEntry(
                playerId = "p4",
                userName = "Delta",
                score = 100,
                rank = 4,
                previousRank = 4,
                scoreDelta = 100
            )
        )
        val entries = RankingEngine.applyUpdate(scores, playerMap, update, prevList)

        val p1Entry = entries.first { it.playerId == "p1" }
        val p2Entry = entries.first { it.playerId == "p2" }

        assertEquals("Tied players must share rank", p1Entry.rank, p2Entry.rank)
        assertEquals("Tied players should be rank 1", 1, p1Entry.rank)
    }

    @Test
    fun `rank skips after a tie group (1,1,3 not 1,1,2)`() {
        val scores = mutableMapOf("p1" to 500L, "p2" to 0, "p3" to 300L, "p4" to 100L)
        val update = ScoreUpdate(playerId = "p2", newScore = 500L, delta = 500L)
        val initialEntries = RankingEngine.buildInitial(players = players)
        val entries = RankingEngine.applyUpdate(scores, playerMap, update, initialEntries)

        val p3Entry = entries.first { it.playerId == "p3" }

        assertEquals("After a 2-way tie at rank 1, next rank should be 3", 3, p3Entry.rank)
    }

    @Test
    fun `four-way tie assigns rank 1 to all and rank 5 to next (if any)`() {
        val scores = mutableMapOf("p1" to 500L, "p2" to 500L, "p3" to 500L, "p4" to 500L)
        val update = ScoreUpdate(playerId = "p1", newScore = 500L, delta = 500L)
        val entries = RankingEngine.applyUpdate(scores, playerMap, update, emptyList())

        entries.forEach { entry ->
            assertEquals("All tied — all should be rank 1", 1, entry.rank)
        }
    }

    // ── Score monotonicity ────────────────────────────────────────────────────

    @Test
    fun `stale update with lower score is ignored`() {
        val scores = mutableMapOf("p1" to 0L, "p2" to 0L, "p3" to 0L, "p4" to 0L)
        var prevState = RankingEngine.buildInitial(players)
        RankingEngine.applyUpdate(
            scores, playerMap,
            ScoreUpdate("p1", 500L, 500L), prevState
        )
        prevState = RankingEngine.applyUpdate(
            scores, playerMap,
            ScoreUpdate("p2", 300L, 300L), prevState
        )
        prevState = RankingEngine.applyUpdate(
            scores, playerMap,
            ScoreUpdate("p3", 200L, 200L), prevState
        )
        prevState = RankingEngine.applyUpdate(
            scores, playerMap,
            ScoreUpdate("p4", 100L, 100L), prevState
        )

        prevState = RankingEngine.applyUpdate(
            scores, playerMap,
            ScoreUpdate("p1", 500L, 500L), prevState
        )

        // Attempt to apply an update with a LOWER score for p1
        val staleUpdate = ScoreUpdate(playerId = "p1", newScore = 400L, delta = -100L)
        val entries = RankingEngine.applyUpdate(scores, playerMap, staleUpdate, prevState)

        val p1Entry = entries.first { it.playerId == "p1" }
        assertEquals("Score must not decrease", 500L, p1Entry.score)
    }

    // ── Delta tracking ────────────────────────────────────────────────────────

    @Test
    fun `only the updated player has a non-zero scoreDelta`() {
        val scores = mutableMapOf("p1" to 0L, "p2" to 0L, "p3" to 0L, "p4" to 0L)
        val update = ScoreUpdate(playerId = "p3", newScore = 250L, delta = 250L)
        val entries = RankingEngine.applyUpdate(scores, playerMap, update, emptyList())

        val p3Entry = entries.first { it.playerId == "p3" }
        assertEquals(250L, p3Entry.scoreDelta)

        entries.filter { it.playerId != "p3" }.forEach { entry ->
            assertEquals("Non-updated players should have delta 0", 0L, entry.scoreDelta)
        }
    }

    // ── Rank delta ────────────────────────────────────────────────────────────

    @Test
    fun `rankDelta is positive when player moves up`() {
        val scores = mutableMapOf("p1" to 80L, "p2" to 50L, "p3" to 0L, "p4" to 0L)
        val initialEntries = RankingEngine.applyUpdate(
            scores, playerMap, ScoreUpdate("p1", 100L, 100L), emptyList()
        )

        // p2 jumps over p1
        val update = ScoreUpdate(playerId = "p2", newScore = 200L, delta = 150L)
        val newEntries = RankingEngine.applyUpdate(scores, playerMap, update, initialEntries)

        val p2Entry = newEntries.first { it.playerId == "p2" }
        assert(p2Entry.movedUp) { "p2 should have moved up" }
        assert(p2Entry.rankDelta > 0) { "rankDelta should be positive when moving up" }
    }
}
