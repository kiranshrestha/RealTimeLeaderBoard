# Real-Time Leaderboard — Lead Android Developer Assignment


---

## Module Responsibilities

### Module 1 — Score Engine (`scoreengine/`)

| Class | Role |
|---|---|
| `Player` | Immutable data model for a game participant |
| `ScoreUpdate` | Event emitted per score change (playerId, newScore, delta, timestamp) |
| `ScoreEngineRepository` | Interface contract — what the engine promises |
| `ScoreEngineRepositoryImpl` | Concrete engine: spawns one coroutine per player, emits random score events at 500ms–2000ms intervals |

**Key guarantees:**
- Scores only increase (`newScore > currentScore` enforced before emission)
- Each player's random seed is derived from their ID → **deterministic per session**
- Engine runs entirely on `Dispatchers.IO`, never touches main thread
- `MutableSharedFlow(extraBufferCapacity = 64)` prevents back-pressure drops

### Module 2 — Leaderboard (`leaderboard/`)

| Class | Role |
|---|---|
| `LeaderboardEntry` | Enriched display model: rank, previousRank, scoreDelta, rankDelta |
| `LeaderboardState` | Sealed class: `Loading` / `Active(entries)` / `Error` |
| `RankingEngine` | **Pure domain object** — all ranking math lives here |
| `LeaderboardRepository` | Interface: exposes `StateFlow<LeaderboardState>` |
| `LeaderboardRepositoryImpl` | Consumes engine events, applies `RankingEngine`, maintains state |
| `ObserveLeaderboardUseCase` | Thin use-case wrapping the repository flow |

**Key guarantee:** This module **never generates scores**. It is purely reactive.

---

## Architecture Overview

```
                       UI Layer                             
  LeaderboardScreen (Compose)  ←  LeaderboardViewModel       
         collectAsStateWithLifecycle()      
                 
                          │ StateFlow<LeaderboardState>
                          
                    Domain Layer                             
            ObserveLeaderboardUseCase                                  
  RankingEngine  (pure Kotlin, zero Android deps)   
          
                          │
                          
 Score Engine            Leaderboard Repository       
 Module                  (Consumer)                   
                                                      
 ScoreEngine         LeaderboardRepositoryImpl         
 RepositoryImpl       .collect(scoreUpdates)           
                      .applyUpdate(RankingEngine)      
 Flow<ScoreUpdate>   → StateFlow<LeaderboardState>      
                                 
```

**Pattern:** Clean Architecture with MVVM presentation layer.

**Why MVVM over MVI here?** MVI adds value when you have complex intent mapping and side-effect management (e.g., one-shot events, navigation). For a leaderboard that is purely observational (no user actions beyond viewing), MVVM + StateFlow is simpler, equally testable, and avoids unnecessary ceremony. I'd adopt MVI if we added betting, reactions, or chat to this screen.

---

## Part 4 — Performance & Lifecycle

### Blocking the UI Thread
- The score engine runs on `Dispatchers.IO`
- Ranking computation runs on `Dispatchers.Default` (CPU-bound, never IO)
- The ViewModel only calls `collectAsStateWithLifecycle()` — zero blocking

### Unnecessary Recompositions
- Each `LeaderboardRow` uses the player's **stable `playerId`** as the `key` in `LazyColumn`
- Only the row whose score changed has state mutations (`isFlashing`, `scale.animateTo`)
- Unchanged rows are **skipped entirely by the Compose runtime**
- `AnimatedContent` uses `targetState = entry.score` so it only triggers on actual score changes

### Memory Leaks
- `ScoreEngineRepositoryImpl` and `LeaderboardRepositoryImpl` each own a `CoroutineScope(SupervisorJob() + dispatcher)`
- `ViewModel.onCleared()` calls `stop()` / `stopObserving()` → scopes are cancelled, coroutines cleaned up
- `SharingStarted.WhileSubscribed(5_000)` in the ViewModel means the upstream collection pauses 5 seconds after all UI collectors leave (e.g., HOME press), and resumes on return

### Screen Rotation
- `LeaderboardViewModel` is scoped to the Activity by Hilt — it **survives rotation**
- The `StateFlow` in the repositories is also a singleton — same instance, same state, no re-fetch
- The Compose UI simply re-collects the existing StateFlow; users see zero loading flicker

### App Goes to Background
- `collectAsStateWithLifecycle()` (from `lifecycle-runtime-compose`) automatically **pauses collection** when the lifecycle drops below `STARTED`
- The engine and leaderboard repo continue running (scores accumulate in the background)
- When the app returns to foreground, the UI instantly reflects the current state (StateFlow replay = 1)
- With `WhileSubscribed(5_000)`: if the user is in the background > 5 seconds, the ViewModel stops its StateFlow collection. The engine itself continues (it's singleton-scoped), so no score events are lost — they buffer in `SharedFlow(extraBufferCapacity = 64)`

### Scaling

**1K users:**  
Current design handles this comfortably. One coroutine per player, O(n log n) sort per update. 1,000 players = ~1,000 concurrent coroutines (Kotlin coroutines are extremely lightweight, ~1KB each). The sort on each update is ~10ms worst case. No UI issue since `LazyColumn` virtualizes the list.

**100K users:**  
Direct architecture needs changes:
- **Engine side:** Move score events to a **WebSocket / gRPC stream** from a real server. The device shouldn't simulate 100K players.
- **Leaderboard side:** Switch from full re-sort on every event to an **indexed sorted structure** (e.g., TreeMap or a sorted skip list) for O(log n) rank updates. Only send **top-N** (e.g., top 100) to the UI; paginate the rest.

---

## Part 5 — Leadership & Ownership

### Why Split Modules This Way?

The split mirrors the **bounded contexts** of the system:

- **Score Engine** = the game backend / match engine concern. It answers: *"What happened in the game?"*
- **Leaderboard** = the display/ranking concern. It answers: *"Who is winning right now?"*

This means:
1. Either module can be replaced independently (e.g., swap fake engine for a real WebSocket engine without touching leaderboard code)
2. Each module can be tested in isolation without mocking the other
3. A backend team could eventually own the engine contract; a frontend team owns the leaderboard consumer

### Where Does Ranking Logic Live and Why?

`RankingEngine` is a **pure Kotlin object in the domain layer** — no ViewModel, no Repository, no Android imports.

Why not in ViewModel? The ViewModel is presentation logic. If we add a second consumer of the leaderboard (e.g., a widget, a notification), they'd each need to replicate ranking logic. Centralizing it in the domain prevents that.

Why not in Repository? The repository manages state and data flow. Embedding ranking logic there couples business rules to infrastructure — harder to test, harder to evolve.

### Trade-offs Consciously Made

| Decision | Trade-off |
|---|---|
| `SharedFlow` over `Channel` for score events | SharedFlow is multicast (multiple collectors) at the cost of potentially losing events if the buffer overflows. Chosen because leaderboard + potential analytics could both subscribe. |
| One coroutine per player in engine | Simpler code, easier to test. At 1K players it's fine.  |
| Singleton repos via Hilt | ViewModel survives rotation without re-fetching. Trade-off: the engine never resets during a session (intentional for a "game session" model). |
| Full re-sort on every update | O(n log n) per event. Correct and simple. Acceptable for ≤1K players. For 100K, switch to indexed data structure. |
| `animateItem()` for rank movement | Compose handles the animation automatically using item keys. Trade-off: less control over the animation curve vs. manual `Animatable` per item. |
| Dispatchers.Default for ranking | CPU-bound work, not IO. Keeps IO dispatcher threads free for actual IO. |

---



## 7-Day Ship Plan

### Non-Negotiable (Days 1–4)
- ✅ Score engine with correct monotonic scores
- ✅ Ranking logic with tie-breaking (the business rule that matters most)
- ✅ Live leaderboard screen with real-time updates
- ✅ Lifecycle safety (no leaks, rotation survival)
- ✅ Unit tests for ranking logic

### Cut / Defer (Days 5–7)
- Score animation and rank movement polish → defer to v1.1
- Anti-cheat measures → defer (document the gap)
- 100K scale optimizations → defer (design is ready, optimize when needed)

### Work Division

| Task | Owner | Why |
|---|---|---|
| Score engine (`ScoreEngineRepositoryImpl`) | Mid-level | Well-defined scope, no architectural decisions |
| Ranking engine + unit tests | Mid-level | Requires careful business logic + test thinking |
| Leaderboard repository + DI wiring | Lead | Architecture boundary decisions live here |
| ViewModel + Compose screen | Mid-level | Standard MVVM + Compose, good growth opportunity |
| Animation polish | Junior | Self-contained, bounded risk |
| Code review, PR sign-off, README | Lead | Ownership of quality bar |

---


## What I'd Improve with More Time

1. **Header animation Fix** - Proper header animation on scroll could be fixed.
2. **Proper avatar images** - Coil + DiceBear API seeded by `avatarSeed`
3. **Offline state** - show cached last-known leaderboard with a "Reconnecting…" banner
