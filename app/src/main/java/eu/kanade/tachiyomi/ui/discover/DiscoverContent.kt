package eu.kanade.tachiyomi.ui.discover

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                val scale by infiniteTransition.animateFloat(
                    initialValue = 0.92f,
                    targetValue = 1.0f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(250, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "scale"
                )

                Box(
                    modifier = Modifier
                        .size(width = 160.dp, height = 240.dp)
                        .scale(scale)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    if (posterUrl != null) {
                        AsyncImage(
                            model = posterUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )

                Spacer(modifier = Modifier.height(16.dp))

                when (lookupState) {
                    is LookupState.Loading -> {
                        var statusText by remember { mutableStateOf("Finding sources...") }
                        LaunchedEffect(Unit) {
                            while (true) {
                                delay(1200)
                                statusText = if (statusText == "Finding sources...") "Checking episodes..." else "Finding sources..."
                            }
                        }

                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.8f),
                            textAlign = TextAlign.Center,
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
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "None of your installed sources have this title.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        OutlinedButton(
                            onClick = onDismiss,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(20.dp),
                        ) {
                            Text(text = "Close")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DiscoverContent(screenModel: DiscoverScreenModel) {
    val state by screenModel.state.collectAsState()
    val navigator = LocalNavigator.currentOrThrow
    val scope = rememberCoroutineScope()
    var currentLookupJob by remember { mutableStateOf<Job?>(null) }
    var lookupState by remember { mutableStateOf<LookupState?>(null) }

    val openAnime: (String, String?) -> Unit = { title, posterUrl ->
        currentLookupJob?.cancel()
        lookupState = LookupState.Loading(title = title, posterUrl = posterUrl)
        currentLookupJob = scope.launch {
            try {
                val id = DiscoverResolver.resolveAnime(listOf(title))
                withContext(Dispatchers.Main) {
                    if (id != null) {
                        lookupState = null
                        navigator.push(AnimeScreen(id))
                    } else {
                        lookupState = LookupState.NotFound(title = title, posterUrl = posterUrl)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    lookupState = LookupState.NotFound(title = title, posterUrl = posterUrl)
                }
            } finally {
                if (currentLookupJob == coroutineContext[Job]) {
                    currentLookupJob = null
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (val current = state) {
            is DiscoverScreenModel.State.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            is DiscoverScreenModel.State.Error -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(text = current.message)
                    Button(onClick = { screenModel.load() }) {
                        Text(text = "Retry")
                    }
                }
            }
            is DiscoverScreenModel.State.Success -> {
                val trendingItems = current.data.trending
                val topRatedItems = current.data.trending.sortedByDescending { it.score ?: 0 }
                val popularItems = current.data.popular
                val actionItems = current.data.action
                val romanceItems = current.data.romance
                val fantasyItems = current.data.fantasy

                LaunchedEffect(current.data.trending) {
                    val heroTitles = current.data.trending.take(5).map { it.title }
                    DiscoverResolver.prefetchHero(heroTitles)
                }

                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    if (current.data.trending.isNotEmpty()) {
                        item(key = "hero") {
                            val heroItems = current.data.trending.take(5)
                            val pagerState = rememberPagerState(pageCount = { heroItems.size })

                            LaunchedEffect(pagerState) {
                                while (true) {
                                    delay(5000)
                                    val nextPage = (pagerState.currentPage + 1) % heroItems.size
                                    pagerState.animateScrollToPage(nextPage)
                                }
                            }

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(480.dp)
                                        .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
                                ) {
                                    HorizontalPager(
                                        state = pagerState,
                                        modifier = Modifier.fillMaxSize()
                                    ) { page ->
                                        val heroItem = heroItems[page]
                                        val heroImageUrl = heroItem.heroImageUrl ?: heroItem.coverUrl
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clickable {
                                                    openAnime(heroItem.title, heroImageUrl)
                                                }
                                        ) {
                                            if (heroImageUrl != null) {
                                                AsyncImage(
                                                    model = heroImageUrl,
                                                    contentDescription = null,
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = ContentScale.Crop,
                                                    alignment = Alignment.TopCenter,
                                                )
                                            }

                                            // Dark gradient over the image using colorStops
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(
                                                        Brush.verticalGradient(
                                                            colorStops = arrayOf(
                                                                0.0f to Color.Transparent,
                                                                0.5f to Color.Transparent,
                                                                0.8f to MaterialTheme.colorScheme.background.copy(alpha = 0.7f),
                                                                1.0f to MaterialTheme.colorScheme.background
                                                            )
                                                        )
                                                    )
                                            )

                                            // Content centered at the bottom
                                            Column(
                                                modifier = Modifier
                                                    .align(Alignment.BottomCenter)
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 24.dp)
                                                    .padding(bottom = 24.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                            ) {
                                                Text(
                                                    text = heroItem.title,
                                                    style = MaterialTheme.typography.titleLarge.copy(
                                                        fontSize = 28.sp,
                                                        fontWeight = FontWeight.Bold,
                                                    ),
                                                    color = Color.White,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis,
                                                    textAlign = TextAlign.Center,
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))

                                                val detailsList = mutableListOf<String>()
                                                heroItem.format?.let { detailsList.add(it) }
                                                val genresPart = heroItem.genres.take(2)
                                                if (genresPart.isNotEmpty()) {
                                                    detailsList.add(genresPart.joinToString(", "))
                                                }
                                                heroItem.year?.let { detailsList.add(it.toString()) }
                                                heroItem.score?.let { detailsList.add("★ %.1f".format(it / 10.0)) }

                                                if (detailsList.isNotEmpty()) {
                                                    Text(
                                                        text = detailsList.joinToString(" • "),
                                                        style = MaterialTheme.typography.bodyMedium.copy(
                                                            fontSize = 14.sp
                                                        ),
                                                        color = Color.White.copy(alpha = 0.8f),
                                                        textAlign = TextAlign.Center,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                    )
                                                    Spacer(modifier = Modifier.height(12.dp))
                                                }

                                                Button(
                                                    onClick = { openAnime(heroItem.title, heroImageUrl) },
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(48.dp),
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = Color.White,
                                                        contentColor = Color.Black
                                                    ),
                                                    shape = RoundedCornerShape(24.dp)
                                                ) {
                                                    Text(
                                                        text = "View Details",
                                                        fontWeight = FontWeight.Bold,
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // Page-indicator dots under the button, or at bottom inside the Box
                                    Row(
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .padding(bottom = 8.dp),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        heroItems.forEachIndexed { index, _ ->
                                            val isSelected = pagerState.currentPage == index
                                            if (isSelected) {
                                                Box(
                                                    modifier = Modifier
                                                        .width(24.dp)
                                                        .height(8.dp)
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(Color.White)
                                                )
                                            } else {
                                                Box(
                                                    modifier = Modifier
                                                        .size(8.dp)
                                                        .clip(CircleShape)
                                                        .background(Color.White.copy(alpha = 0.5f))
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item(key = "trending_section") {
                        Column(modifier = Modifier.padding(vertical = 12.dp)) {
                            Text(
                                text = "Trending",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                            ) {
                                items(trendingItems, key = { "trending_${it.id}" }) { item ->
                                    val formatStr = item.format?.let { f -> f.uppercase() }
                                    val yearStr = item.year?.toString()
                                    val subtitle = when {
                                        formatStr != null && yearStr != null -> "$formatStr $yearStr"
                                        formatStr != null -> formatStr
                                        yearStr != null -> yearStr
                                        else -> null
                                    }
                                    val rankText = "#${trendingItems.indexOf(item) + 1} Today"
                                    PremiumPosterCard(
                                        title = item.title,
                                        posterUrl = item.coverUrl,
                                        scoreText = item.score?.let { "%.1f".format(it / 10.0) },
                                        rankText = rankText,
                                        subtitle = subtitle,
                                        onClick = { openAnime(item.title, item.coverUrl) }
                                    )
                                }
                            }
                        }
                    }

                    item(key = "top_rated_section") {
                        Column(modifier = Modifier.padding(vertical = 12.dp)) {
                            Text(
                                text = "Top Rated",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                            ) {
                                items(topRatedItems, key = { "top_${it.id}" }) { item ->
                                    val formatStr = item.format?.let { f -> f.uppercase() }
                                    val yearStr = item.year?.toString()
                                    val subtitle = when {
                                        formatStr != null && yearStr != null -> "$formatStr $yearStr"
                                        formatStr != null -> formatStr
                                        yearStr != null -> yearStr
                                        else -> null
                                    }
                                    PremiumPosterCard(
                                        title = item.title,
                                        posterUrl = item.coverUrl,
                                        scoreText = item.score?.let { "%.1f".format(it / 10.0) },
                                        rankText = null,
                                        subtitle = subtitle,
                                        onClick = { openAnime(item.title, item.coverUrl) }
                                    )
                                }
                            }
                        }
                    }

                    val extraRows = listOf(
                        "Popular" to popularItems,
                        "Action" to actionItems,
                        "Romance" to romanceItems,
                        "Fantasy" to fantasyItems,
                    )
                    extraRows.forEach { (sectionTitle, sectionItems) ->
                        if (sectionItems.isNotEmpty()) {
                            item(key = "section_${sectionTitle.lowercase()}") {
                                Column(modifier = Modifier.padding(vertical = 12.dp)) {
                                    Text(
                                        text = sectionTitle,
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    )
                                    LazyRow(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                                    ) {
                                        items(sectionItems, key = { "${sectionTitle}_${it.id}" }) { item ->
                                            Column(
                                                modifier = Modifier
                                                    .width(120.dp)
                                                    .clickable {
                                                        openAnime(item.title, item.coverUrl)
                                                    }
                                            ) {
                                                if (item.coverUrl != null) {
                                                    AsyncImage(
                                                        model = item.coverUrl,
                                                        contentDescription = null,
                                                        modifier = Modifier
                                                            .width(120.dp)
                                                            .height(170.dp)
                                                            .clip(RoundedCornerShape(8.dp)),
                                                        contentScale = ContentScale.Crop,
                                                    )
                                                } else {
                                                    Box(
                                                        modifier = Modifier
                                                            .width(120.dp)
                                                            .height(170.dp)
                                                            .clip(RoundedCornerShape(8.dp))
                                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                                    )
                                                }
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = item.title,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = item.score?.let { "Score: %.1f".format(it / 10.0) } ?: "Score: -",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.secondary,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item(key = "bottom_spacer") {
                        Spacer(modifier = Modifier.height(96.dp))
                    }
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
