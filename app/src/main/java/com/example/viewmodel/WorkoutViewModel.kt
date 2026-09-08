package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.WorkoutSession
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.WorkoutRepository
import com.example.posedetection.model.BodySide
import com.example.posedetection.model.PoseKeypoints
import com.example.posedetection.model.PostureFeedback
import com.example.posedetection.model.PushupDetectionResult
import com.example.posedetection.model.RepState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDate

data class WorkoutUiState(
    val validReps: Int = 0,
    val invalidReps: Int = 0,
    val currentElbowAngle: Float = 180f,
    val currentBackAngle: Float = 180f,
    val repState: RepState = RepState.IDLE,
    val feedback: PostureFeedback = PostureFeedback.READY,
    val isFormValid: Boolean = true,
    val detectedSide: BodySide = BodySide.NONE,
    val isBodyVisible: Boolean = false,
    val repDepthProgress: Float = 0f,
    val keypoints: PoseKeypoints? = null,
    val frameWidth: Int = 0,
    val frameHeight: Int = 0,
    val isPaused: Boolean = false,
    val elapsedSeconds: Int = 0,
    val useFrontCamera: Boolean = true,
    val sessionFinished: Boolean = false,
    val savedSession: WorkoutSession? = null,
    val isBothHandsDetected: Boolean = false,
    val leftElbowAngle: Float = 0f,
    val rightElbowAngle: Float = 0f
)

class WorkoutViewModel(
    private val workoutRepository: WorkoutRepository,
    private val preferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(WorkoutUiState())
    val uiState: StateFlow<WorkoutUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null

    init {
        viewModelScope.launch {
            val initialCameraPref = preferencesRepository.useFrontCameraFlow.first()
            _uiState.update { it.copy(useFrontCamera = initialCameraPref) }
            startTimer()
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (isActive) {
                delay(1000)
                if (!_uiState.value.isPaused && !_uiState.value.sessionFinished) {
                    _uiState.update { it.copy(elapsedSeconds = it.elapsedSeconds + 1) }
                }
            }
        }
    }

    fun onPoseAnalysisResult(
        result: PushupDetectionResult,
        frameWidth: Int,
        frameHeight: Int,
        isRotated: Boolean
    ) {
        if (_uiState.value.isPaused || _uiState.value.sessionFinished) return

        _uiState.update { current ->
            current.copy(
                validReps = result.validReps,
                invalidReps = result.invalidReps,
                currentElbowAngle = result.currentElbowAngle,
                currentBackAngle = result.currentBackAngle,
                repState = result.repState,
                feedback = result.feedback,
                isFormValid = result.isFormValid,
                detectedSide = result.detectedSide,
                isBodyVisible = result.isBodyVisible,
                repDepthProgress = result.repDepthProgress,
                keypoints = result.keypoints,
                frameWidth = frameWidth,
                frameHeight = frameHeight,
                isBothHandsDetected = result.isBothHandsDetected,
                leftElbowAngle = result.leftElbowAngle,
                rightElbowAngle = result.rightElbowAngle
            )
        }
    }

    fun togglePause() {
        _uiState.update { it.copy(isPaused = !it.isPaused) }
    }

    fun flipCamera() {
        val newFrontCamera = !_uiState.value.useFrontCamera
        _uiState.update { it.copy(useFrontCamera = newFrontCamera) }
        viewModelScope.launch {
            preferencesRepository.setUseFrontCamera(newFrontCamera)
        }
    }

    fun finishWorkout(onSaved: (WorkoutSession) -> Unit) {
        val state = _uiState.value
        if (state.sessionFinished) return

        viewModelScope.launch {
            val session = WorkoutSession(
                date = LocalDate.now().toEpochDay(),
                validReps = state.validReps,
                invalidReps = state.invalidReps,
                durationSeconds = state.elapsedSeconds,
                timestamp = System.currentTimeMillis()
            )

            // Only insert into database if user actually attempted reps or spent > 3 seconds
            if (session.totalReps > 0 || session.durationSeconds >= 3) {
                val id = workoutRepository.insertSession(session)
                val savedSessionWithId = session.copy(id = id)
                _uiState.update { it.copy(sessionFinished = true, savedSession = savedSessionWithId) }
                onSaved(savedSessionWithId)
            } else {
                _uiState.update { it.copy(sessionFinished = true, savedSession = session) }
                onSaved(session)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
    }
}
