package com.example.posedetection

/**
 * Configuration thresholds for pushup detection, rep counting, and posture validation.
 * All thresholds are centralized here for easy tuning.
 */
data class PushupDetectorConfig(
    // Angle at elbow when arms are fully extended/locked out (> ~160°)
    val elbowExtensionThreshold: Float = 158f,

    // Angle at elbow when full pushup depth is reached (< ~90°)
    val elbowDepthThreshold: Float = 90f,

    // Inflection threshold to initiate GOING_DOWN state
    val elbowDescentThreshold: Float = 145f,

    // Inflection threshold to initiate GOING_UP state from BOTTOM
    val elbowAscentThreshold: Float = 105f,

    // Tolerance band for back-line angle (shoulder -> hip -> ankle)
    // 180 degrees represents a perfect straight plank line.
    val backAngleMinTolerance: Float = 155f,
    val backAngleMaxTolerance: Float = 205f,

    // Minimum confidence for an individual landmark to be considered reliable
    val minLandmarkConfidence: Float = 0.5f,

    // Number of frames for moving average smoothing
    val smoothingWindowSize: Int = 5,

    // Pixel delta tolerance to classify sagging vs high hips
    val hipDisplacementThresholdPx: Float = 15f,

    // Front-view specific settings
    // In front view, 95 degrees accommodates 2D perspective foreshortening while ensuring deep chest descent
    val frontViewElbowDepthThreshold: Float = 95f,
    val frontViewElbowExtensionThreshold: Float = 155f,
    // Maximum allowed tilt between shoulders in degrees from horizontal (level shoulders)
    val maxShoulderTiltDegrees: Float = 22f,
    // Maximum disparity between left and right elbow angles before flagging uneven pushup
    val maxArmAngleDisparity: Float = 35f,
    // Minimum horizontal distance between left and right shoulder in px to classify as FRONT orientation
    val minShoulderWidthPx: Float = 60f
)
