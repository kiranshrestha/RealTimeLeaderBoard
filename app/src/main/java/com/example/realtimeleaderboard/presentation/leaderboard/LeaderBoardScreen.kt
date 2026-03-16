package com.example.realtimeleaderboard.presentation.leaderboard


import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.realtimeleaderboard.R
import com.example.realtimeleaderboard.leaderboard.model.LeaderboardEntry
import com.example.realtimeleaderboard.leaderboard.model.LeaderboardState
import com.example.realtimeleaderboard.presentation.theme.AccentNeon
import com.example.realtimeleaderboard.presentation.theme.BgDark
import com.example.realtimeleaderboard.presentation.theme.GoldTrophy
import com.example.realtimeleaderboard.presentation.theme.OrangeBright
import com.example.realtimeleaderboard.presentation.theme.OrangeDeep
import com.example.realtimeleaderboard.presentation.theme.OrangeMid
import com.example.realtimeleaderboard.presentation.theme.RankDownColor
import com.example.realtimeleaderboard.presentation.theme.RankSameColor
import com.example.realtimeleaderboard.presentation.theme.RankUpColor
import com.example.realtimeleaderboard.presentation.theme.TextPrimary
import com.example.realtimeleaderboard.presentation.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlin.math.min




// ─── Constants ───────────────────────────────────────────────────────────────
private val HEADER_EXPANDED_HEIGHT = 320.dp
private val HEADER_COLLAPSED_HEIGHT = 100.dp   // toolbar height

// ─── Main composable ─────────────────────────────────────────────────────────
@Composable
fun LeaderboardScreen(
    viewmodel: LeaderBoardViewModel = hiltViewModel(),
    seasonName: String = "GENESIS SEASON",
    seasonEndsIn: String = "60 days",
    currentRank: Int = 718,
    currentScore: Long = 2100,
) {
    val listState = rememberLazyListState()
    val uiState by viewmodel.uiState.collectAsStateWithLifecycle()

    // collapseProgress: 0f = fully expanded, 1f = fully collapsed
    val collapseProgress by remember {
        derivedStateOf {
            val firstVisible = listState.firstVisibleItemIndex
            val scrollOffset = listState.firstVisibleItemScrollOffset
            if (firstVisible > 0) {
                1f
            } else {
                // Normalize scroll offset over ~200dp worth of pixels (~600px on mdpi)
                min(1f, scrollOffset / 600f)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(OrangeMid, BgDark),
                    startY = 0f,
                    endY = 1200f
                )
            )
    ) {

        when (val state = uiState) {
            is LeaderboardState.Active -> LeaderboardContent(
                listState = listState,
                entries = state.entries,
                collapseProgress = collapseProgress,
                seasonName = seasonName,
                seasonEndsIn = seasonEndsIn,
                currentRank = currentRank,
                currentScore = currentScore
            )

            is LeaderboardState.Error -> ErrorContent(message = state.message)
            is LeaderboardState.Loading -> LoadingContent()
        }


    }
}

// ─── Collapsing header ────────────────────────────────────────────────────────
@Composable
fun CollapsingHeader(
    collapseProgress: Float,
    seasonName: String,
    seasonEndsIn: String,
    currentRank: Int,
    currentScore: Long,
) {
    val animProgress by animateFloatAsState(
        targetValue = collapseProgress,
        animationSpec = tween(durationMillis = 50),
        label = "collapseProgress"
    )

    // Derived animation values
    val headerHeight: Dp = lerp(HEADER_EXPANDED_HEIGHT, HEADER_COLLAPSED_HEIGHT, animProgress)

    // Badge size: 140dp → 40dp
    val badgeSize: Dp = lerp(140.dp, 10.dp, animProgress)

    // "LEGENDS" watermark alpha: 1f → 0f (fades out as we collapse)
    val legendsWatermarkAlpha: Float = (1f - animProgress * 1.5f).coerceIn(0f, 1f)

    // Season subtitle alpha: 1f → 0f
    val subtitleAlpha: Float = (1f - animProgress * 2f).coerceIn(0f, 1f)

    // Badge vertical offset: when expanded it sits in the center of expanded area;
    // when collapsed it moves up to align with the toolbar row
    val badgeTopPadding: Dp = lerp(100.dp, 40.dp, animProgress)

    // Badge horizontal alignment: center → start
    // We fake this by offsetting from center
    val badgeHorizontalOffset: Dp = lerp(0.dp, (-200).dp, animProgress)

    // Pill row moves up with badge
    val pillTopPadding: Dp = lerp(280.dp, 80.dp, animProgress)
    val pillHorizontalOffset: Dp = lerp(0.dp, (-32).dp, animProgress)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(headerHeight)
            .background(
                Brush.verticalGradient(
                    colors = listOf(OrangeMid, OrangeMid.copy(alpha = 0.95f)),
                )
            )
    ) {
        // ── "LEGENDS" large watermark text (only visible when expanded) ──────
        Text(
            text = "LEGENDS",
            fontSize = 72.sp,
            fontWeight = FontWeight.Black,
            color = OrangeDeep.copy(alpha = 0.4f * legendsWatermarkAlpha),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 0.dp, top = 0.dp)
                .alpha(legendsWatermarkAlpha)
        )

        // ── Top toolbar row ─────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .statusBarsPadding(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = "Back",
                tint = TextPrimary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = seasonName,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
            )
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = "Expand",
                tint = TextPrimary,
            )
        }

        // ── Season subtitle ──────────────────────────────────────────────────
        Text(
            text = "Season ends in ",
            color = TextSecondary,
            fontSize = 13.sp,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 46.dp, top = 52.dp)
                .alpha(subtitleAlpha)
        )

        // ── Badge (scales & moves) ───────────────────────────────────────────
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = badgeTopPadding)
                .offset(x = badgeHorizontalOffset)
                .size(badgeSize)
        ) {
            LeagueBadgeIcon(size = badgeSize)
        }

        // ── Rank + Score pills (move with badge) ─────────────────────────────
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = pillTopPadding)
                .offset(x = pillHorizontalOffset)
        ) {
            RankPill(rank = currentRank)
            Spacer(modifier = Modifier.width(8.dp))
            ScorePill(score = currentScore)
        }

        // ── "LEGENDS" label below badge (only expanded) ───────────────────────
        Text(
            text = "LEGENDS",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = OrangeBright,
            letterSpacing = 3.sp,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = lerp(250.dp, 300.dp, animProgress))
                .alpha(subtitleAlpha)
        )
    }
}

// ─── Badge placeholder (replace with your actual Image/drawable) ──────────────
@Composable
fun LeagueBadgeIcon(size: Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(OrangeDeep),
        contentAlignment = Alignment.Center
    ) {
        Image(painter = painterResource(R.drawable.fire_badge), contentDescription = "fire badge")

    }
}

// ─── Pills ────────────────────────────────────────────────────────────────────
@Composable
fun RankPill(rank: Int) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(BgDark.copy(alpha = 0.6f))
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Text(
            text = "${rank}th",
            color = TextPrimary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
        )
    }
}

@Composable
fun ScorePill(score: Long) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(BgDark.copy(alpha = 0.6f))
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🏆", fontSize = 14.sp)
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = score.toString(),
                color = GoldTrophy,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
            )
        }
    }
}

// ─── Leaderboard section header ───────────────────────────────────────────────
@Composable
fun LeaderboardSectionHeader() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = "Leaderboard",
            color = TextPrimary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
        )
        Spacer(modifier = Modifier.width(6.dp))
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = "Info",
            tint = TextSecondary,
            modifier = Modifier.size(16.dp)
        )
    }
}

// ─── Leaderboard row ──────────────────────────────────────────────────────────
@Composable
private fun LeaderboardRow(entry: LeaderboardEntry, modifier : Modifier) {

    var isFlashing by remember(entry.playerId) { mutableStateOf(false) }

    LaunchedEffect(entry.score) {
        if (entry.didScore) {
            isFlashing = true
            delay(600)
            isFlashing = false
        }
    }

    val cardColor by animateColorAsState(
        targetValue = if (isFlashing) AccentNeon.copy(alpha = 0.12f) else Color.Transparent,
        animationSpec = tween(durationMillis = 300),
        label = "cardFlash_${entry.playerId}",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .background(cardColor)
        ,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar placeholder
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(OrangeDeep),
            contentAlignment = Alignment.Center
        ) {
            Text("👤", fontSize = 20.sp)
        }
        Spacer(modifier = Modifier.width(12.dp))

        // Rank + name
        Text(
            text = "${entry.rank}.",
            color = TextSecondary,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            modifier = Modifier.width(28.dp)
        )
        Text(
            text = entry.userName,
            color = TextPrimary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            modifier = Modifier.weight(1f)
        )

        // Rank-up arrow
        RankDeltaIndicator(entry = entry)

        // Trophy + score
        Text("🏆", fontSize = 14.sp)
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = entry.score.toString(),
            color = GoldTrophy,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
        )
    }
}

@Composable
fun LeaderboardContent(
    entries: List<LeaderboardEntry>,
    collapseProgress: Float,
    seasonName: String,
    seasonEndsIn: String,
    currentRank: Int,
    currentScore: Long,
    listState: LazyListState,
) {

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize()
    ) {
        // Spacer that the sticky header overlaps
        item {
            Spacer(modifier = Modifier.height(HEADER_EXPANDED_HEIGHT))
        }

        // "Leaderboard" label
        item {
            LeaderboardSectionHeader()
        }

        // Entries
        items(entries) { entry ->
            LeaderboardRow(
                entry = entry, modifier = Modifier.animateItem(
                    // Built-in item animation handles rank movement smoothly
                    placementSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    )
                )
            )
        }

        // Bottom padding
        item { Spacer(modifier = Modifier.height(32.dp)) }
    }

    // Sticky collapsing header
    CollapsingHeader(
        collapseProgress = collapseProgress,
        seasonName = seasonName,
        seasonEndsIn = seasonEndsIn,
        currentRank = currentRank,
        currentScore = currentScore,
    )

}

@Composable
private fun RankDeltaIndicator(entry: LeaderboardEntry) {
    val (icon, color, label) = when {
        entry.movedUp -> Triple(Icons.Default.KeyboardArrowUp, RankUpColor, "+${entry.rankDelta}")
        entry.movedDown -> Triple(
            Icons.Default.KeyboardArrowDown,
            RankDownColor,
            "${entry.rankDelta}"
        )

        else -> Triple(null, RankSameColor, "—")
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(14.dp)
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
    }
}

@Composable
fun ErrorContent(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "⚠️ $message",
            color = RankDownColor,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}


@Composable
fun LoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = AccentNeon, strokeWidth = 3.dp)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Connecting to game engine…",
                color = TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}