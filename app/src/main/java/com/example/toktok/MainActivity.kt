@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.example.toktok

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

// ---------- Данные ----------

data class Video(
    val id: Int,
    val url: String,
    val author: String,
    val description: String,
    val likes: Int,
)

/** Источник видео. Позже сюда можно подключить реальный источник, интерфейс менять не придётся. */
interface VideoRepository {
    suspend fun feed(): List<Video>
}

class DemoRepository : VideoRepository {
    private val base = "https://storage.googleapis.com/gtv-videos-bucket/sample/"
    override suspend fun feed() = listOf(
        Video(1, base + "ForBiggerBlazes.mp4", "pixel_fan", "Огонь и скорость 🔥", 1240),
        Video(2, base + "ForBiggerEscapes.mp4", "road_trip", "Побег из города", 860),
        Video(3, base + "ForBiggerFun.mp4", "fun_daily", "Просто весело 😄", 2310),
        Video(4, base + "ForBiggerJoyrides.mp4", "drive_mode", "Прокатимся?", 540),
        Video(5, base + "ForBiggerMeltdowns.mp4", "chill_zone", "Всё тает", 975),
    )
}

// ---------- Activity и тема ----------

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TokTokTheme { App(DemoRepository()) }
        }
    }
}

@Composable
fun TokTokTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val scheme = if (Build.VERSION.SDK_INT >= 31) dynamicDarkColorScheme(context) else darkColorScheme()
    MaterialTheme(colorScheme = scheme, content = content)
}

// ---------- Каркас с нижней навигацией (стиль Pixel) ----------

@Composable
fun App(repo: VideoRepository) {
    var tab by remember { mutableIntStateOf(0) }
    var videos by remember { mutableStateOf<List<Video>>(emptyList()) }
    LaunchedEffect(Unit) { videos = repo.feed() }

    val items = listOf(
        "Лента" to Icons.Filled.Home,
        "Поиск" to Icons.Filled.Search,
        "Профиль" to Icons.Filled.Person,
    )

    Scaffold(
        bottomBar = {
            NavigationBar {
                items.forEachIndexed { i, (label, icon) ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label) },
                    )
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                0 -> FeedScreen(videos)
                1 -> SearchScreen(videos)
                else -> ProfileScreen()
            }
        }
    }
}

// ---------- Лента ----------

@Composable
fun FeedScreen(videos: List<Video>) {
    if (videos.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        FeedContent(videos)
    }
}

@Composable
fun FeedContent(videos: List<Video>) {
    val context = LocalContext.current
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
            playWhenReady = true
        }
    }
    val pager = rememberPagerState { videos.size }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val liked = remember { mutableStateMapOf<Int, Boolean>() }

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) player.pause()
            if (event == Lifecycle.Event.ON_START) player.play()
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            player.release()
        }
    }

    LaunchedEffect(pager.settledPage) {
        player.setMediaItem(MediaItem.fromUri(videos[pager.settledPage].url))
        player.prepare()
        player.play()
    }

    VerticalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
        val v = videos[page]
        val isLiked = liked[v.id] == true

        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable { if (player.isPlaying) player.pause() else player.play() }
        ) {
            if (page == pager.settledPage) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            useController = false
                            this.player = player
                        }
                    },
                )
            }

            // затемнение снизу, чтобы текст читался
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(220.dp)
                    .background(
                        Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f)))
                    )
            )

            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(0.75f)
                    .padding(16.dp)
            ) {
                Text(
                    "@${v.author}",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(v.description, color = Color.White, style = MaterialTheme.typography.bodyMedium)
            }

            Column(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalIconButton(onClick = { liked[v.id] = !isLiked }) {
                    Icon(
                        Icons.Filled.Favorite,
                        contentDescription = "Нравится",
                        tint = if (isLiked) MaterialTheme.colorScheme.error else LocalContentColor.current,
                    )
                }
                Text(
                    (v.likes + if (isLiked) 1 else 0).toString(),
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                )
                FilledTonalIconButton(onClick = { }) {
                    Icon(Icons.Filled.Share, contentDescription = "Поделиться")
                }
            }
        }
    }
}

// ---------- Поиск ----------

@Composable
fun SearchScreen(videos: List<Video>) {
    var query by remember { mutableStateOf("") }
    val found = videos.filter {
        query.isBlank() ||
            it.description.contains(query, ignoreCase = true) ||
            it.author.contains(query, ignoreCase = true)
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().padding(16.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Поиск") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            shape = RoundedCornerShape(28.dp),
            singleLine = true,
        )
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(found) { v ->
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("@${v.author}", style = MaterialTheme.typography.titleMedium)
                        Text(v.description, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

// ---------- Профиль ----------

@Composable
fun ProfileScreen() {
    Column(
        Modifier.fillMaxSize().statusBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(96.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Filled.Person,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        Text("Мой профиль", style = MaterialTheme.typography.headlineSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("Подписки", "0")
            StatCard("Подписчики", "0")
            StatCard("Лайки", "0")
        }
    }
}

@Composable
fun StatCard(label: String, value: String) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleLarge)
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}
