package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SkeletonListLoader() {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1000
                0.2f at 0
                0.6f at 500
                0.2f at 1000
            },
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        repeat(6) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Circle element for thumbnail
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .background(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                            shape = RoundedCornerShape(12.dp)
                        )
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    // Line for Title
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .height(16.dp)
                            .background(
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                                shape = RoundedCornerShape(8.dp)
                            )
                    )
                    // Line for Subtitle
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.4f)
                            .height(12.dp)
                            .background(
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                                shape = RoundedCornerShape(6.dp)
                            )
                    )
                }

                // Small square for action
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                            shape = RoundedCornerShape(4.dp)
                        )
                )
            }
        }
    }
}
