package com.example.posedetection

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
    private val config: PushupDetectorConfig = PushupDetectorConfig()
) {
    private val elbowFilter = MovingAverageFilter(config.smoothingWindowSize)
    private val backFilter = MovingAverageFilter(config.smoothingWindowSize)

    var currentState: RepState = RepState.IDLE
        private set

    var validReps: Int = 0
        private set

    var invalidReps: Int = 0
        private set

    private var isCurrentRepPostureValid: Boolean = true
    private var lastPostureFeedback: PostureFeedback = PostureFeedback.READY
    private var framesWithLowConfidence: Int = 0

    fun reset() {
        currentState = RepState.IDLE
        validReps = 0
        invalidReps = 0
        isCurrentRepPostureValid = true
        lastPostureFeedback = PostureFeedback.READY
        framesWithLowConfidence = 0
        elbowFilter.reset()
        backFilter.reset()
    }

    /**
     * Main entry point to process a single frame's detected keypoints.
     */
    fun processKeypoints(keypoints: PoseKeypoints?): PushupDetectionResult {
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
            return processFrontViewKeypoints(keypoints)
        }

        // Handle SIDE view (profile view, single dominant side tracked)
        return processSideViewKeypoints(keypoints)
    }

    private fun processFrontViewKeypoints(keypoints: PoseKeypoints): PushupDetectionResult {
        // 1. Check if both hands are detected
        if (!keypoints.isBothHandsDetected) {
            if (currentState != RepState.IDLE) {
                isCurrentRepPostureValid = false
            }
            lastPostureFeedback = PostureFeedback.BOTH_HANDS_NOT_DETECTED
            return PushupDetectionResult(
                repState = currentState,
                validReps = validReps,
                invalidReps = invalidReps,
                currentElbowAngle = elbowFilter.currentAverage(),
                currentBackAngle = 180f,
                feedback = PostureFeedback.BOTH_HANDS_NOT_DETECTED,
                isFormValid = isCurrentRepPostureValid,
                detectedSide = BodySide.FRONT,
                isBodyVisible = true,
                keypoints = keypoints,
                repDepthProgress = calculateDepthProgress(elbowFilter.currentAverage()),
                isBothHandsDetected = false,
                leftElbowAngle = keypoints.leftElbowAngle,
                rightElbowAngle = keypoints.rightElbowAngle
            )
        }

        // 2. Both hands are detected: calculate dual arm elbow angles
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

        // 3. Form validation in front view:
        // a) Shoulders should remain level (not tilting heavily to one side)
        val isShouldersLevel = keypoints.shoulderTiltDegrees <= config.maxShoulderTiltDegrees
        // b) Both arms should bend symmetrically
        val isArmSymmetric = abs(leftAngle - rightAngle) <= config.maxArmAngleDisparity
        val isFormGood = isShouldersLevel && isArmSymmetric

        val postureDirectionFeedback = when {
            !isShouldersLevel -> PostureFeedback.KEEP_SHOULDERS_LEVEL
            !isArmSymmetric -> PostureFeedback.UNEVEN_ARMS
            else -> PostureFeedback.GOOD_FORM
        }

        if (currentState != RepState.IDLE && !isFormGood) {
            isCurrentRepPostureValid = false
        }

        val depthThreshold = config.frontViewElbowDepthThreshold
        val extensionThreshold = config.frontViewElbowExtensionThreshold

        // 4. State Machine transitions
        when (currentState) {
            RepState.IDLE -> {
                isCurrentRepPostureValid = true
                if (smoothedElbow <= config.elbowDescentThreshold) {
                    currentState = RepState.GOING_DOWN
                    lastPostureFeedback = postureDirectionFeedback
                } else {
                    lastPostureFeedback = if (isFormGood) PostureFeedback.READY else postureDirectionFeedback
                }
            }

            RepState.GOING_DOWN -> {
                if (smoothedElbow <= depthThreshold) {
                    currentState = RepState.BOTTOM
                    lastPostureFeedback = if (isCurrentRepPostureValid) PostureFeedback.GOOD_FORM else postureDirectionFeedback
                } else if (smoothedElbow >= extensionThreshold) {
                    currentState = RepState.IDLE
                    lastPostureFeedback = PostureFeedback.GO_LOWER
                } else {
                    lastPostureFeedback = if (isCurrentRepPostureValid) PostureFeedback.GO_LOWER else postureDirectionFeedback
                }
            }

            RepState.BOTTOM -> {
                if (smoothedElbow >= config.elbowAscentThreshold) {
                    currentState = RepState.GOING_UP
                    lastPostureFeedback = if (isCurrentRepPostureValid) PostureFeedback.PUSH_ALL_WAY_UP else postureDirectionFeedback
                } else {
                    lastPostureFeedback = if (isCurrentRepPostureValid) PostureFeedback.GOOD_FORM else postureDirectionFeedback
                }
            }

            RepState.GOING_UP -> {
                if (smoothedElbow >= extensionThreshold) {
                    currentState = RepState.REP_COMPLETE
                    if (isCurrentRepPostureValid) {
                        validReps++
                        lastPostureFeedback = PostureFeedback.GOOD_FORM
                    } else {
                        invalidReps++
                        lastPostureFeedback = if (!isShouldersLevel) {
                            PostureFeedback.KEEP_SHOULDERS_LEVEL
                        } else if (!isArmSymmetric) {
                            PostureFeedback.UNEVEN_ARMS
                        } else {
                            PostureFeedback.KEEP_BACK_STRAIGHT
                        }
                    }
                    currentState = RepState.IDLE
                    isCurrentRepPostureValid = true
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
            isBothHandsDetected = true,
            leftElbowAngle = leftAngle,
            rightElbowAngle = rightAngle
        )
    }

    private fun processSideViewKeypoints(keypoints: PoseKeypoints): PushupDetectionResult {
        // 1. Calculate raw joint angles
        val rawElbowAngle = calculateJointAngle(
            first = keypoints.shoulder,
            mid = keypoints.elbow,
            last = keypoints.wrist
        )

        val rawBackAngle = if (keypoints.hasAnkle) {
            calculateJointAngle(
                first = keypoints.shoulder,
                mid = keypoints.hip,
                last = keypoints.ankle
            )
        } else {
            calculateJointAngle(
                first = keypoints.shoulder,
                mid = keypoints.hip,
                last = keypoints.knee
            )
        }

        // 2. Smooth angles using moving average
        val smoothedElbow = elbowFilter.add(rawElbowAngle)
        val smoothedBack = backFilter.add(rawBackAngle)

        // 3. Check posture gating against tolerance band
        val isBackWithinTolerance = smoothedBack in config.backAngleMinTolerance..config.backAngleMaxTolerance
        val postureDirectionFeedback = if (!isBackWithinTolerance) {
            detectPostureIssue(keypoints, smoothedBack)
        } else {
            PostureFeedback.GOOD_FORM
        }

        // If posture fails tolerance during active rep, invalidate this rep
        if (currentState != RepState.IDLE && !isBackWithinTolerance) {
            isCurrentRepPostureValid = false
        }

        // 4. State Machine transitions
        when (currentState) {
            RepState.IDLE -> {
                isCurrentRepPostureValid = true
                if (smoothedElbow <= config.elbowDescentThreshold) {
                    currentState = RepState.GOING_DOWN
                    lastPostureFeedback = postureDirectionFeedback
                } else {
                    lastPostureFeedback = if (isBackWithinTolerance) PostureFeedback.READY else postureDirectionFeedback
                }
            }

            RepState.GOING_DOWN -> {
                if (smoothedElbow <= config.elbowDepthThreshold) {
                    currentState = RepState.BOTTOM
                    lastPostureFeedback = if (isCurrentRepPostureValid) PostureFeedback.GOOD_FORM else postureDirectionFeedback
                } else if (smoothedElbow >= config.elbowExtensionThreshold) {
                    // Aborted rep before reaching bottom
                    currentState = RepState.IDLE
                    lastPostureFeedback = PostureFeedback.GO_LOWER
                } else {
                    lastPostureFeedback = if (isCurrentRepPostureValid) PostureFeedback.GO_LOWER else postureDirectionFeedback
                }
            }

            RepState.BOTTOM -> {
                if (smoothedElbow >= config.elbowAscentThreshold) {
                    currentState = RepState.GOING_UP
                    lastPostureFeedback = if (isCurrentRepPostureValid) PostureFeedback.PUSH_ALL_WAY_UP else postureDirectionFeedback
                } else {
                    lastPostureFeedback = if (isCurrentRepPostureValid) PostureFeedback.GOOD_FORM else postureDirectionFeedback
                }
            }

            RepState.GOING_UP -> {
                if (smoothedElbow >= config.elbowExtensionThreshold) {
                    // Rep completed!
                    currentState = RepState.REP_COMPLETE
                    if (isCurrentRepPostureValid) {
                        validReps++
                        lastPostureFeedback = PostureFeedback.GOOD_FORM
                    } else {
                        invalidReps++
                        lastPostureFeedback = PostureFeedback.KEEP_BACK_STRAIGHT
                    }
                    // Immediately ready for next rep
                    currentState = RepState.IDLE
                    isCurrentRepPostureValid = true
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
            isFormValid = isCurrentRepPostureValid,
            detectedSide = keypoints.side,
            isBodyVisible = true,
            keypoints = keypoints,
            repDepthProgress = calculateDepthProgress(smoothedElbow),
            isBothHandsDetected = true,
            leftElbowAngle = if (keypoints.side == BodySide.LEFT) smoothedElbow else 0f,
            rightElbowAngle = if (keypoints.side == BodySide.RIGHT) smoothedElbow else 0f
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
