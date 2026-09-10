package com.example.posedetection

import com.example.data.model.PushupMode
import com.example.posedetection.model.BodySide
import com.example.posedetection.model.PoseKeypoints
import com.example.posedetection.model.PosePoint
import com.example.posedetection.model.PostureFeedback
import com.example.posedetection.model.PushupDetectionResult
import com.example.posedetection.model.RepState
import kotlin.math.abs
import kotlin.math.atan2

/**
 * Standalone, pure-logic pushup rep detector with posture validation gating.
 * Implements the 5-state rep machine and 2-angle posture validation described in the specification.
 */
class PushupRepDetector(
    private var config: PushupDetectorConfig = PushupDetectorConfig(),
    var activeMode: PushupMode = PushupMode.BEGINNER
) {
    private var elbowFilter = MovingAverageFilter(config.smoothingWindowSize)
    private var backFilter = MovingAverageFilter(config.smoothingWindowSize)

    var currentState: RepState = RepState.IDLE
        private set

    var validReps: Int = 0
        private set

    var invalidReps: Int = 0
        private set

    private var isCurrentRepPostureValid: Boolean = true
    private var hasSeverePostureViolationInCurrentRep: Boolean = false
    private var lastSeverePostureFeedback: PostureFeedback = PostureFeedback.KEEP_BACK_STRAIGHT
    private var lastPostureFeedback: PostureFeedback = PostureFeedback.READY
    private var framesWithLowConfidence: Int = 0
    private var repStartTimestampMs: Long = 0L
    private var lastRepCompletionTimestampMs: Long = 0L
    private var currentRepFrameCount: Int = 0
    private var minElbowAngleInCurrentRep: Float = 180f
    private var maxElbowAngleInCurrentRep: Float = 0f

    fun setMode(mode: PushupMode, preserveCounters: Boolean = true) {
        val savedValid = if (preserveCounters) validReps else 0
        val savedInvalid = if (preserveCounters) invalidReps else 0
        activeMode = mode
        config = PushupDetectorConfig.forMode(mode, smoothingWindow = config.smoothingWindowSize)
        elbowFilter = MovingAverageFilter(config.smoothingWindowSize)
        backFilter = MovingAverageFilter(config.smoothingWindowSize)
        reset()
        if (preserveCounters) {
            validReps = savedValid
            invalidReps = savedInvalid
        }
    }

    private fun handleRepCompletion(
        timestampMs: Long,
        rawElbowAngle: Float,
        isPostureValid: Boolean,
        hasSevereViolation: Boolean,
        severeFeedback: PostureFeedback,
        standardFeedback: PostureFeedback
    ) {
        val elbowDelta = maxElbowAngleInCurrentRep - minElbowAngleInCurrentRep
        val isDeliberateRep = elbowDelta >= config.minRepElbowDelta

        if (!isDeliberateRep) {
            currentState = RepState.IDLE
            lastPostureFeedback = PostureFeedback.READY
            return
        }

        lastRepCompletionTimestampMs = timestampMs
        when (activeMode) {
            PushupMode.BEGINNER -> {
                if (hasSevereViolation) {
                    invalidReps++
                    lastPostureFeedback = severeFeedback
                } else if (isPostureValid) {
                    validReps++
                    lastPostureFeedback = PostureFeedback.GOOD_FORM
                } else {
                    validReps++
                    lastPostureFeedback = PostureFeedback.NEARBY_FORM
                }
            }
            PushupMode.HARD -> {
                if (isPostureValid && !hasSevereViolation) {
                    validReps++
                    lastPostureFeedback = PostureFeedback.GOOD_FORM
                } else {
                    invalidReps++
                    lastPostureFeedback = PostureFeedback.STRICT_FORM_FAILED
                }
            }
            PushupMode.WALL -> {
                validReps++
                lastPostureFeedback = PostureFeedback.WALL_PUSHUP_DETECTED
            }
            PushupMode.KNEE -> {
                if (isPostureValid && !hasSevereViolation) {
                    validReps++
                    lastPostureFeedback = PostureFeedback.KNEE_PUSHUP_DETECTED
                } else {
                    invalidReps++
                    lastPostureFeedback = standardFeedback
                }
            }
        }
        currentState = RepState.IDLE
        isCurrentRepPostureValid = true
        hasSeverePostureViolationInCurrentRep = false
        minElbowAngleInCurrentRep = rawElbowAngle
        maxElbowAngleInCurrentRep = rawElbowAngle
        currentRepFrameCount = 0
    }

    fun reset() {
        currentState = RepState.IDLE
        validReps = 0
        invalidReps = 0
        isCurrentRepPostureValid = true
        hasSeverePostureViolationInCurrentRep = false
        lastSeverePostureFeedback = PostureFeedback.KEEP_BACK_STRAIGHT
        lastPostureFeedback = PostureFeedback.READY
        framesWithLowConfidence = 0
        repStartTimestampMs = 0L
        lastRepCompletionTimestampMs = 0L
        currentRepFrameCount = 0
        minElbowAngleInCurrentRep = 180f
        maxElbowAngleInCurrentRep = 0f
        elbowFilter.reset()
        backFilter.reset()
    }

    /**
     * Main entry point to process a single frame's detected keypoints.
     */
    fun processKeypoints(keypoints: PoseKeypoints?, timestampMs: Long = 0L): PushupDetectionResult {
        if (keypoints == null || keypoints.averageConfidence < config.minLandmarkConfidence) {
            framesWithLowConfidence++
            val feedback = if (framesWithLowConfidence > 10) {
                PostureFeedback.POOR_VISIBILITY
            } else {
                lastPostureFeedback
            }
            return PushupDetectionResult(
                repState = currentState,
                validReps = validReps,
                invalidReps = invalidReps,
                currentElbowAngle = elbowFilter.currentAverage(),
                currentBackAngle = backFilter.currentAverage(),
                feedback = feedback,
                isFormValid = isCurrentRepPostureValid,
                detectedSide = keypoints?.side ?: BodySide.NONE,
                isBodyVisible = false,
                keypoints = keypoints,
                repDepthProgress = calculateDepthProgress(elbowFilter.currentAverage()),
                isBothHandsDetected = keypoints?.isBothHandsDetected ?: false,
                leftElbowAngle = keypoints?.leftElbowAngle ?: 0f,
                rightElbowAngle = keypoints?.rightElbowAngle ?: 0f
            )
        }

        framesWithLowConfidence = 0

        // Handle FRONT view (facing camera directly, both arms/hands tracked)
        if (keypoints.side == BodySide.FRONT) {
            return processFrontViewKeypoints(keypoints, timestampMs)
        }

        // Handle SIDE view (profile view, single dominant side tracked)
        return processSideViewKeypoints(keypoints, timestampMs)
    }

    private fun isStandingOrSittingUpright(keypoints: PoseKeypoints): Boolean {
        if (activeMode == PushupMode.WALL) {
            // In Wall pushup mode, leaning against the wall is standard
            return false
        }
        // In floor modes (BEGINNER, HARD, KNEE):
        // Torso vector: shoulder to hip
        val dx = kotlin.math.abs(keypoints.hip.x - keypoints.shoulder.x)
        val dy = kotlin.math.abs(keypoints.hip.y - keypoints.shoulder.y)
        val torsoInclineAngle = Math.toDegrees(kotlin.math.atan2(dy.toDouble(), dx.toDouble())).toFloat()
        return torsoInclineAngle > config.maxTorsoInclineFromHorizontal
    }

    private fun isFrontViewArmFloating(keypoints: PoseKeypoints): Boolean {
        if (activeMode == PushupMode.WALL) return false

        // In a floor pushup, wrists are on the ground, so wrists are below or level with elbows.
        // If wrists are high above elbows in screen coordinates, arms are raised in the air (bicep curl, touching face, etc.)
        val lWristAboveElbow = keypoints.leftWrist != null && keypoints.leftElbow != null &&
                (keypoints.leftWrist.y < keypoints.leftElbow.y - 35f)
        val rWristAboveElbow = keypoints.rightWrist != null && keypoints.rightElbow != null &&
                (keypoints.rightWrist.y < keypoints.rightElbow.y - 35f)
        if (lWristAboveElbow && rWristAboveElbow) {
            return true
        }

        // Check vertical body span in front view: if standing upright, hips are far below shoulders
        val lHip = keypoints.leftHip
        val rHip = keypoints.rightHip
        val lShoulder = keypoints.leftShoulder
        val rShoulder = keypoints.rightShoulder
        if (lHip != null && rHip != null && lShoulder != null && rShoulder != null &&
            lHip.confidence >= 0.4f && rHip.confidence >= 0.4f) {
            val midHipY = (lHip.y + rHip.y) / 2f
            val midShoulderY = (lShoulder.y + rShoulder.y) / 2f
            val shoulderWidth = kotlin.math.abs(rShoulder.x - lShoulder.x).coerceAtLeast(40f)
            val verticalSpanRatio = (midHipY - midShoulderY) / shoulderWidth
            if (verticalSpanRatio > 1.45f) {
                return true
            }
        }
        return false
    }

    private fun processFrontViewKeypoints(keypoints: PoseKeypoints, timestampMs: Long): PushupDetectionResult {
        // 1. Calculate dual arm elbow angles
        val leftAngle = if (keypoints.leftElbowAngle > 0f) {
            keypoints.leftElbowAngle
        } else if (keypoints.leftShoulder != null && keypoints.leftElbow != null && keypoints.leftWrist != null) {
            calculateJointAngle(keypoints.leftShoulder, keypoints.leftElbow, keypoints.leftWrist)
        } else {
            180f
        }

        val rightAngle = if (keypoints.rightElbowAngle > 0f) {
            keypoints.rightElbowAngle
        } else if (keypoints.rightShoulder != null && keypoints.rightElbow != null && keypoints.rightWrist != null) {
            calculateJointAngle(keypoints.rightShoulder, keypoints.rightElbow, keypoints.rightWrist)
        } else {
            180f
        }

        val rawElbowAngle = (leftAngle + rightAngle) / 2f
        val smoothedElbow = elbowFilter.add(rawElbowAngle)

        val isKnee = (activeMode == PushupMode.KNEE) || keypoints.isKneePushup

        // 2. Both hands / arms in view check
        val isArmsInView = keypoints.isBothHandsDetected
        if (!isArmsInView) {
            currentState = RepState.IDLE
            return PushupDetectionResult(
                repState = RepState.IDLE,
                validReps = validReps,
                invalidReps = invalidReps,
                currentElbowAngle = smoothedElbow,
                currentBackAngle = 180f,
                feedback = PostureFeedback.BOTH_HANDS_NOT_DETECTED,
                isFormValid = false,
                detectedSide = BodySide.FRONT,
                isBodyVisible = true,
                keypoints = keypoints,
                repDepthProgress = 0f,
                isBothHandsDetected = false,
                leftElbowAngle = leftAngle,
                rightElbowAngle = rightAngle,
                isKneePushup = isKnee
            )
        }

        // 3. Reject standing upright or holding arms in the air
        if (isFrontViewArmFloating(keypoints)) {
            currentState = RepState.IDLE
            return PushupDetectionResult(
                repState = RepState.IDLE,
                validReps = validReps,
                invalidReps = invalidReps,
                currentElbowAngle = smoothedElbow,
                currentBackAngle = 180f,
                feedback = PostureFeedback.READY,
                isFormValid = true,
                detectedSide = BodySide.FRONT,
                isBodyVisible = true,
                keypoints = keypoints,
                repDepthProgress = 0f,
                isBothHandsDetected = true,
                leftElbowAngle = leftAngle,
                rightElbowAngle = rightAngle,
                isKneePushup = isKnee
            )
        }

        // Form validation in front view:
        val isShouldersLevel = keypoints.shoulderTiltDegrees <= config.maxShoulderTiltDegrees
        val isArmSymmetric = abs(leftAngle - rightAngle) <= config.maxArmAngleDisparity

        val postureDirectionFeedback = when {
            activeMode == PushupMode.WALL -> PostureFeedback.WALL_PUSHUP_DETECTED
            isKnee -> PostureFeedback.KNEE_PUSHUP_DETECTED
            !isShouldersLevel -> PostureFeedback.KEEP_SHOULDERS_LEVEL
            !isArmSymmetric -> PostureFeedback.UNEVEN_ARMS
            else -> PostureFeedback.GOOD_FORM
        }

        val depthThreshold = config.frontViewElbowDepthThreshold
        val extensionThreshold = config.frontViewElbowExtensionThreshold

        val isSevereViolation = keypoints.shoulderTiltDegrees > (config.maxShoulderTiltDegrees + 15f) ||
                abs(leftAngle - rightAngle) > (config.maxArmAngleDisparity + 18f)

        // Fast & accurate angle detection:
        val effectiveElbowAngle = minOf(rawElbowAngle, smoothedElbow)
        val effectiveExtensionAngle = maxOf(rawElbowAngle, smoothedElbow)

        val isDebounced = timestampMs == 0L || (timestampMs - lastRepCompletionTimestampMs >= 30L)
        val isRepActiveOrStarting = currentState != RepState.IDLE || (effectiveElbowAngle <= config.elbowDescentThreshold && isDebounced)

        if (isRepActiveOrStarting && isSevereViolation) {
            isCurrentRepPostureValid = false
        }

        // Track range of motion during rep
        if (currentState != RepState.IDLE) {
            currentRepFrameCount++
            if (effectiveElbowAngle < minElbowAngleInCurrentRep) minElbowAngleInCurrentRep = effectiveElbowAngle
            if (effectiveExtensionAngle > maxElbowAngleInCurrentRep) maxElbowAngleInCurrentRep = effectiveExtensionAngle
        }

        // 4. State Machine transitions
        when (currentState) {
            RepState.IDLE -> {
                minElbowAngleInCurrentRep = effectiveElbowAngle
                maxElbowAngleInCurrentRep = effectiveExtensionAngle
                currentRepFrameCount = 0

                if (effectiveElbowAngle <= config.elbowDescentThreshold && isDebounced) {
                    currentState = RepState.GOING_DOWN
                    repStartTimestampMs = timestampMs
                    currentRepFrameCount = 1

                    if (effectiveElbowAngle <= depthThreshold) {
                        currentState = RepState.BOTTOM
                        lastPostureFeedback = if (isCurrentRepPostureValid) PostureFeedback.GOOD_FORM else postureDirectionFeedback
                    } else {
                        lastPostureFeedback = postureDirectionFeedback
                    }
                } else {
                    isCurrentRepPostureValid = true
                    hasSeverePostureViolationInCurrentRep = false
                    lastPostureFeedback = PostureFeedback.READY
                }
            }

            RepState.GOING_DOWN -> {
                if (effectiveElbowAngle <= depthThreshold) {
                    currentState = RepState.BOTTOM
                    lastPostureFeedback = if (isCurrentRepPostureValid) PostureFeedback.GOOD_FORM else postureDirectionFeedback
                } else if (effectiveExtensionAngle >= extensionThreshold) {
                    currentState = RepState.IDLE
                    lastPostureFeedback = PostureFeedback.GO_LOWER
                } else {
                    lastPostureFeedback = if (isCurrentRepPostureValid) PostureFeedback.GO_LOWER else postureDirectionFeedback
                }
            }

            RepState.BOTTOM -> {
                if (effectiveExtensionAngle >= extensionThreshold) {
                    handleRepCompletion(
                        timestampMs = timestampMs,
                        rawElbowAngle = rawElbowAngle,
                        isPostureValid = isCurrentRepPostureValid,
                        hasSevereViolation = isSevereViolation,
                        severeFeedback = postureDirectionFeedback,
                        standardFeedback = postureDirectionFeedback
                    )
                } else if (effectiveElbowAngle >= config.elbowAscentThreshold) {
                    currentState = RepState.GOING_UP
                    lastPostureFeedback = if (isCurrentRepPostureValid) PostureFeedback.PUSH_ALL_WAY_UP else postureDirectionFeedback
                } else {
                    lastPostureFeedback = if (isCurrentRepPostureValid) PostureFeedback.GOOD_FORM else postureDirectionFeedback
                }
            }

            RepState.GOING_UP -> {
                if (effectiveExtensionAngle >= extensionThreshold) {
                    handleRepCompletion(
                        timestampMs = timestampMs,
                        rawElbowAngle = rawElbowAngle,
                        isPostureValid = isCurrentRepPostureValid,
                        hasSevereViolation = isSevereViolation,
                        severeFeedback = postureDirectionFeedback,
                        standardFeedback = postureDirectionFeedback
                    )
                } else {
                    lastPostureFeedback = if (isCurrentRepPostureValid) PostureFeedback.PUSH_ALL_WAY_UP else postureDirectionFeedback
                }
            }

            RepState.REP_COMPLETE -> {
                currentState = RepState.IDLE
                isCurrentRepPostureValid = true
            }
        }

        return PushupDetectionResult(
            repState = currentState,
            validReps = validReps,
            invalidReps = invalidReps,
            currentElbowAngle = smoothedElbow,
            currentBackAngle = (180f - keypoints.shoulderTiltDegrees).coerceIn(0f, 180f),
            feedback = lastPostureFeedback,
            isFormValid = isCurrentRepPostureValid,
            detectedSide = BodySide.FRONT,
            isBodyVisible = true,
            keypoints = keypoints,
            repDepthProgress = calculateDepthProgress(smoothedElbow),
            isBothHandsDetected = keypoints.isBothHandsDetected,
            leftElbowAngle = leftAngle,
            rightElbowAngle = rightAngle,
            isKneePushup = isKnee
        )
    }

    private fun processSideViewKeypoints(keypoints: PoseKeypoints, timestampMs: Long): PushupDetectionResult {
        // 1. Calculate raw joint angles
        val rawElbowAngle = calculateJointAngle(
            first = keypoints.shoulder,
            mid = keypoints.elbow,
            last = keypoints.wrist
        )

        // For Knee Pushups: the core line is shoulder -> hip -> knee!
        val isKnee = (activeMode == PushupMode.KNEE) || keypoints.isKneePushup
        val rawBackAngle = if (isKnee || !keypoints.hasAnkle) {
            calculateJointAngle(
                first = keypoints.shoulder,
                mid = keypoints.hip,
                last = keypoints.knee
            )
        } else {
            val hipKneeAnkle = calculateJointAngle(keypoints.hip, keypoints.knee, keypoints.ankle)
            if (hipKneeAnkle < 150f) {
                // Knee is bent (knee pushup!)
                calculateJointAngle(keypoints.shoulder, keypoints.hip, keypoints.knee)
            } else {
                calculateJointAngle(keypoints.shoulder, keypoints.hip, keypoints.ankle)
            }
        }

        // 2. Smooth angles using moving average
        val smoothedElbow = elbowFilter.add(rawElbowAngle)
        val smoothedBack = backFilter.add(rawBackAngle)

        // Reject standing or sitting upright in floor modes
        if (isStandingOrSittingUpright(keypoints)) {
            currentState = RepState.IDLE
            return PushupDetectionResult(
                repState = RepState.IDLE,
                validReps = validReps,
                invalidReps = invalidReps,
                currentElbowAngle = smoothedElbow,
                currentBackAngle = smoothedBack,
                feedback = PostureFeedback.READY,
                isFormValid = true,
                detectedSide = keypoints.side,
                isBodyVisible = true,
                keypoints = keypoints,
                repDepthProgress = 0f,
                isBothHandsDetected = true,
                leftElbowAngle = if (keypoints.side == BodySide.LEFT) smoothedElbow else 0f,
                rightElbowAngle = if (keypoints.side == BodySide.RIGHT) smoothedElbow else 0f,
                isKneePushup = isKnee
            )
        }

        // 3. Check posture gating against generous tolerance band
        val isBackWithinTolerance = smoothedBack in config.backAngleMinTolerance..config.backAngleMaxTolerance
        val postureDirectionFeedback = when {
            activeMode == PushupMode.WALL -> PostureFeedback.WALL_PUSHUP_DETECTED
            isKnee -> PostureFeedback.KNEE_PUSHUP_DETECTED
            !isBackWithinTolerance -> PostureFeedback.KEEP_BACK_STRAIGHT
            else -> PostureFeedback.GOOD_FORM
        }

        val effectiveElbowAngle = minOf(rawElbowAngle, smoothedElbow)
        val effectiveExtensionAngle = maxOf(rawElbowAngle, smoothedElbow)

        val isDebounced = timestampMs == 0L || (timestampMs - lastRepCompletionTimestampMs >= 30L)
        val isRepActiveOrStarting = currentState != RepState.IDLE || (effectiveElbowAngle <= config.elbowDescentThreshold && isDebounced)

        if (isRepActiveOrStarting && !isBackWithinTolerance) {
            isCurrentRepPostureValid = false
            if (smoothedBack < 120f || smoothedBack > 220f || rawBackAngle < 120f || rawBackAngle > 220f) {
                hasSeverePostureViolationInCurrentRep = true
                lastSeverePostureFeedback = postureDirectionFeedback
            }
        }

        // Track range of motion during rep
        if (currentState != RepState.IDLE) {
            currentRepFrameCount++
            if (effectiveElbowAngle < minElbowAngleInCurrentRep) minElbowAngleInCurrentRep = effectiveElbowAngle
            if (effectiveExtensionAngle > maxElbowAngleInCurrentRep) maxElbowAngleInCurrentRep = effectiveExtensionAngle
        }

        // 4. State Machine transitions
        when (currentState) {
            RepState.IDLE -> {
                minElbowAngleInCurrentRep = effectiveElbowAngle
                maxElbowAngleInCurrentRep = effectiveExtensionAngle
                currentRepFrameCount = 0

                if (effectiveElbowAngle <= config.elbowDescentThreshold && isDebounced) {
                    currentState = RepState.GOING_DOWN
                    repStartTimestampMs = timestampMs
                    currentRepFrameCount = 1

                    if (effectiveElbowAngle <= config.elbowDepthThreshold) {
                        currentState = RepState.BOTTOM
                        lastPostureFeedback = if (isCurrentRepPostureValid) PostureFeedback.GOOD_FORM else postureDirectionFeedback
                    } else {
                        lastPostureFeedback = postureDirectionFeedback
                    }
                } else {
                    isCurrentRepPostureValid = true
                    hasSeverePostureViolationInCurrentRep = false
                    lastPostureFeedback = if (isBackWithinTolerance) PostureFeedback.READY else postureDirectionFeedback
                }
            }

            RepState.GOING_DOWN -> {
                if (effectiveElbowAngle <= config.elbowDepthThreshold) {
                    currentState = RepState.BOTTOM
                    lastPostureFeedback = if (isCurrentRepPostureValid) PostureFeedback.GOOD_FORM else postureDirectionFeedback
                } else if (effectiveExtensionAngle >= config.elbowExtensionThreshold) {
                    currentState = RepState.IDLE
                    lastPostureFeedback = PostureFeedback.GO_LOWER
                } else {
                    lastPostureFeedback = if (isCurrentRepPostureValid) PostureFeedback.GO_LOWER else postureDirectionFeedback
                }
            }

            RepState.BOTTOM -> {
                if (effectiveExtensionAngle >= config.elbowExtensionThreshold) {
                    handleRepCompletion(
                        timestampMs = timestampMs,
                        rawElbowAngle = rawElbowAngle,
                        isPostureValid = isCurrentRepPostureValid,
                        hasSevereViolation = hasSeverePostureViolationInCurrentRep,
                        severeFeedback = lastSeverePostureFeedback,
                        standardFeedback = postureDirectionFeedback
                    )
                } else if (effectiveElbowAngle >= config.elbowAscentThreshold) {
                    currentState = RepState.GOING_UP
                    lastPostureFeedback = if (isCurrentRepPostureValid) PostureFeedback.PUSH_ALL_WAY_UP else postureDirectionFeedback
                } else {
                    lastPostureFeedback = if (isCurrentRepPostureValid) PostureFeedback.GOOD_FORM else postureDirectionFeedback
                }
            }

            RepState.GOING_UP -> {
                if (effectiveExtensionAngle >= config.elbowExtensionThreshold) {
                    handleRepCompletion(
                        timestampMs = timestampMs,
                        rawElbowAngle = rawElbowAngle,
                        isPostureValid = isCurrentRepPostureValid,
                        hasSevereViolation = hasSeverePostureViolationInCurrentRep,
                        severeFeedback = lastSeverePostureFeedback,
                        standardFeedback = postureDirectionFeedback
                    )
                } else {
                    lastPostureFeedback = if (isCurrentRepPostureValid) PostureFeedback.PUSH_ALL_WAY_UP else postureDirectionFeedback
                }
            }

            RepState.REP_COMPLETE -> {
                currentState = RepState.IDLE
                isCurrentRepPostureValid = true
            }
        }

        return PushupDetectionResult(
            repState = currentState,
            validReps = validReps,
            invalidReps = invalidReps,
            currentElbowAngle = smoothedElbow,
            currentBackAngle = smoothedBack,
            feedback = lastPostureFeedback,
            isFormValid = (activeMode == PushupMode.WALL || isBackWithinTolerance) && isCurrentRepPostureValid,
            detectedSide = keypoints.side,
            isBodyVisible = true,
            keypoints = keypoints,
            repDepthProgress = calculateDepthProgress(smoothedElbow),
            isBothHandsDetected = true,
            leftElbowAngle = if (keypoints.side == BodySide.LEFT) smoothedElbow else 0f,
            rightElbowAngle = if (keypoints.side == BodySide.RIGHT) smoothedElbow else 0f,
            isKneePushup = isKnee
        )
    }

    /**
     * Determines whether back form violation is due to sagging hips or piking hips too high.
     */
    private fun detectPostureIssue(keypoints: PoseKeypoints, backAngle: Float): PostureFeedback {
        val shoulder = keypoints.shoulder
        val hip = keypoints.hip
        val ankle = keypoints.ankle

        val deltaX = ankle.x - shoulder.x
        if (abs(deltaX) > 10f) {
            val t = (hip.x - shoulder.x) / deltaX
            val expectedLineY = shoulder.y + t * (ankle.y - shoulder.y)
            // Y increases downwards towards floor in screen coordinates
            return if (hip.y > expectedLineY + config.hipDisplacementThresholdPx) {
                PostureFeedback.HIPS_SAGGING
            } else if (hip.y < expectedLineY - config.hipDisplacementThresholdPx) {
                PostureFeedback.HIPS_TOO_HIGH
            } else {
                PostureFeedback.KEEP_BACK_STRAIGHT
            }
        }

        return PostureFeedback.KEEP_BACK_STRAIGHT
    }

    private fun calculateDepthProgress(elbowAngle: Float): Float {
        val top = config.elbowExtensionThreshold
        val bottom = config.elbowDepthThreshold
        return ((top - elbowAngle) / (top - bottom)).coerceIn(0f, 1f)
    }

    companion object {
        /**
         * Calculates interior angle at 'mid' point formed by first -> mid -> last in degrees [0, 180].
         */
        fun calculateJointAngle(first: PosePoint, mid: PosePoint, last: PosePoint): Float {
            val angle1 = atan2(first.y - mid.y, first.x - mid.x)
            val angle2 = atan2(last.y - mid.y, last.x - mid.x)
            var degrees = abs(Math.toDegrees((angle1 - angle2).toDouble())).toFloat()
            if (degrees > 180f) {
                degrees = 360f - degrees
            }
            return degrees
        }
    }
}
