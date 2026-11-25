package eu.kanade.tachiyomi.ui.home

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEachIndexed
import eu.kanade.presentation.util.Tab

@Composable
fun FloatingNavBar(
    tabs: List<Tab>,
    selectedIndex: Int,
    onTabClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .wrapContentWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.08f),
                            Color.Transparent,
                        ),
                    ),
                )
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(50)),
            contentAlignment = Alignment.CenterStart,
        ) {
            val indicatorOffset by animateDpAsState(
                targetValue = (selectedIndex * 64).dp,
                animationSpec = spring(
                    dampingRatio = 0.8f,
                    stiffness = Spring.StiffnessMediumLow,
                ),
                label = "indicatorOffset",
            )

            Box(
                modifier = Modifier
                    .offset { IntOffset(indicatorOffset.roundToPx(), 0) }
                    .padding(4.dp)
                    .size(width = 64.dp, height = 48.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)),
            )

            Row(
                modifier = Modifier
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                tabs.fastForEachIndexed { index, tab ->
                    val selected = index == selectedIndex
                    val interactionSource = remember { MutableInteractionSource() }
                    Box(
                        modifier = Modifier
                            .size(width = 64.dp, height = 48.dp)
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null,
                                onClick = { onTabClick(index) },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        val iconColor = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        CompositionLocalProvider(LocalContentColor provides iconColor) {
                            Icon(
                                painter = tab.options.icon!!,
                                contentDescription = tab.options.title,
                                modifier = Modifier
                                    .size(24.dp)
                                    .graphicsLayer {
                                        // gentle press scale could be added via interaction if desired, but let's keep it clean
                                    },
                            )
                        }
                    }
                }
            }
        }
    }
}
