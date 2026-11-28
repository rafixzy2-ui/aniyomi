package eu.kanade.tachiyomi.ui.discover

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage

@Composable
fun PremiumPosterCard(
    title: String,
    posterUrl: String?,
    scoreText: String?,
    rankText: String?,
    subtitle: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(targetValue = if (isPressed) 0.96f else 1.0f, label = "card_scale")

    Column(
        modifier = modifier
            .width(140.dp)
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp)),
        ) {
            AsyncImage(
                model = posterUrl,
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
            )

            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)),
                            startY = 150f,
                        ),
                    ),
            )

            val (cleanedTitle, seasonLabel) = remember(title) {
                val patterns = listOf(
                    Regex("""(?i)(?::\s*|\s+-\s+|\s+)(Season\s+\d+|[0-9]+(?:st|nd|rd|th)\s+Season|Part\s+\d+|Cour\s+\d+|The\s+Final\s+Season|Final\s+Season)(\s+(?:Season\s+\d+|[0-9]+(?:st|nd|rd|th)\s+Season|Part\s+\d+|Cour\s+\d+|The\s+Final\s+Season|Final\s+Season))*$""")
                )
                var bestClean = title.trim()
                var foundLabel: String? = null
                for (pattern in patterns) {
                    val match = pattern.find(bestClean)
                    if (match != null) {
                        foundLabel = match.groupValues[1].trim()
                        val remainder = bestClean.substring(0, match.range.first).trim()
                        if (remainder.length >= 3) {
                            bestClean = remainder
                            break
                        } else {
                            foundLabel = null
                        }
                    }
                }
                if (bestClean.length < 3) {
                    Pair(title.trim(), null)
                } else {
                    Pair(bestClean, foundLabel)
                }
            }

            // Title on poster
            Text(
                text = cleanedTitle.uppercase(),
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                lineHeight = 15.sp,
                letterSpacing = 0.6.sp,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium.copy(
                    shadow = Shadow(
                        color = Color.Black.copy(alpha = 0.6f),
                        offset = Offset(0f, 2f),
                        blurRadius = 6f,
                    ),
                ),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(10.dp)
                    .fillMaxWidth(),
            )

            // Rating badge
            val scoreFloat = scoreText?.toFloatOrNull()
            if (scoreFloat != null) {
                val dotColor = when {
                    scoreFloat >= 8.0f -> Color(0xFF21D07A)
                    scoreFloat >= 6.5f -> Color(0xFFD2D531)
                    else -> Color(0xFFF65605)
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .height(22.dp)
                        .background(Color.Black.copy(alpha = 0.58f), RoundedCornerShape(6.dp))
                        .border(0.5.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 7.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(dotColor, CircleShape)
                        )
                        Text(
                            text = scoreText,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            // Rank badge
            if (rankText != null) {
                val parts = rankText.split(" ", limit = 2)
                val rankNum = parts.getOrNull(0) ?: ""
                val rankLabel = parts.getOrNull(1) ?: ""

                val rankNumColor = when (rankNum) {
                    "#1" -> Color(0xFFFFD54F)
                    "#2" -> Color(0xFFE0E6EA)
                    "#3" -> Color(0xFFE8A06A)
                    else -> Color.White
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .height(22.dp)
                        .background(Color.Black.copy(alpha = 0.58f), RoundedCornerShape(6.dp))
                        .border(0.5.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 7.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = rankNum,
                            color = rankNumColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (rankLabel.isNotEmpty()) {
                            Text(
                                text = rankLabel.uppercase(),
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Medium,
                                letterSpacing = 0.8.sp,
                            )
                        }
                    }
                }
            }
        }

        val displaySubtitle = remember(seasonLabel, subtitle) {
            when {
                seasonLabel != null && subtitle != null -> "$seasonLabel • $subtitle"
                seasonLabel != null -> seasonLabel
                else -> subtitle
            }
        }

        if (displaySubtitle != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = displaySubtitle,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
