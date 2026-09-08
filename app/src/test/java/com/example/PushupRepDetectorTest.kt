package com.example

import com.example.data.repository.WorkoutRepository
import com.example.posedetection.PushupDetectorConfig
import com.example.posedetection.PushupRepDetector
import com.example.posedetection.model.BodySide
import com.example.posedetection.model.PoseKeypoints
import com.example.posedetection.model.PosePoint
import com.example.posedetection.model.PostureFeedback
import com.example.posedetection.model.RepState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PushupRepDetectorTest {

    private lateinit var detector: PushupRepDetector

    @Before
    fun setUp() {
        // Use smoothingWindowSize = 1 for direct unit testing of frame state transitions
        detector = PushupRepDetector(
            config = PushupDetectorConfig(
                smoothingWindowSize = 1,
                elbowExtensionThreshold = 160f,
                elbowDepthThreshold = 90f,
                elbowDescentThreshold = 145f,
                elbowAscentThreshold = 105f,
                backAngleMinTolerance = 155f,
                backAngleMaxTolerance = 205f
            )
        )
    }

    @Test
    fun calculateJointAngle_rightAngle_is90Degrees() {
        val first = PosePoint(0f, 100f, 1f)
        val mid = PosePoint(0f, 0f, 1f)
        val last = PosePoint(100f, 0f, 1f)

        val angle = PushupRepDetector.calculateJointAngle(first, mid, last)
        assertEquals(90f, angle, 0.1f)
    }

    @Test
    fun calculateJointAngle_straightLine_is180Degrees() {
        val first = PosePoint(0f, 0f, 1f)
        val mid = PosePoint(50f, 0f, 1f)
        val last = PosePoint(100f, 0f, 1f)

        val angle = PushupRepDetector.calculateJointAngle(first, mid, last)
        assertEquals(180f, angle, 0.1f)
    }

    @Test
    fun validPushup_fullCycle_incrementsValidReps() {
        // 1. Initial lockout (Elbow ~170°, Back ~180°) -> IDLE
        var keypoints = createFrame(elbowDeg = 170f, backDeg = 180f)
        var result = detector.processKeypoints(keypoints)
        assertEquals(RepState.IDLE, result.repState)
        assertEquals(0, result.validReps)

        // 2. Going down (Elbow 130°, Back 180°) -> GOING_DOWN
        keypoints = createFrame(elbowDeg = 130f, backDeg = 180f)
        result = detector.processKeypoints(keypoints)
        assertEquals(RepState.GOING_DOWN, result.repState)

        // 3. Reaching bottom (Elbow 85°, Back 180°) -> BOTTOM
        keypoints = createFrame(elbowDeg = 85f, backDeg = 180f)
        result = detector.processKeypoints(keypoints)
        assertEquals(RepState.BOTTOM, result.repState)

        // 4. Going up (Elbow 120°, Back 180°) -> GOING_UP
        keypoints = createFrame(elbowDeg = 120f, backDeg = 180f)
        result = detector.processKeypoints(keypoints)
        assertEquals(RepState.GOING_UP, result.repState)

        // 5. Full lockout (Elbow 165°, Back 180°) -> Completed rep!
        keypoints = createFrame(elbowDeg = 165f, backDeg = 180f)
        result = detector.processKeypoints(keypoints)
        assertEquals(1, result.validReps)
        assertEquals(0, result.invalidReps)
        assertEquals(PostureFeedback.GOOD_FORM, result.feedback)
    }

    @Test
    fun invalidPushup_saggingHips_incrementsInvalidReps() {
        // 1. Initial lockout
        detector.processKeypoints(createFrame(elbowDeg = 170f, backDeg = 180f))

        // 2. Going down
        detector.processKeypoints(createFrame(elbowDeg = 130f, backDeg = 180f))

        // 3. Bottom reached, BUT hips sag significantly (back angle drops to 140° < 155°)
        val resultBottom = detector.processKeypoints(createFrame(elbowDeg = 85f, backDeg = 140f))
        assertFalse("Posture should be flagged invalid", resultBottom.isFormValid)

        // 4. Going up
        detector.processKeypoints(createFrame(elbowDeg = 120f, backDeg = 140f))

        // 5. Lockout
        val finalResult = detector.processKeypoints(createFrame(elbowDeg = 165f, backDeg = 180f))
        assertEquals("Valid reps must not increment on invalid form", 0, finalResult.validReps)
        assertEquals("Invalid reps should increment", 1, finalResult.invalidReps)
        assertEquals(PostureFeedback.KEEP_BACK_STRAIGHT, finalResult.feedback)
    }

    @Test
    fun abortedPushup_doesNotIncrementReps() {
        // 1. Start descent
        detector.processKeypoints(createFrame(elbowDeg = 170f, backDeg = 180f))
        detector.processKeypoints(createFrame(elbowDeg = 130f, backDeg = 180f))

        // 2. Returns to top without reaching <= 90° bottom depth
        val result = detector.processKeypoints(createFrame(elbowDeg = 165f, backDeg = 180f))
        assertEquals(RepState.IDLE, result.repState)
        assertEquals(0, result.validReps)
        assertEquals(0, result.invalidReps)
        assertEquals(PostureFeedback.GO_LOWER, result.feedback)
    }

    @Test
    fun frontView_bothHandsDetected_countsValidRep() {
        // 1. Initial position (both hands in frame, elbows at ~170°)
        var frame = createFrontFrame(elbowDeg = 170f, bothHandsDetected = true)
        var result = detector.processKeypoints(frame)
        assertEquals(RepState.IDLE, result.repState)
        assertTrue(result.isBothHandsDetected)

        // 2. Going down
        frame = createFrontFrame(elbowDeg = 130f, bothHandsDetected = true)
        result = detector.processKeypoints(frame)
        assertEquals(RepState.GOING_DOWN, result.repState)

        // 3. Bottom reached (< 95°)
        frame = createFrontFrame(elbowDeg = 85f, bothHandsDetected = true)
        result = detector.processKeypoints(frame)
        assertEquals(RepState.BOTTOM, result.repState)

        // 4. Going up (> 105°)
        frame = createFrontFrame(elbowDeg = 120f, bothHandsDetected = true)
        result = detector.processKeypoints(frame)
        assertEquals(RepState.GOING_UP, result.repState)

        // 5. Lockout (> 155°) -> Valid rep completed!
        frame = createFrontFrame(elbowDeg = 165f, bothHandsDetected = true)
        result = detector.processKeypoints(frame)
        assertEquals(1, result.validReps)
        assertEquals(0, result.invalidReps)
        assertEquals(PostureFeedback.GOOD_FORM, result.feedback)
    }

    @Test
    fun frontView_bothHandsNotDetected_flagsWarningFeedback() {
        // Front view frame where hands are not detected
        val frame = createFrontFrame(elbowDeg = 170f, bothHandsDetected = false)
        val result = detector.processKeypoints(frame)

        assertFalse("Should indicate both hands not detected", result.isBothHandsDetected)
        assertEquals(PostureFeedback.BOTH_HANDS_NOT_DETECTED, result.feedback)
    }

    @Test
    fun streakCalculation_consecutiveDays_calculatesCorrectly() {
        val today = 20000L

        // Did reps today, yesterday, and day before yesterday = 3 day streak
        val streak3 = WorkoutRepository.calculateConsecutiveStreak(
            listOf(today, today - 1, today - 2),
            today
        )
        assertEquals(3, streak3)

        // Did not do reps today yet, but did yesterday and day before = 2 day streak
        val streak2 = WorkoutRepository.calculateConsecutiveStreak(
            listOf(today - 1, today - 2),
            today
        )
        assertEquals(2, streak2)

        // Gap in streak: did reps 3 days ago, none yesterday or today = 0 streak
        val streak0 = WorkoutRepository.calculateConsecutiveStreak(
            listOf(today - 3),
            today
        )
        assertEquals(0, streak0)
    }

    private fun createFrame(elbowDeg: Float, backDeg: Float): PoseKeypoints {
        // Shoulder at (100, 100)
        val shoulder = PosePoint(100f, 100f, 1f)

        // Elbow at (100, 200)
        val elbow = PosePoint(100f, 200f, 1f)

        // Wrist positioned to create exact elbowDeg
        val radElbow = Math.toRadians((180 - elbowDeg).toDouble())
        val wristX = (100f + 100f * Math.sin(radElbow)).toFloat()
        val wristY = (200f + 100f * Math.cos(radElbow)).toFloat()
        val wrist = PosePoint(wristX, wristY, 1f)

        // Hip at (250, 100)
        val hip = PosePoint(250f, 100f, 1f)

        // Ankle positioned to create exact backDeg
        val radBack = Math.toRadians((180 - backDeg).toDouble())
        val ankleX = (250f + 150f * Math.cos(radBack)).toFloat()
        val ankleY = (100f + 150f * Math.sin(radBack)).toFloat()
        val ankle = PosePoint(ankleX, ankleY, 1f)

        return PoseKeypoints(
            side = BodySide.RIGHT,
            shoulder = shoulder,
            elbow = elbow,
            wrist = wrist,
            hip = hip,
            knee = PosePoint(300f, 100f, 1f),
            ankle = ankle,
            averageConfidence = 0.95f
        )
    }

    private fun createFrontFrame(
        elbowDeg: Float,
        bothHandsDetected: Boolean = true,
        shoulderTiltDeg: Float = 0f
    ): PoseKeypoints {
        val lShoulder = PosePoint(150f, 100f, 1f)
        val rShoulder = PosePoint(250f, 100f + shoulderTiltDeg, 1f)

        val lElbow = PosePoint(120f, 180f, 1f)
        val rElbow = PosePoint(280f, 180f, 1f)

        val wristConf = if (bothHandsDetected) 0.95f else 0.1f
        val lWrist = PosePoint(120f, 260f, wristConf)
        val rWrist = PosePoint(280f, 260f, wristConf)

        val midShoulder = PosePoint(200f, 100f, 1f)
        val midElbow = PosePoint(200f, 180f, 1f)
        val midWrist = PosePoint(200f, 260f, wristConf)
        val midHip = PosePoint(200f, 220f, 0.8f)

        return PoseKeypoints(
            side = BodySide.FRONT,
            shoulder = midShoulder,
            elbow = midElbow,
            wrist = midWrist,
            hip = midHip,
            knee = midHip,
            ankle = midHip,
            averageConfidence = if (bothHandsDetected) 0.95f else 0.5f,
            leftShoulder = lShoulder,
            leftElbow = lElbow,
            leftWrist = lWrist,
            rightShoulder = rShoulder,
            rightElbow = rElbow,
            rightWrist = rWrist,
            isBothHandsDetected = bothHandsDetected,
            leftElbowAngle = elbowDeg,
            rightElbowAngle = elbowDeg,
            shoulderTiltDegrees = shoulderTiltDeg
        )
    }
}
