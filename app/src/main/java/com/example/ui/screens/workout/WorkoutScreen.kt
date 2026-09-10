package com.example.ui.screens.workout

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.media.AudioManager
import android.media.ToneGenerator
import android.view.WindowManager
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.example.data.model.PushupMode
import com.example.data.model.WorkoutSession
import com.example.posedetection.PoseDetectorProcessor
import com.example.ui.components.PostureBanner
import com.example.viewmodel.WorkoutViewModel

@Composable
fun WorkoutScreen(
    viewModel: WorkoutViewModel,
    onFinishAndExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Keep screen on while workout camera is running
    DisposableEffect(Unit) {
        val activity = context.findActivity()
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    var prevValidReps by remember { mutableStateOf(state.validReps) }
    var prevInvalidReps by remember { mutableStateOf(state.invalidReps) }

    LaunchedEffect(state.validReps) {
        if (state.validReps > prevValidReps) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            try {
                val toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 70)
                toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
            } catch (_: Exception) {}
        }
        prevValidReps = state.validReps
    }

    LaunchedEffect(state.invalidReps) {
        if (state.invalidReps > prevInvalidReps) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            try {
                val toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 70)
                toneGen.startTone(ToneGenerator.TONE_PROP_NACK, 180)
            } catch (_: Exception) {}
        }
        prevInvalidReps = state.invalidReps
    }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    var showEndDialog by remember { mutableStateOf(false) }

    if (!hasCameraPermission) {
        CameraPermissionRationale(
            onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
            onCancel = onFinishAndExit
        )
        return
    }

    val poseProcessor = remember {
        PoseDetectorProcessor { result, width, height, isRotated ->
            viewModel.onPoseAnalysisResult(result, width, height, isRotated)
        }
    }

    // Keep detector config in sync with active mode
    LaunchedEffect(state.activeMode) {
        poseProcessor.setMode(state.activeMode)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("workout_screen")
    ) {
        // 1. Live Camera Preview (Full Screen)
        CameraPreviewView(
            useFrontCamera = state.useFrontCamera,
            poseProcessor = poseProcessor,
            modifier = Modifier.fillMaxSize()
        )

        // 2. Skeleton Overlay (Bones, Joints, Real-Time Angle values)
        SkeletonOverlayView(
            keypoints = state.keypoints,
            frameWidth = state.frameWidth,
            frameHeight = state.frameHeight,
            isFormValid = state.isFormValid,
            isFrontCamera = state.useFrontCamera,
            elbowAngle = state.currentElbowAngle,
            backAngle = state.currentBackAngle,
            isBothHandsDetected = state.isBothHandsDetected,
            leftElbowAngle = state.leftElbowAngle,
            rightElbowAngle = state.rightElbowAngle,
            modifier = Modifier.fillMaxSize()
        )

        // 3. Top Controls Bar: Exit, Flip Camera, Mode Switcher, Timer
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { showEndDialog = true },
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.65f))
                        .testTag("workout_close_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Exit Workout",
                        tint = Color.White
                    )
                }

                // Mode Selector Pill (Tap to cycle through: Beginner, Hard, Wall, Knee)
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.Black.copy(alpha = 0.7f),
                    modifier = Modifier
                        .clickable { viewModel.cycleTargetMode() }
                        .testTag("exercise_mode_selector")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        val tintColor = when (state.activeMode) {
                            PushupMode.BEGINNER -> Color(0xFF00E5FF)
                            PushupMode.HARD -> Color(0xFFFF5252)
                            PushupMode.WALL -> Color(0xFF69F0AE)
                            PushupMode.KNEE -> Color(0xFFFFB300)
                        }
                        Icon(
                            imageVector = when (state.activeMode) {
                                PushupMode.WALL, PushupMode.KNEE -> Icons.Default.AccessibilityNew
                                else -> Icons.Default.FitnessCenter
                            },
                            contentDescription = null,
                            tint = tintColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = state.activeMode.displayName.uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                // Duration Pill
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.Black.copy(alpha = 0.65f),
                    modifier = Modifier.testTag("workout_timer")
                ) {
                    Text(
                        text = formatDuration(state.elapsedSeconds),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }

                // Camera Flip Button
                IconButton(
                    onClick = { viewModel.flipCamera() },
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.65f))
                        .testTag("workout_flip_camera_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Cameraswitch,
                        contentDescription = "Switch Camera",
                        tint = Color.White
                    )
                }
            }
        }

        // 4. Center-Top: Live Rep Counter HUD & Angle Stats
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 68.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Orientation & Detection Status Badges
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 6.dp)
            ) {
                // View detection badge
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = when {
                        state.detectedSide == com.example.posedetection.model.BodySide.FRONT && state.isBothHandsDetected ->
                            Color(0xFF00E676).copy(alpha = 0.85f)
                        state.detectedSide == com.example.posedetection.model.BodySide.FRONT && !state.isBothHandsDetected ->
                            Color(0xFFFFB300).copy(alpha = 0.85f)
                        state.detectedSide != com.example.posedetection.model.BodySide.NONE ->
                            Color(0xFF00B0FF).copy(alpha = 0.85f)
                        else ->
                            Color.Black.copy(alpha = 0.65f)
                    },
                    modifier = Modifier.testTag("detection_mode_badge")
                ) {
                    val statusText = when {
                        state.detectedSide == com.example.posedetection.model.BodySide.FRONT && state.isBothHandsDetected ->
                            "FRONT • BOTH ARMS IN VIEW"
                        state.detectedSide == com.example.posedetection.model.BodySide.FRONT && !state.isBothHandsDetected ->
                            "FRONT • STEP BACK SLIGHTLY"
                        state.detectedSide == com.example.posedetection.model.BodySide.LEFT ->
                            "SIDE • LEFT PROFILE"
                        state.detectedSide == com.example.posedetection.model.BodySide.RIGHT ->
                            "SIDE • RIGHT PROFILE"
                        else ->
                            "STEP IN FRONT OF CAMERA"
                    }

                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                // Knee Pushup indicator badge
                if (state.isKneePushup) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFFFFB300),
                        modifier = Modifier.testTag("knee_pushup_badge")
                    ) {
                        Text(
                            text = "KNEE PUSHUP",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            color = Color.Black,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // High-Tech Glassmorphic Rep Counter Card
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFF101622).copy(alpha = 0.8f),
                modifier = Modifier
                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(24.dp))
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(22.dp),
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp)
                ) {
                    // Valid Reps (Hero Glow)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${state.validReps}",
                            fontSize = 44.sp,
                            lineHeight = 46.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF00E676)
                        )
                        Text(
                            text = "VALID REPS",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.75f)
                        )
                    }

                    // Divider
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(44.dp)
                            .background(Color.White.copy(alpha = 0.2f))
                    )

                    // Secondary: Live Elbow Angle / Form stats
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val displayAngle = if (state.detectedSide == com.example.posedetection.model.BodySide.FRONT && state.leftElbowAngle > 0f) {
                            "${state.leftElbowAngle.toInt()}°"
                        } else {
                            "${state.currentElbowAngle.toInt()}°"
                        }

                        Text(
                            text = displayAngle,
                            fontSize = 32.sp,
                            lineHeight = 36.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF00E5FF)
                        )
                        Text(
                            text = "ARM DEPTH",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.75f)
                        )
                    }

                    // Divider
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(44.dp)
                            .background(Color.White.copy(alpha = 0.2f))
                    )

                    // Invalid Reps
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${state.invalidReps}",
                            fontSize = 32.sp,
                            lineHeight = 36.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (state.invalidReps > 0) Color(0xFFFF3366) else Color.White.copy(alpha = 0.4f)
                        )
                        Text(
                            text = "NO REP",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Real-Time Posture Feedback Banner
            PostureBanner(
                feedback = state.feedback,
                isFormValid = state.isFormValid
            )
        }

        // 5. Dynamic Left-Side Rep Depth Gauge
        val animatedDepth by animateFloatAsState(
            targetValue = state.repDepthProgress,
            label = "rep_depth_progress"
        )

        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 16.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.Black.copy(alpha = 0.75f),
                modifier = Modifier
                    .width(42.dp)
                    .height(210.dp)
                    .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                    .padding(6.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "TOP",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.6f)
                    )

                    // Vertical Progress Tube
                    Box(
                        modifier = Modifier
                            .width(12.dp)
                            .weight(1f)
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.White.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        // Depth fill bar
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(animatedDepth)
                                .background(
                                    if (animatedDepth >= 0.95f) {
                                        Brush.verticalGradient(listOf(Color(0xFF00E676), Color(0xFF00E5FF)))
                                    } else {
                                        Brush.verticalGradient(listOf(Color(0xFF00E5FF), Color(0xFF2979FF)))
                                    }
                                )
                        )

                        // 100% Target Notch line
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.dp)
                                .align(Alignment.TopCenter)
                                .background(Color(0xFF00E676))
                        )
                    }

                    Text(
                        text = if (animatedDepth >= 0.95f) "100%" else "${(animatedDepth * 100).toInt()}%",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        color = if (animatedDepth >= 0.95f) Color(0xFF00E676) else Color.White
                    )
                }
            }
        }

        // 6. Bottom Controls: Pause/Resume, End Workout
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Pause/Resume Button
            Button(
                onClick = { viewModel.togglePause() },
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Black.copy(alpha = 0.7f),
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .size(64.dp)
                    .testTag("workout_pause_button")
            ) {
                Icon(
                    imageVector = if (state.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                    contentDescription = if (state.isPaused) "Resume" else "Pause",
                    modifier = Modifier.size(28.dp)
                )
            }

            // Finish Workout Button
            Button(
                onClick = { showEndDialog = true },
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF00E676),
                    contentColor = Color.Black
                ),
                modifier = Modifier
                    .height(56.dp)
                    .padding(horizontal = 16.dp)
                    .testTag("workout_end_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Stop,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "END WORKOUT",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Paused Overlay
        AnimatedVisibility(
            visible = state.isPaused,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "WORKOUT PAUSED",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { viewModel.togglePause() },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("RESUME")
                    }
                }
            }
        }

        // End Workout Confirmation / Summary Dialog
        if (showEndDialog) {
            EndWorkoutDialog(
                validReps = state.validReps,
                invalidReps = state.invalidReps,
                durationSeconds = state.elapsedSeconds,
                onConfirm = {
                    showEndDialog = false
                    viewModel.finishWorkout {
                        onFinishAndExit()
                    }
                },
                onDismiss = { showEndDialog = false }
            )
        }
    }
}

@Composable
private fun EndWorkoutDialog(
    validReps: Int,
    invalidReps: Int,
    durationSeconds: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val total = validReps + invalidReps
    val accuracy = if (total > 0) ((validReps.toDouble() / total) * 100).toInt() else 100

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.FitnessCenter,
                    contentDescription = null,
                    tint = Color(0xFF00E676),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Workout Summary",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = if (accuracy >= 80) "Outstanding effort! Your reps were counted with high precision."
                    else "Good workout session! Keep working on full lockout at the top.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Accuracy Bar
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Form Accuracy",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "$accuracy%",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Black,
                            color = if (accuracy >= 80) Color(0xFF00E676) else Color(0xFFFFB300)
                        )
                    }
                    LinearProgressIndicator(
                        progress = { (accuracy / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = if (accuracy >= 80) Color(0xFF00E676) else Color(0xFFFFB300),
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "Valid Reps Counted:", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = "$validReps",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF00E676),
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "Unfinished / No Rep:", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = "$invalidReps",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (invalidReps > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "Active Duration:", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = formatDuration(durationSeconds),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676), contentColor = Color.Black),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("dialog_save_exit_button")
            ) {
                Text("SAVE & EXIT", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("CONTINUE")
            }
        }
    )
}

@Composable
private fun CameraPermissionRationale(
    onRequestPermission: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Videocam,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Camera Access Required",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "PushCount uses on-device computer vision to detect your posture and count reps in real time. Video feeds are never stored or transmitted anywhere.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(36.dp))

        Button(
            onClick = onRequestPermission,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("enable_camera_permission_button"),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Text("ENABLE CAMERA", fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("GO BACK")
        }
    }
}

private fun formatDuration(seconds: Int): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return String.format("%02d:%02d", mins, secs)
}

private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
