package com.example.posedetection.model

enum class RepState {
    IDLE,
    GOING_DOWN,
    BOTTOM,
    GOING_UP,
    REP_COMPLETE
}

enum class BodySide {
    LEFT,
    RIGHT,
    FRONT,
    NONE
}

enum class PostureFeedback(val message: String, val isError: Boolean) {
    GOOD_FORM("Good form!", false),
    NEARBY_FORM("Good effort - rep counted!", false),
    KEEP_BACK_STRAIGHT("Keep back steady", false),
    HIPS_SAGGING("Hips dipping slightly", false),
    HIPS_TOO_HIGH("Hips high - lower slightly", false),
    GO_LOWER("Lower chest a bit more", false),
    PUSH_ALL_WAY_UP("Push all the way up", false),
    POOR_VISIBILITY("Can't see body - step back", true),
    READY("Ready - get into position", false),
    BOTH_HANDS_NOT_DETECTED("Keep both arms in view", true),
    KEEP_SHOULDERS_LEVEL("Keep shoulders level", false),
    UNEVEN_ARMS("Push evenly with both arms", false),
    KNEE_PUSHUP_DETECTED("Knee pushup active", false),
    WALL_PUSHUP_DETECTED("Wall pushup active", false),
    STRICT_FORM_FAILED("Strict form required - rep invalid", true)
}

data class PosePoint(
    val x: Float,
    val y: Float,
    val confidence: Float
)

data class PoseKeypoints(
    val side: BodySide,
    val shoulder: PosePoint,
    val elbow: PosePoint,
    val wrist: PosePoint,
    val hip: PosePoint,
    val knee: PosePoint,
    val ankle: PosePoint,
    val averageConfidence: Float,
    // Dual arm & Front view support
    val leftShoulder: PosePoint? = null,
    val leftElbow: PosePoint? = null,
    val leftWrist: PosePoint? = null,
    val rightShoulder: PosePoint? = null,
    val rightElbow: PosePoint? = null,
    val rightWrist: PosePoint? = null,
    val leftHip: PosePoint? = null,
    val rightHip: PosePoint? = null,
    val isBothHandsDetected: Boolean = false,
    val leftElbowAngle: Float = 0f,
    val rightElbowAngle: Float = 0f,
    val shoulderTiltDegrees: Float = 0f,
    val hasAnkle: Boolean = true,
    val isKneePushup: Boolean = false
)

data class PushupDetectionResult(
    val repState: RepState,
    val validReps: Int,
    val invalidReps: Int,
    val currentElbowAngle: Float,
    val currentBackAngle: Float,
    val feedback: PostureFeedback,
    val isFormValid: Boolean,
    val detectedSide: BodySide,
    val isBodyVisible: Boolean,
    val keypoints: PoseKeypoints? = null,
    // Progress of current rep depth: 0f (top / lockout) to 1f (bottom depth)
    val repDepthProgress: Float = 0f,
    val isBothHandsDetected: Boolean = false,
    val leftElbowAngle: Float = 0f,
    val rightElbowAngle: Float = 0f,
    val isKneePushup: Boolean = false
)
