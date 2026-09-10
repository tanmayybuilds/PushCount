package com.example

import com.example.data.model.PushupMode
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
                backAngleMinTolerance = 130f,
                backAngleMaxTolerance = 230f
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

        // 3. Bottom reached, BUT hips sag severely (back angle drops to 115° < min tolerance 130°)
        val resultBottom = detector.processKeypoints(createFrame(elbowDeg = 85f, backDeg = 115f))
        assertFalse("Posture should be flagged invalid on severe sag", resultBottom.isFormValid)

        // 4. Going up with severe sag
        detector.processKeypoints(createFrame(elbowDeg = 120f, backDeg = 115f))

        // 5. Lockout
        val finalResult = detector.processKeypoints(createFrame(elbowDeg = 165f, backDeg = 180f))
        assertEquals("Valid reps must not increment on severe form failure", 0, finalResult.validReps)
        assertEquals("Invalid reps should increment", 1, finalResult.invalidReps)
        assertEquals(PostureFeedback.KEEP_BACK_STRAIGHT, finalResult.feedback)
    }

    @Test
    fun lenientPushup_nearbyForm_countsAsValidRep() {
        // Form with moderate curve (140° back angle) should now be counted as valid per lenient user intent
        detector.processKeypoints(createFrame(elbowDeg = 170f, backDeg = 180f))
        detector.processKeypoints(createFrame(elbowDeg = 130f, backDeg = 145f))
        val resultBottom = detector.processKeypoints(createFrame(elbowDeg = 85f, backDeg = 140f))
        assertTrue("Moderate form deviation should be tolerated as valid", resultBottom.isFormValid)

        detector.processKeypoints(createFrame(elbowDeg = 120f, backDeg = 145f))
        val finalResult = detector.processKeypoints(createFrame(elbowDeg = 165f, backDeg = 180f))
        assertEquals("Lenient threshold should count nearby form as valid", 1, finalResult.validReps)
        assertEquals(0, finalResult.invalidReps)
    }

    @Test
    fun kneePushup_countsValidRep() {
        val initialKneeFrame = createFrame(elbowDeg = 170f, backDeg = 180f).copy(isKneePushup = true)
        detector.processKeypoints(initialKneeFrame)

        val downKneeFrame = createFrame(elbowDeg = 125f, backDeg = 170f).copy(isKneePushup = true)
        detector.processKeypoints(downKneeFrame)

        val bottomKneeFrame = createFrame(elbowDeg = 85f, backDeg = 165f).copy(isKneePushup = true)
        val bottomResult = detector.processKeypoints(bottomKneeFrame)
        assertTrue(bottomResult.isKneePushup)

        val upKneeFrame = createFrame(elbowDeg = 125f, backDeg = 170f).copy(isKneePushup = true)
        detector.processKeypoints(upKneeFrame)

        val finalResult = detector.processKeypoints(initialKneeFrame)
        assertEquals(1, finalResult.validReps)
        assertEquals(0, finalResult.invalidReps)
        assertTrue(finalResult.isKneePushup)
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
    fun standingUpright_armBending_doesNotCountReps() {
        // Simulates a user standing upright (dx = 0, dy = 300 -> 90° vertical inclination)
        // User moves arms, scratching head, gestures, or doing bicep curls
        val shoulder = PosePoint(200f, 100f, 1f)
        val elbow = PosePoint(200f, 200f, 1f)
        val hip = PosePoint(200f, 400f, 1f) // directly below shoulder = standing upright
        val knee = PosePoint(200f, 550f, 1f)
        val ankle = PosePoint(200f, 700f, 1f)

        fun standingFrame(wristY: Float): PoseKeypoints {
            val wrist = PosePoint(250f, wristY, 1f)
            return PoseKeypoints(
                side = BodySide.RIGHT,
                shoulder = shoulder,
                elbow = elbow,
                wrist = wrist,
                hip = hip,
                knee = knee,
                ankle = ankle,
                averageConfidence = 0.95f,
                leftElbowAngle = 80f,
                rightElbowAngle = 80f
            )
        }

        detector.processKeypoints(standingFrame(300f)) // lockout
        detector.processKeypoints(standingFrame(220f)) // bend
        detector.processKeypoints(standingFrame(150f)) // deep bend
        detector.processKeypoints(standingFrame(220f)) // ascent
        val result = detector.processKeypoints(standingFrame(300f)) // lockout

        assertEquals("Standing upright should NEVER count as pushup reps", 0, result.validReps)
        assertEquals("Standing upright should not increment invalid reps either", 0, result.invalidReps)
        assertEquals(RepState.IDLE, result.repState)
    }

    @Test
    fun frontView_wristsInAir_doesNotCountReps() {
        // Simulates person standing facing camera and gesturing / bicep curls with hands held in the air
        val lShoulder = PosePoint(150f, 100f, 1f)
        val rShoulder = PosePoint(250f, 100f, 1f)
        val lElbow = PosePoint(120f, 200f, 1f)
        val rElbow = PosePoint(280f, 200f, 1f)
        // Wrists floating high above elbows (y = 130 vs elbow y = 200)
        val lWrist = PosePoint(120f, 130f, 0.95f)
        val rWrist = PosePoint(280f, 130f, 0.95f)
        val midHip = PosePoint(200f, 380f, 0.9f)

        val frame = PoseKeypoints(
            side = BodySide.FRONT,
            shoulder = PosePoint(200f, 100f, 1f),
            elbow = PosePoint(200f, 200f, 1f),
            wrist = PosePoint(200f, 130f, 0.95f),
            hip = midHip,
            knee = midHip,
            ankle = midHip,
            averageConfidence = 0.95f,
            leftShoulder = lShoulder,
            leftElbow = lElbow,
            leftWrist = lWrist,
            rightShoulder = rShoulder,
            rightElbow = rElbow,
            rightWrist = rWrist,
            isBothHandsDetected = true,
            leftElbowAngle = 80f,
            rightElbowAngle = 80f
        )

        detector.processKeypoints(frame)
        val result = detector.processKeypoints(frame)

        assertEquals("Hands in the air in front view should not trigger pushup reps", 0, result.validReps)
        assertEquals(RepState.IDLE, result.repState)
    }

    @Test
    fun minorTwitchOrJitter_doesNotCountRep() {
        // Test that micro-movements (delta < minRepElbowDelta) are rejected as sensor noise
        detector.processKeypoints(createFrame(elbowDeg = 160f, backDeg = 180f))
        detector.processKeypoints(createFrame(elbowDeg = 142f, backDeg = 180f)) // only 18° movement
        detector.processKeypoints(createFrame(elbowDeg = 140f, backDeg = 180f))
        val result = detector.processKeypoints(createFrame(elbowDeg = 160f, backDeg = 180f))

        assertEquals("Small jitter should not count as a rep", 0, result.validReps)
        assertEquals(0, result.invalidReps)
    }

    @Test
    fun beginnerMode_validMovement_countsRep() {
        detector.setMode(PushupMode.BEGINNER)

        // Beginner mode requires ~115° depth and lock out at ~148°
        detector.processKeypoints(createFrame(elbowDeg = 155f, backDeg = 180f))
        detector.processKeypoints(createFrame(elbowDeg = 130f, backDeg = 180f))
        detector.processKeypoints(createFrame(elbowDeg = 108f, backDeg = 180f))
        detector.processKeypoints(createFrame(elbowDeg = 130f, backDeg = 180f))
        val result = detector.processKeypoints(createFrame(elbowDeg = 152f, backDeg = 180f))

        assertEquals("Beginner pushup should count with lenient depth", 1, result.validReps)
        assertEquals(0, result.invalidReps)
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

    @Test
    fun fastPushup_countedRegardlessOfSpeed() {
        detector.setMode(PushupMode.BEGINNER)
        var t = 1000L

        // Extremely fast rep: lockout -> bottom -> lockout in only 3 frames (60ms total)
        detector.processKeypoints(createFrame(elbowDeg = 170f, backDeg = 180f), timestampMs = t)
        t += 30L
        detector.processKeypoints(createFrame(elbowDeg = 85f, backDeg = 180f), timestampMs = t)
        t += 30L
        val res1 = detector.processKeypoints(createFrame(elbowDeg = 165f, backDeg = 180f), timestampMs = t)

        assertEquals("Fast rep must be counted", 1, res1.validReps)

        // Immediate subsequent fast rep within 50ms
        t += 40L
        detector.processKeypoints(createFrame(elbowDeg = 80f, backDeg = 180f), timestampMs = t)
        t += 30L
        val res2 = detector.processKeypoints(createFrame(elbowDeg = 165f, backDeg = 180f), timestampMs = t)

        assertEquals("Consecutive rapid rep must also be counted", 2, res2.validReps)
    }

    @Test
    fun wallPushupMode_countsProperlyWithIncline() {
        detector.setMode(PushupMode.WALL)
        var t = 1000L

        // Wall pushup: start at 165°, lean to 115°, return to 165°
        detector.processKeypoints(createFrame(elbowDeg = 165f, backDeg = 180f), timestampMs = t)
        t += 100L
        detector.processKeypoints(createFrame(elbowDeg = 115f, backDeg = 180f), timestampMs = t)
        t += 100L
        val result = detector.processKeypoints(createFrame(elbowDeg = 165f, backDeg = 180f), timestampMs = t)

        assertEquals("Wall pushup should be counted at 115° depth", 1, result.validReps)
        assertEquals(PostureFeedback.WALL_PUSHUP_DETECTED, result.feedback)
    }

    @Test
    fun kneePushupMode_countsProperly() {
        detector.setMode(PushupMode.KNEE)
        var t = 1000L

        val initialFrame = createFrame(elbowDeg = 170f, backDeg = 180f).copy(isKneePushup = true)
        detector.processKeypoints(initialFrame, timestampMs = t)
        t += 80L
        val downFrame = createFrame(elbowDeg = 95f, backDeg = 175f).copy(isKneePushup = true)
        detector.processKeypoints(downFrame, timestampMs = t)
        t += 80L
        val result = detector.processKeypoints(initialFrame, timestampMs = t)

        assertEquals("Knee pushup in KNEE mode must count", 1, result.validReps)
        assertEquals(PostureFeedback.KNEE_PUSHUP_DETECTED, result.feedback)
    }

    @Test
    fun hardMode_strictFormValidation() {
        detector.setMode(PushupMode.HARD)
        var t = 1000L

        // In hard mode, good form (elbow down to 88°, back 180°) passes
        detector.processKeypoints(createFrame(elbowDeg = 170f, backDeg = 180f), timestampMs = t)
        t += 100L
        detector.processKeypoints(createFrame(elbowDeg = 88f, backDeg = 180f), timestampMs = t)
        t += 100L
        val goodResult = detector.processKeypoints(createFrame(elbowDeg = 165f, backDeg = 180f), timestampMs = t)
        assertEquals("Hard mode good rep counted", 1, goodResult.validReps)
        assertEquals(0, goodResult.invalidReps)

        // In hard mode, bad back posture counts as invalid
        t += 100L
        detector.processKeypoints(createFrame(elbowDeg = 170f, backDeg = 180f), timestampMs = t)
        t += 100L
        detector.processKeypoints(createFrame(elbowDeg = 85f, backDeg = 110f), timestampMs = t)
        t += 100L
        val badResult = detector.processKeypoints(createFrame(elbowDeg = 165f, backDeg = 180f), timestampMs = t)
        assertEquals("Hard mode bad rep must not increment valid", 1, badResult.validReps)
        assertEquals("Hard mode bad rep must increment invalid", 1, badResult.invalidReps)
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

        // Knee positioned midway between hip and ankle
        val knee = PosePoint((hip.x + ankle.x) / 2f, (hip.y + ankle.y) / 2f, 1f)

        return PoseKeypoints(
            side = BodySide.RIGHT,
            shoulder = shoulder,
            elbow = elbow,
            wrist = wrist,
            hip = hip,
            knee = knee,
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
