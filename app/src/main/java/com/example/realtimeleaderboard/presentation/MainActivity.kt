package com.example.realtimeleaderboard.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.example.realtimeleaderboard.presentation.leaderboard.LeaderboardScreen
import com.example.realtimeleaderboard.presentation.theme.RealTimeLeaderBoardTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RealTimeLeaderBoardTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    LeaderboardScreen()
                }
            }
        }
    }
}

