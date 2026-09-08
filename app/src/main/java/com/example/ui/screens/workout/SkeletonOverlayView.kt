package com.example.ui.screens.workout

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.posedetection.model.PoseKeypoints
import com.example.posedetection.model.PosePoint
import com.example.ui.theme.PostureGreen
import com.example.ui.theme.PostureRed

@Composable
fun SkeletonOverlayView(
    keypoints: PoseKeypoints?,
    frameWidth: Int,
    frameHeight: Int,
    isFormValid: Boolean,
    isFrontCamera: Boolean,
    elbowAngle: Float,
    backAngle: Float,
    isBothHandsDetected: Boolean = false,
    leftElbowAngle: Float = 0f,
    rightElbowAngle: Float = 0f,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .fillMaxSize()
            .testTag("skeleton_overlay")
    ) {
        if (keypoints == null || frameWidth <= 0 || frameHeight <= 0) return@Canvas

        val canvasW = size.width
        val canvasH = size.height

        // Scaling function mapping camera frame pixels to canvas space
        fun mapPoint(point: PosePoint?): Offset? {
            if (point == null) return null
            val normX = (point.x / frameWidth).coerceIn(0f, 1f)
            val normY = (point.y / frameHeight).coerceIn(0f, 1f)
            val x = if (isFrontCamera) (1f - normX) * canvasW else normX * canvasW
            val y = normY * canvasH
            return Offset(x, y)
        }

        val boneColor = if (isFormValid) PostureGreen else PostureRed
        val armColor = Color(0xFFFF8555)
        val handGreen = Color(0xFF00E676)
        val handAlert = Color(0xFFFF3D00)
        val jointColor = Color.White
        val boneStroke = 7.dp.toPx()
        val jointRadius = 8.dp.toPx()

        val textPaint = Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 36f
            isFakeBoldText = true
            setShadowLayer(8f, 0f, 0f, android.graphics.Color.BLACK)
        }

        val smallTextPaint = Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 26f
            isFakeBoldText = true
            setShadowLayer(6f, 0f, 0f, android.graphics.Color.BLACK)
        }

        // ==============================
        // 1. FRONT VIEW RENDERING (Dual Arms & Hands)
        // ==============================
        if (keypoints.side == com.example.posedetection.model.BodySide.FRONT &&
            keypoints.leftShoulder != null && keypoints.rightShoulder != null
        ) {
            val lShoulder = mapPoint(keypoints.leftShoulder) ?: return@Canvas
            val rShoulder = mapPoint(keypoints.rightShoulder) ?: return@Canvas
            val lElbow = mapPoint(keypoints.leftElbow) ?: return@Canvas
            val rElbow = mapPoint(keypoints.rightElbow) ?: return@Canvas
            val lWrist = mapPoint(keypoints.leftWrist) ?: return@Canvas
            val rWrist = mapPoint(keypoints.rightWrist) ?: return@Canvas
            val lHip = mapPoint(keypoints.leftHip)
            val rHip = mapPoint(keypoints.rightHip)

            // Shoulder Bar
            val shoulderColor = if (keypoints.shoulderTiltDegrees <= 22f) PostureGreen else PostureRed
            drawLine(
                color = shoulderColor,
                start = lShoulder,
                end = rShoulder,
                strokeWidth = boneStroke + 2f,
                cap = StrokeCap.Round
            )

            // Left Arm: Shoulder -> Elbow -> Wrist
            val lArmColor = if (isFormValid) armColor else PostureRed
            drawLine(
                color = lArmColor,
                start = lShoulder,
                end = lElbow,
                strokeWidth = boneStroke,
                cap = StrokeCap.Round
            )
            drawLine(
                color = lArmColor,
                start = lElbow,
                end = lWrist,
                strokeWidth = boneStroke,
                cap = StrokeCap.Round
            )

            // Right Arm: Shoulder -> Elbow -> Wrist
            val rArmColor = if (isFormValid) armColor else PostureRed
            drawLine(
                color = rArmColor,
                start = rShoulder,
                end = rElbow,
                strokeWidth = boneStroke,
                cap = StrokeCap.Round
            )
            drawLine(
                color = rArmColor,
                start = rElbow,
                end = rWrist,
                strokeWidth = boneStroke,
                cap = StrokeCap.Round
            )

            // Torso lines if hips are detected
            if (lHip != null && rHip != null) {
                val torsoColor = Color.White.copy(alpha = 0.4f)
                drawLine(color = torsoColor, start = lShoulder, end = lHip, strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
                drawLine(color = torsoColor, start = rShoulder, end = rHip, strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
                drawLine(color = torsoColor, start = lHip, end = rHip, strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)

                drawCircle(color = jointColor, radius = 6.dp.toPx(), center = lHip)
                drawCircle(color = jointColor, radius = 6.dp.toPx(), center = rHip)
            }

            // Draw Elbow and Shoulder Joints
            val upperJoints = listOf(lShoulder, rShoulder, lElbow, rElbow)
            for (joint in upperJoints) {
                drawCircle(color = jointColor, radius = jointRadius, center = joint)
                drawCircle(color = if (isFormValid) PostureGreen else PostureRed, radius = jointRadius * 0.6f, center = joint)
            }

            // Draw Hand Indicators at Wrists (Distinguishing Detected vs Not Detected)
            val leftHandOk = (keypoints.leftWrist?.confidence ?: 0f) >= 0.35f
            val rightHandOk = (keypoints.rightWrist?.confidence ?: 0f) >= 0.35f

            // Left Hand Target
            val lHandColor = if (leftHandOk) handGreen else handAlert
            drawCircle(color = lHandColor.copy(alpha = 0.25f), radius = 24.dp.toPx(), center = lWrist)
            drawCircle(color = lHandColor, radius = 14.dp.toPx(), center = lWrist)
            drawCircle(color = Color.White, radius = 7.dp.toPx(), center = lWrist)

            // Right Hand Target
            val rHandColor = if (rightHandOk) handGreen else handAlert
            drawCircle(color = rHandColor.copy(alpha = 0.25f), radius = 24.dp.toPx(), center = rWrist)
            drawCircle(color = rHandColor, radius = 14.dp.toPx(), center = rWrist)
            drawCircle(color = Color.White, radius = 7.dp.toPx(), center = rWrist)

            // Hand Status Label over wrists
            drawContext.canvas.nativeCanvas.drawText(
                if (leftHandOk) "HAND" else "NO HAND",
                lWrist.x - 36f,
                lWrist.y + 40f,
                smallTextPaint
            )

            drawContext.canvas.nativeCanvas.drawText(
                if (rightHandOk) "HAND" else "NO HAND",
                rWrist.x - 36f,
                rWrist.y + 40f,
                smallTextPaint
            )

            // Live Angle Readouts on both elbows
            val lAngle = if (leftElbowAngle > 0f) leftElbowAngle else elbowAngle
            val rAngle = if (rightElbowAngle > 0f) rightElbowAngle else elbowAngle

            drawContext.canvas.nativeCanvas.drawText(
                "L: ${lAngle.toInt()}°",
                lElbow.x - 50f,
                lElbow.y - 18f,
                textPaint
            )

            drawContext.canvas.nativeCanvas.drawText(
                "R: ${rAngle.toInt()}°",
                rElbow.x + 16f,
                rElbow.y - 18f,
                textPaint
            )

            return@Canvas
        }

        // ==============================
        // 2. SIDE VIEW RENDERING (Profile)
        // ==============================
        val shoulder = mapPoint(keypoints.shoulder) ?: return@Canvas
        val elbow = mapPoint(keypoints.elbow) ?: return@Canvas
        val wrist = mapPoint(keypoints.wrist) ?: return@Canvas
        val hip = mapPoint(keypoints.hip) ?: return@Canvas
        val knee = mapPoint(keypoints.knee) ?: hip
        val ankle = mapPoint(keypoints.ankle) ?: knee

        // 1. Draw Arm Bones: Shoulder -> Elbow -> Wrist
        drawLine(
            color = armColor,
            start = shoulder,
            end = elbow,
            strokeWidth = boneStroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = armColor,
            start = elbow,
            end = wrist,
            strokeWidth = boneStroke,
            cap = StrokeCap.Round
        )

        // Hand Target indicator
        drawCircle(color = handGreen.copy(alpha = 0.25f), radius = 20.dp.toPx(), center = wrist)
        drawCircle(color = handGreen, radius = 12.dp.toPx(), center = wrist)
        drawCircle(color = Color.White, radius = 6.dp.toPx(), center = wrist)

        // 2. Draw Plank Bones: Shoulder -> Hip -> Knee -> Ankle
        drawLine(
            color = boneColor,
            start = shoulder,
            end = hip,
            strokeWidth = boneStroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = boneColor,
            start = hip,
            end = knee,
            strokeWidth = boneStroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = boneColor,
            start = knee,
            end = ankle,
            strokeWidth = boneStroke,
            cap = StrokeCap.Round
        )

        // 3. Draw Joint Indicators
        val joints = listOf(shoulder, elbow, hip, knee, ankle)
        for (joint in joints) {
            drawCircle(
                color = jointColor,
                radius = jointRadius,
                center = joint
            )
            drawCircle(
                color = boneColor,
                radius = jointRadius * 0.6f,
                center = joint
            )
        }

        // 4. Draw Angle Text Overlays near Elbow and Hip joints
        drawContext.canvas.nativeCanvas.drawText(
            "${elbowAngle.toInt()}°",
            elbow.x + 16f,
            elbow.y - 12f,
            textPaint
        )

        drawContext.canvas.nativeCanvas.drawText(
            "${backAngle.toInt()}°",
            hip.x + 16f,
            hip.y - 12f,
            textPaint
        )
    }
}
