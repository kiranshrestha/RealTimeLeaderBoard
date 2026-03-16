package com.example.realtimeleaderboard.leaderboard.model


data class LeaderboardEntry(
    val playerId:String,
    val userName: String,
    val score:Long,
    val rank:Int,
    val previousRank:Int = rank,
    val scoreDelta:Long = 0,
){
    val rankDelta : Int get() = previousRank - rank

    val movedUp: Boolean get() = rankDelta > 0
    val movedDown: Boolean get() = rankDelta < 0
    val didScore: Boolean get() = scoreDelta != 0L

}
