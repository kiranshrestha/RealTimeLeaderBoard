package com.example.realtimeleaderboard.di

import com.example.realtimeleaderboard.leaderboard.repository.LeaderboardRepository
import com.example.realtimeleaderboard.leaderboard.repository.LeaderboardRepositoryImpl
import com.example.realtimeleaderboard.score_engine.repository.ScoreEngineRepository
import com.example.realtimeleaderboard.score_engine.repository.ScoreEngineRepositoryImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideScoreEngineRepository(): ScoreEngineRepository {
        return ScoreEngineRepositoryImpl(
            dispatcher = Dispatchers.IO,
            playerCount = 20,
            minIntervalMs = 500L,
            maxIntervalMs = 2000L
        )
    }

    @Provides
    @Singleton
    fun provideLeaderboardRepository(
        scoreEngineRepository: ScoreEngineRepository,
    ): LeaderboardRepository {
        return LeaderboardRepositoryImpl(
            scoreEngine = scoreEngineRepository,
            dispatcher = Dispatchers.Default, //CPU intensive task.
        )
    }

}