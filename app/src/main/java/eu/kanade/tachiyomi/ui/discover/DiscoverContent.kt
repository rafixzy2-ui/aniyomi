package eu.kanade.tachiyomi.ui.discover

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.compose.AsyncImage
import eu.kanade.tachiyomi.ui.entries.anime.AnimeScreen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface LookupState {
    data class Loading(val title: String, val posterUrl: String?) : LookupState
    data class NotFound(val title: String, val posterUrl: String?) : LookupState
}

@Composable
fun OpenOverlay(
    lookupState: LookupState?,
    onDismiss: () -> Unit,
) {
    AnimatedVisibility(
        visible = lookupState != null,
        enter = fadeIn(animationSpec = tween(200)),
        exit = fadeOut(animationSpec = tween(150)),
    ) {
        if (lookupState == null) return@AnimatedVisibility

        BackHandler(onBack = onDismiss)

        val posterUrl = when (lookupState) {
            is LookupState.Loading -> lookupState.posterUrl
            is LookupState.NotFound -> lookupState.posterUrl
        }
        val title = when (lookupState) {
            is LookupState.Loading -> lookupState.title
            is LookupState.NotFound -> lookupState.title
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(enabled = false) {}
        ) {
            if (posterUrl != null) {
                AsyncImage(
                    model = posterUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    alpha = 0.35f,
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to Color.Transparent,
                                0.5f to MaterialTheme.colorScheme.background.copy(alpha = 0.5f),
                                1.0f to MaterialTheme.colorScheme.background
                            )
                        )
                    )
            )

            // Close button at top-left
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .padding(16.dp)
                    .align(Alignment.TopStart)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "Close",
                    tint = Color.White
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Poster Card
                    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                    val scale by infiniteTransition.animateFloat(
                        initialValue = 0.98f,
                        targetValue = 1.02f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1000),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "scalePulse"
                    )

                    Box(
                        modifier = Modifier
                            .size(width = 160.dp, height = 240.dp)
                            .scale(if (lookupState is LookupState.Loading) scale else 1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        if (posterUrl != null) {
                            AsyncImage(
                                model = posterUrl,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp,
                            color = Color.White
                        ),
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(280.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    when (lookupState) {
                        is LookupState.Loading -> {
                            var statusMessageIndex by remember { mutableStateOf(0) }
                            LaunchedEffect(Unit) {
                                while (true) {
                                    delay(1200)
                                    statusMessageIndex = (statusMessageIndex + 1) % 2
                                }
                            }

                            Text(
                                text = if (statusMessageIndex == 0) "Finding sources..." else "Checking episodes...",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = Color.White.copy(alpha = 0.8f)
                                )
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            LinearProgressIndicator(
                                modifier = Modifier
                                    .width(200.dp)
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp)),
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        is LookupState.NotFound -> {
                            Text(
                                text = "No playable source found",
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                ),
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "None of your installed sources have this title.",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color.White.copy(alpha = 0.7f)
                                ),
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            OutlinedButton(
                                onClick = onDismiss,
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = Color.White
                                ),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.5f))
                            ) {
                                Text("Close")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DiscoverContent(
    state: DiscoverScreenModel.State,
    onRefresh: () -> Unit,
) {
    val navigator = LocalNavigator.currentOrThrow
    val coroutineScope = rememberCoroutineScope()
    val resolver = remember { DiscoverResolver() }

    var lookupState by remember { mutableStateOf<LookupState?>(null) }
    var currentLookupJob by remember { mutableStateOf<Job?>(null) }

    fun openAnime(titles: List<String>, fallbackTitle: String, posterUrl: String?) {
        currentLookupJob?.cancel()
        lookupState = LookupState.Loading(fallbackTitle, posterUrl)
        currentLookupJob = coroutineScope.launch {
            try {
                val animeId = resolver.resolveAnime(titles)
                if (animeId != null) {
                    lookupState = null
                    withContext(Dispatchers.Main) {
                        navigator.push(AnimeScreen(animeId))
                    }
                } else {
                    lookupState = LookupState.NotFound(fallbackTitle, posterUrl)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                lookupState = LookupState.NotFound(fallbackTitle, posterUrl)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize()
        ) {
            // Hero Pager
            if (state.heroItems.isNotEmpty()) {
                item {
                    val pagerState = rememberPagerState(pageCount = { state.heroItems.size })
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(420.dp)
                    ) {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize()
                        ) { page ->
                            val item = state.heroItems[page]
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clickable {
                                        val titles = listOfNotNull(item.title, item.englishTitle, item.romajiTitle)
                                        openAnime(titles, item.title, item.bannerUrl ?: item.coverUrl)
                                    }
                            ) {
                                AsyncImage(
                                    model = item.bannerUrl ?: item.coverUrl,
                                    contentDescription = item.title,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.verticalGradient(
                                                colorStops = arrayOf(
                                                    0.0f to Color.Transparent,
                                                    0.6f to Color.Black.copy(alpha = 0.3f),
                                                    1.0f to MaterialTheme.colorScheme.background
                                                )
                                            )
                                        )
                                )
                                Column(
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(20.dp)
                                ) {
                                    Text(
                                        text = item.title,
                                        style = MaterialTheme.typography.headlineMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        ),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(
                                        onClick = {
                                            val titles = listOfNotNull(item.title, item.englishTitle, item.romajiTitle)
                                            openAnime(titles, item.title, item.bannerUrl ?: item.coverUrl)
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary
                                        )
                                    ) {
                                        Text("Watch Now")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Rows
            items(state.rows) { row ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                ) {
                    Text(
                        text = row.title,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        ),
                        modifier = Modifier.padding(start = 16.dp, bottom = 12.dp)
                    )

                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp)
                    ) {
                        items(row.items) { item ->
                            val isTrendingRow = row.title.equals("Trending", ignoreCase = true)
                            val rank = if (isTrendingRow) row.items.indexOf(item) + 1 else null

                            val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                            val isPressed by interactionSource.collectIsPressedAsState()
                            val scale by animateFloatAsState(
                                targetValue = if (isPressed) 0.96f else 1f,
                                label = "cardScale"
                            )

                            Column(
                                modifier = Modifier
                                    .width(140.dp)
                                    .scale(scale)
                                    .clickable(
                                        interactionSource = interactionSource,
                                        indication = null
                                    ) {
                                        val titles = listOfNotNull(item.title, item.englishTitle, item.romajiTitle)
                                        openAnime(titles, item.title, item.coverUrl)
                                    }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(140.dp)
                                        .aspectRatio(2f / 3f)
                                        .clip(RoundedCornerShape(14.dp))
                                        .border(
                                            BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                                            RoundedCornerShape(14.dp)
                                        )
                                ) {
                                    AsyncImage(
                                        model = item.coverUrl,
                                        contentDescription = item.title,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )

                                    // Gradient overlay on bottom
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(60.dp)
                                            .align(Alignment.BottomCenter)
                                            .background(
                                                Brush.verticalGradient(
                                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))
                                                )
                                            )
                                    )

                                    // Score pill (bottom-left)
                                    if (item.score != null && item.score > 0) {
                                        Surface(
                                            modifier = Modifier
                                                .align(Alignment.BottomStart)
                                                .padding(8.dp),
                                            shape = RoundedCornerShape(8.dp),
                                            color = Color.Black.copy(alpha = 0.65f)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.Star,
                                                    contentDescription = null,
                                                    tint = Color(0xFFFFC107),
                                                    modifier = Modifier.size(11.dp)
                                                )
                                                Text(
                                                    text = String.format("%.1f", item.score),
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 11.sp,
                                                        color = Color.White
                                                    )
                                                )
                                            }
                                        }
                                    }

                                    // Rank pill for Trending (top-left)
                                    if (rank != null) {
                                        Surface(
                                            modifier = Modifier
                                                .align(Alignment.TopStart)
                                                .padding(8.dp),
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
                                        ) {
                                            Text(
                                                text = "#$rank Today",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 11.sp,
                                                    color = Color.White
                                                ),
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onBackground
                                    ),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )

                                val formatYearText = listOfNotNull(
                                    item.format?.let { if (it.isNotBlank()) it.uppercase() else null },
                                    item.year?.let { if (it > 0) it.toString() else null }
                                ).joinToString(" ")

                                if (formatYearText.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = formatYearText,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }

        OpenOverlay(
            lookupState = lookupState,
            onDismiss = {
                currentLookupJob?.cancel()
                currentLookupJob = null
                lookupState = null
            }
        )
    }
}
