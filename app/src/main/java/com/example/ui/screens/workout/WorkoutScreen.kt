package com.example.ui.screens.workout

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    val state by viewModel.uiState.collectAsStateWithLifecycle()

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

        // 3. Top Controls Bar: Exit, Flip Camera, Timer
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { showEndDialog = true },
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.6f))
                    .testTag("workout_close_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Exit Workout",
                    tint = Color.White
                )
            }

            // Duration Pill
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.Black.copy(alpha = 0.6f),
                modifier = Modifier.testTag("workout_timer")
            ) {
                Text(
                    text = formatDuration(state.elapsedSeconds),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            // Camera Flip Button
            IconButton(
                onClick = { viewModel.flipCamera() },
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.6f))
                    .testTag("workout_flip_camera_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Cameraswitch,
                    contentDescription = "Switch Camera",
                    tint = Color.White
                )
            }
        }

        // 4. Center-Top: Live Rep Counter HUD
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 80.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Orientation & Hands Detection Status Badge
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = when {
                    state.detectedSide == com.example.posedetection.model.BodySide.FRONT && state.isBothHandsDetected ->
                        Color(0xFF00C853).copy(alpha = 0.85f)
                    state.detectedSide == com.example.posedetection.model.BodySide.FRONT && !state.isBothHandsDetected ->
                        Color(0xFFFF3D00).copy(alpha = 0.9f)
                    state.detectedSide != com.example.posedetection.model.BodySide.NONE ->
                        Color(0xFF2979FF).copy(alpha = 0.85f)
                    else ->
                        Color.Black.copy(alpha = 0.6f)
                },
                modifier = Modifier
                    .padding(bottom = 8.dp)
                    .testTag("detection_mode_badge")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    val statusText = when {
                        state.detectedSide == com.example.posedetection.model.BodySide.FRONT && state.isBothHandsDetected ->
                            "FRONT VIEW • BOTH HANDS DETECTED"
                        state.detectedSide == com.example.posedetection.model.BodySide.FRONT && !state.isBothHandsDetected ->
                            "FRONT VIEW • BOTH HANDS NOT DETECTED"
                        state.detectedSide == com.example.posedetection.model.BodySide.LEFT ->
                            "SIDE VIEW • LEFT PROFILE"
                        state.detectedSide == com.example.posedetection.model.BodySide.RIGHT ->
                            "SIDE VIEW • RIGHT PROFILE"
                        else ->
                            "GET IN CAMERA VIEW"
                    }

                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(28.dp),
                color = Color.Black.copy(alpha = 0.65f),
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                ) {
                    // Valid Reps (Hero)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${state.validReps}",
                            style = MaterialTheme.typography.displayMedium,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            text = "VALID",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }

                    // Divider
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(40.dp)
                            .background(Color.White.copy(alpha = 0.2f))
                    )

                    // Invalid Reps (Secondary)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${state.invalidReps}",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = "NO REP",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Real-Time Posture Feedback Banner
            PostureBanner(
                feedback = state.feedback,
                isFormValid = state.isFormValid
            )
        }

        // 5. Bottom Controls: Pause/Resume, End Workout
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
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White
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
            Text(
                text = "Workout Summary",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Great effort! Here is how your form looked:",
                    style = MaterialTheme.typography.bodyMedium
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Valid Reps:", fontWeight = FontWeight.SemiBold)
                    Text(
                        text = "$validReps",
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Form Issues (No Rep):", fontWeight = FontWeight.SemiBold)
                    Text(
                        text = "$invalidReps",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Form Accuracy:", fontWeight = FontWeight.SemiBold)
                    Text(text = "$accuracy%", fontWeight = FontWeight.Bold)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Total Duration:", fontWeight = FontWeight.SemiBold)
                    Text(text = formatDuration(durationSeconds), fontWeight = FontWeight.Bold)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.testTag("dialog_save_exit_button")
            ) {
                Text("SAVE & EXIT")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
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
