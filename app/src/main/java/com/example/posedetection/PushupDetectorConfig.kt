package com.example.posedetection

import com.example.data.model.PushupMode

/**
 * Configuration thresholds for pushup detection, rep counting, and posture validation.
 * All thresholds are centralized here for easy tuning.
 */
data class PushupDetectorConfig(
    // Angle at elbow when arms are extended (~146° for comfortable lockout without hyperextension)
    val elbowExtensionThreshold: Float = 146f,

    // Angle at elbow when pushup depth is reached (lenient 118° so natural depth counts)
    val elbowDepthThreshold: Float = 118f,

    // Inflection threshold to initiate GOING_DOWN state
    val elbowDescentThreshold: Float = 138f,

    // Inflection threshold to initiate GOING_UP state from BOTTOM
    val elbowAscentThreshold: Float = 124f,

    // Generous tolerance band for back/body angle (shoulder -> hip -> knee / ankle)
    // 180° is straight; 125° to 235° accommodates natural spinal curvature, hip variations, and knee pushups
    val backAngleMinTolerance: Float = 125f,
    val backAngleMaxTolerance: Float = 235f,

    // Minimum confidence for an individual landmark to be considered reliable
    val minLandmarkConfidence: Float = 0.25f,

    // Number of frames for moving average smoothing (low window size for zero-lag fast tracking)
    val smoothingWindowSize: Int = 2,

    // Pixel delta tolerance to classify sagging vs high hips (generous)
    val hipDisplacementThresholdPx: Float = 35f,

    // Front-view specific settings
    val frontViewElbowDepthThreshold: Float = 118f,
    val frontViewElbowExtensionThreshold: Float = 146f,
    // Maximum allowed tilt between shoulders in degrees from horizontal
    val maxShoulderTiltDegrees: Float = 36f,
    // Maximum disparity between left and right elbow angles before flagging uneven pushup
    val maxArmAngleDisparity: Float = 50f,
    // Minimum horizontal distance between left and right shoulder in px to classify as FRONT orientation
    val minShoulderWidthPx: Float = 35f,

    // Minimum required elbow range of motion (degrees) between highest and lowest points during rep
    val minRepElbowDelta: Float = 15f,

    // Maximum torso incline (angle from horizontal ground plane) allowed for floor pushups.
    val maxTorsoInclineFromHorizontal: Float = 58f,

    // Minimum duration in ms required for a pushup (0L allows fastest explosive reps)
    val minRepDurationMs: Long = 0L,

    // Minimum number of consecutive frames in descending/bottom phase
    val minRepFrames: Int = 1
) {
    companion object {
        fun forMode(mode: PushupMode, smoothingWindow: Int = 2): PushupDetectorConfig {
            return when (mode) {
                PushupMode.BEGINNER -> PushupDetectorConfig(
                    elbowExtensionThreshold = 146f,
                    elbowDepthThreshold = 118f,
                    elbowDescentThreshold = 138f,
                    elbowAscentThreshold = 125f,
                    backAngleMinTolerance = 90f,
                    backAngleMaxTolerance = 270f,
                    frontViewElbowDepthThreshold = 118f,
                    frontViewElbowExtensionThreshold = 146f,
                    maxShoulderTiltDegrees = 40f,
                    maxArmAngleDisparity = 60f,
                    minRepElbowDelta = 15f,
                    maxTorsoInclineFromHorizontal = 58f,
                    minRepDurationMs = 0L,
                    minRepFrames = 1,
                    smoothingWindowSize = smoothingWindow
                )
                PushupMode.HARD -> PushupDetectorConfig(
                    elbowExtensionThreshold = 158f,
                    elbowDepthThreshold = 92f,
                    elbowDescentThreshold = 142f,
                    elbowAscentThreshold = 105f,
                    backAngleMinTolerance = 155f,
                    backAngleMaxTolerance = 205f,
                    frontViewElbowDepthThreshold = 92f,
                    frontViewElbowExtensionThreshold = 158f,
                    hipDisplacementThresholdPx = 20f,
                    maxShoulderTiltDegrees = 22f,
                    maxArmAngleDisparity = 32f,
                    minRepElbowDelta = 32f,
                    maxTorsoInclineFromHorizontal = 50f,
                    minRepDurationMs = 0L,
                    minRepFrames = 1,
                    smoothingWindowSize = smoothingWindow
                )
                PushupMode.WALL -> PushupDetectorConfig(
                    elbowExtensionThreshold = 146f,
                    elbowDepthThreshold = 122f,
                    elbowDescentThreshold = 136f,
                    elbowAscentThreshold = 126f,
                    backAngleMinTolerance = 125f,
                    backAngleMaxTolerance = 235f,
                    frontViewElbowDepthThreshold = 122f,
                    frontViewElbowExtensionThreshold = 146f,
                    minRepElbowDelta = 14f,
                    maxTorsoInclineFromHorizontal = 88f,
                    minRepDurationMs = 0L,
                    minRepFrames = 1,
                    smoothingWindowSize = smoothingWindow
                )
                PushupMode.KNEE -> PushupDetectorConfig(
                    elbowExtensionThreshold = 146f,
                    elbowDepthThreshold = 118f,
                    elbowDescentThreshold = 138f,
                    elbowAscentThreshold = 124f,
                    backAngleMinTolerance = 125f,
                    backAngleMaxTolerance = 235f,
                    frontViewElbowDepthThreshold = 118f,
                    frontViewElbowExtensionThreshold = 146f,
                    minRepElbowDelta = 16f,
                    maxTorsoInclineFromHorizontal = 58f,
                    minRepDurationMs = 0L,
                    minRepFrames = 1,
                    smoothingWindowSize = smoothingWindow
                )
            }
        }
    }
}
