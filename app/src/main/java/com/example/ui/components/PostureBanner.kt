package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.posedetection.model.PostureFeedback

@Composable
fun PostureBanner(
    feedback: PostureFeedback,
    isFormValid: Boolean,
    modifier: Modifier = Modifier
) {
    val targetBgColor = when {
        feedback.isError || !isFormValid -> MaterialTheme.colorScheme.error.copy(alpha = 0.9f)
        feedback == PostureFeedback.GOOD_FORM -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.9f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
    }

    val animatedBg by animateColorAsState(
        targetValue = targetBgColor,
        animationSpec = tween(durationMillis = 250),
        label = "posture_banner_bg"
    )

    val contentColor = when {
        feedback.isError || !isFormValid -> Color.White
        feedback == PostureFeedback.GOOD_FORM -> Color.White
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(animatedBg)
            .border(
                1.dp,
                contentColor.copy(alpha = 0.25f),
                RoundedCornerShape(24.dp)
            )
            .padding(horizontal = 18.dp, vertical = 10.dp)
            .testTag("posture_banner"),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (feedback.isError || !isFormValid) Icons.Default.Warning else Icons.Default.CheckCircle,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(20.dp)
            )

            Spacer(modifier = Modifier.width(10.dp))

            Text(
                text = feedback.message,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = contentColor
            )
        }
    }
}
