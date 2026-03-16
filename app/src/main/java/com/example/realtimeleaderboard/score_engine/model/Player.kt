package com.example.realtimeleaderboard.score_engine.model

data class Player(
    val id:String,
    val userName: String,
    val score:Long = 0
)
