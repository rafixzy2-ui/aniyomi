package eu.kanade.tachiyomi.ui.discover

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.compose.AsyncImage
import eu.kanade.tachiyomi.ui.browse.anime.source.globalsearch.GlobalAnimeSearchScreen
import kotlinx.coroutines.delay

@Composable
fun DiscoverContent(screenModel: DiscoverScreenModel) {
    val state by screenModel.state.collectAsState()
    val navigator = LocalNavigator.currentOrThrow

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
            val trendingItems = current.items
            val topRatedItems = current.items.sortedByDescending { it.score ?: 0 }

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                if (current.items.isNotEmpty()) {
                    item(key = "hero") {
                        val heroItems = current.items.take(5)
                        val pagerState = rememberPagerState(pageCount = { heroItems.size })

                        LaunchedEffect(pagerState) {
                            while (true) {
                                delay(5000)
                                val nextPage = (pagerState.currentPage + 1) % heroItems.size
                                pagerState.animateScrollToPage(nextPage)
                            }
                        }

                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp)
                        ) { page ->
                            val heroItem = heroItems[page]
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clickable {
                                        navigator.push(GlobalAnimeSearchScreen(searchQuery = heroItem.title))
                                    }
                            ) {
                                if (heroItem.coverUrl != null) {
                                    AsyncImage(
                                        model = heroItem.coverUrl,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop,
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)),
                                                startY = 0f,
                                                endY = Float.POSITIVE_INFINITY
                                            )
                                        )
                                )
                                Column(
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(16.dp)
                                ) {
                                    Text(
                                        text = heroItem.title,
                                        style = MaterialTheme.typography.titleLarge,
                                        color = Color.White,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(
                                        onClick = {
                                            navigator.push(GlobalAnimeSearchScreen(searchQuery = heroItem.title))
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color.White,
                                            contentColor = Color.Black
                                        )
                                    ) {
                                        Text(text = "View Details")
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
                                Column(
                                    modifier = Modifier
                                        .width(120.dp)
                                        .clickable {
                                            navigator.push(GlobalAnimeSearchScreen(searchQuery = item.title))
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
                                Column(
                                    modifier = Modifier
                                        .width(120.dp)
                                        .clickable {
                                            navigator.push(GlobalAnimeSearchScreen(searchQuery = item.title))
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
    }
}
