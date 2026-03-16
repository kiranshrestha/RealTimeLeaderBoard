package com.example.realtimeleaderboard.scoreengine


import app.cash.turbine.test
import com.example.realtimeleaderboard.score_engine.repository.ScoreEngineRepositoryImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ScoreEngineRepositoryTest {

    private val testDispatcher = StandardTestDispatcher()

    @OptIn(ExperimentalCoroutinesApi::class)
    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `players StateFlow emits correct player count on init`() = runTest {
        val engine = ScoreEngineRepositoryImpl(
            dispatcher = testDispatcher,
            playerCount = 5,
        )
        assertEquals(5, engine.players.value.size)
    }

    @Test
    fun `all players have unique IDs`() = runTest {
        val engine = ScoreEngineRepositoryImpl(dispatcher = testDispatcher, playerCount = 10)
        val ids = engine.players.value.map { it.id }
        assertEquals("Player IDs must be unique", ids.distinct().size, ids.size)
    }

    @Test
    fun `all players start with score zero`() = runTest {
        val engine = ScoreEngineRepositoryImpl(dispatcher = testDispatcher, playerCount = 5)
        engine.players.value.forEach { player ->
            assertEquals("Initial score should be 0", 0L, player.score)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `score updates emit after engine starts`() = runTest {
        val engine = ScoreEngineRepositoryImpl(
            dispatcher = testDispatcher,
            playerCount = 3,
            minIntervalMs = 100L,
            maxIntervalMs = 200L,
        )

        engine.scoreUpdates.test {
            engine.start()
            advanceTimeBy(500)
            val update = awaitItem()
            assertTrue("newScore must be > 0", update.newScore > 0)
            assertTrue("delta must be > 0", update.delta > 0)
            cancelAndIgnoreRemainingEvents()
        }

        engine.stop()
    }

    @Test
    fun `score only increases - newScore is always greater than previous`() = runTest {
        val engine = ScoreEngineRepositoryImpl(
            dispatcher = testDispatcher,
            playerCount = 2,
            minIntervalMs = 100L,
            maxIntervalMs = 200L,
        )

        val playerScores = mutableMapOf<String, Long>()

        engine.scoreUpdates.test {
            engine.start()
            repeat(10) {
                advanceTimeBy(300)
                val update = runCatching { awaitItem() }.getOrNull() ?: return@repeat
                val previous = playerScores[update.playerId] ?: 0L
                assertTrue(
                    "Score must only increase: was $previous, got ${update.newScore}",
                    update.newScore > previous
                )
                playerScores[update.playerId] = update.newScore
            }
            cancelAndIgnoreRemainingEvents()
        }

        engine.stop()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `stop cancels score update emissions`() = runTest {
        val engine = ScoreEngineRepositoryImpl(
            dispatcher = testDispatcher,
            playerCount = 2,
            minIntervalMs = 100L,
            maxIntervalMs = 200L,
        )

        engine.start()
        advanceTimeBy(300)
        engine.stop()

        // After stop, no further events should arrive within a long wait
        engine.scoreUpdates.test {
            advanceTimeBy(3000)
            expectNoEvents()
            cancel()
        }
    }
}
