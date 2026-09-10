package com.example.posedetection

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.example.posedetection.model.BodySide
import com.example.posedetection.model.PoseKeypoints
import com.example.posedetection.model.PosePoint
import com.example.posedetection.model.PushupDetectionResult
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseDetector
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions

/**
 * Handles ML Kit Pose Detection on camera frames via CameraX ImageAnalysis.
 */
class PoseDetectorProcessor(
    private val repDetector: PushupRepDetector = PushupRepDetector(),
    private val onResult: (PushupDetectionResult, Int, Int, Boolean) -> Unit
) : ImageAnalysis.Analyzer {

    // On-device lightweight pose detector in STREAM_MODE per tech stack requirements
    private val options = PoseDetectorOptions.Builder()
        .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
        .build()

    private val detector: PoseDetector = PoseDetection.getClient(options)

    @Volatile
    private var isProcessing = false

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null || isProcessing) {
            imageProxy.close()
            return
        }

        isProcessing = true
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        val imageWidth = if (rotationDegrees == 90 || rotationDegrees == 270) imageProxy.height else imageProxy.width
        val imageHeight = if (rotationDegrees == 90 || rotationDegrees == 270) imageProxy.width else imageProxy.height

        val inputImage = InputImage.fromMediaImage(mediaImage, rotationDegrees)

        detector.process(inputImage)
            .addOnSuccessListener { pose ->
                val keypoints = extractKeypoints(pose)
                val result = repDetector.processKeypoints(keypoints, System.currentTimeMillis())
                onResult(result, imageWidth, imageHeight, rotationDegrees == 90 || rotationDegrees == 270)
            }
            .addOnFailureListener {
                val result = repDetector.processKeypoints(null, System.currentTimeMillis())
                onResult(result, imageWidth, imageHeight, false)
            }
            .addOnCompleteListener {
                isProcessing = false
                imageProxy.close()
            }
    }

    fun reset() {
        repDetector.reset()
    }

    fun setMode(mode: com.example.data.model.PushupMode) {
        repDetector.setMode(mode)
    }

    fun close() {
        detector.close()
    }

    companion object {
        /**
         * Extracts keypoints supporting both FRONT view (both hands / arms detected) and SIDE view (left or right profile).
         */
        fun extractKeypoints(pose: Pose): PoseKeypoints? {
            val leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
            val leftElbow = pose.getPoseLandmark(PoseLandmark.LEFT_ELBOW)
            val leftWrist = pose.getPoseLandmark(PoseLandmark.LEFT_WRIST)
            val leftHip = pose.getPoseLandmark(PoseLandmark.LEFT_HIP)
            val leftKnee = pose.getPoseLandmark(PoseLandmark.LEFT_KNEE)
            val leftAnkle = pose.getPoseLandmark(PoseLandmark.LEFT_ANKLE)

            val rightShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)
            val rightElbow = pose.getPoseLandmark(PoseLandmark.RIGHT_ELBOW)
            val rightWrist = pose.getPoseLandmark(PoseLandmark.RIGHT_WRIST)
            val rightHip = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP)
            val rightKnee = pose.getPoseLandmark(PoseLandmark.RIGHT_KNEE)
            val rightAnkle = pose.getPoseLandmark(PoseLandmark.RIGHT_ANKLE)

            val minConf = 0.25f

            val hasLeftShoulder = leftShoulder != null && leftShoulder.inFrameLikelihood >= minConf
            val hasRightShoulder = rightShoulder != null && rightShoulder.inFrameLikelihood >= minConf
            val hasLeftElbow = leftElbow != null && leftElbow.inFrameLikelihood >= minConf
            val hasRightElbow = rightElbow != null && rightElbow.inFrameLikelihood >= minConf
            val hasLeftWrist = leftWrist != null && leftWrist.inFrameLikelihood >= minConf
            val hasRightWrist = rightWrist != null && rightWrist.inFrameLikelihood >= minConf

            // Check if user is positioned facing the camera (FRONT orientation)
            // Front view is prioritized because it fits in limited room space
            if (hasLeftShoulder && hasRightShoulder) {
                val shoulderWidth = kotlin.math.abs(leftShoulder.position.x - rightShoulder.position.x)
                val isFrontOrientation = shoulderWidth >= 40f && (
                    (hasLeftElbow || hasRightElbow) ||
                    (hasLeftWrist || hasRightWrist) ||
                    (leftShoulder.inFrameLikelihood >= 0.4f && rightShoulder.inFrameLikelihood >= 0.4f)
                )

                if (isFrontOrientation) {
                    val lShoulderPt = PosePoint(leftShoulder.position.x, leftShoulder.position.y, leftShoulder.inFrameLikelihood)
                    val rShoulderPt = PosePoint(rightShoulder.position.x, rightShoulder.position.y, rightShoulder.inFrameLikelihood)

                    val lElbowPt = if (leftElbow != null && leftElbow.inFrameLikelihood >= minConf) {
                        PosePoint(leftElbow.position.x, leftElbow.position.y, leftElbow.inFrameLikelihood)
                    } else {
                        PosePoint(lShoulderPt.x - 35f, lShoulderPt.y + 60f, 0.4f)
                    }

                    val rElbowPt = if (rightElbow != null && rightElbow.inFrameLikelihood >= minConf) {
                        PosePoint(rightElbow.position.x, rightElbow.position.y, rightElbow.inFrameLikelihood)
                    } else {
                        PosePoint(rShoulderPt.x + 35f, rShoulderPt.y + 60f, 0.4f)
                    }

                    // Forgiving wrist detection: if wrist is close to bottom frame edge or palm on floor,
                    // infer smoothly from forearm trajectory rather than failing the user.
                    val lWristPt = if (leftWrist != null && leftWrist.inFrameLikelihood >= 0.2f) {
                        PosePoint(leftWrist.position.x, leftWrist.position.y, leftWrist.inFrameLikelihood)
                    } else {
                        PosePoint(lElbowPt.x, lElbowPt.y + 50f, 0.4f)
                    }

                    val rWristPt = if (rightWrist != null && rightWrist.inFrameLikelihood >= 0.2f) {
                        PosePoint(rightWrist.position.x, rightWrist.position.y, rightWrist.inFrameLikelihood)
                    } else {
                        PosePoint(rElbowPt.x, rElbowPt.y + 50f, 0.4f)
                    }

                    val lHipPt = leftHip?.let { PosePoint(it.position.x, it.position.y, it.inFrameLikelihood) }
                    val rHipPt = rightHip?.let { PosePoint(it.position.x, it.position.y, it.inFrameLikelihood) }

                    // Both arms in view: both arms must have genuine elbow or wrist detection
                    val hasLeftArm = (hasLeftElbow || hasLeftWrist) && hasLeftShoulder
                    val hasRightArm = (hasRightElbow || hasRightWrist) && hasRightShoulder
                    val isBothHandsDetected = hasLeftArm && hasRightArm

                    val leftElbowAngle = PushupRepDetector.calculateJointAngle(lShoulderPt, lElbowPt, lWristPt)
                    val rightElbowAngle = PushupRepDetector.calculateJointAngle(rShoulderPt, rElbowPt, rWristPt)

                    val dx = kotlin.math.abs(rShoulderPt.x - lShoulderPt.x)
                    val dy = kotlin.math.abs(rShoulderPt.y - lShoulderPt.y)
                    val shoulderTilt = Math.toDegrees(kotlin.math.atan2(dy.toDouble(), dx.toDouble())).toFloat()

                    val midShoulder = PosePoint(
                        (lShoulderPt.x + rShoulderPt.x) / 2f,
                        (lShoulderPt.y + rShoulderPt.y) / 2f,
                        (lShoulderPt.confidence + rShoulderPt.confidence) / 2f
                    )
                    val midElbow = PosePoint(
                        (lElbowPt.x + rElbowPt.x) / 2f,
                        (lElbowPt.y + rElbowPt.y) / 2f,
                        (lElbowPt.confidence + rElbowPt.confidence) / 2f
                    )
                    val midWrist = PosePoint(
                        (lWristPt.x + rWristPt.x) / 2f,
                        (lWristPt.y + rWristPt.y) / 2f,
                        (lWristPt.confidence + rWristPt.confidence) / 2f
                    )
                    val midHip = if (lHipPt != null && rHipPt != null) {
                        PosePoint((lHipPt.x + rHipPt.x) / 2f, (lHipPt.y + rHipPt.y) / 2f, (lHipPt.confidence + rHipPt.confidence) / 2f)
                    } else {
                        PosePoint(midShoulder.x, midShoulder.y + 120f, 0.5f)
                    }

                    // Knee pushup check in front view: if knees are visible and ankles are elevated
                    val hasKnee = (leftKnee != null && leftKnee.inFrameLikelihood >= 0.2f) ||
                            (rightKnee != null && rightKnee.inFrameLikelihood >= 0.2f)
                    val isKneePushup = hasKnee && (leftAnkle != null || rightAnkle != null)

                    val confidenceList = listOfNotNull(leftShoulder, rightShoulder, leftElbow, rightElbow, leftWrist, rightWrist)
                    val avgConfidence = confidenceList.map { it.inFrameLikelihood }.average().toFloat()

                    return PoseKeypoints(
                        side = BodySide.FRONT,
                        shoulder = midShoulder,
                        elbow = midElbow,
                        wrist = midWrist,
                        hip = midHip,
                        knee = midHip,
                        ankle = midHip,
                        averageConfidence = avgConfidence,
                        leftShoulder = lShoulderPt,
                        leftElbow = lElbowPt,
                        leftWrist = lWristPt,
                        rightShoulder = rShoulderPt,
                        rightElbow = rElbowPt,
                        rightWrist = rWristPt,
                        leftHip = lHipPt,
                        rightHip = rHipPt,
                        isBothHandsDetected = isBothHandsDetected,
                        leftElbowAngle = leftElbowAngle,
                        rightElbowAngle = rightElbowAngle,
                        shoulderTiltDegrees = shoulderTilt,
                        hasAnkle = false,
                        isKneePushup = isKneePushup
                    )
                }
            }

            // Fallback to SIDE view (LEFT or RIGHT profile)
            val leftScore = listOfNotNull(leftShoulder, leftElbow, leftWrist, leftHip)
                .map { it.inFrameLikelihood }.average().toFloat()

            val rightScore = listOfNotNull(rightShoulder, rightElbow, rightWrist, rightHip)
                .map { it.inFrameLikelihood }.average().toFloat()

            return if (leftScore >= rightScore && leftShoulder != null && leftElbow != null && leftWrist != null && leftHip != null) {
                val hasAnkle = leftAnkle != null && leftAnkle.inFrameLikelihood >= minConf
                val hasKnee = leftKnee != null && leftKnee.inFrameLikelihood >= minConf

                val anklePoint = if (hasAnkle) {
                    PosePoint(leftAnkle.position.x, leftAnkle.position.y, leftAnkle.inFrameLikelihood)
                } else if (hasKnee) {
                    PosePoint(leftKnee.position.x, leftKnee.position.y, leftKnee.inFrameLikelihood)
                } else {
                    PosePoint(leftHip.position.x + 100f, leftHip.position.y, 0.4f)
                }

                val kneePoint = if (hasKnee) {
                    PosePoint(leftKnee.position.x, leftKnee.position.y, leftKnee.inFrameLikelihood)
                } else {
                    anklePoint
                }

                val lShoulderPt = PosePoint(leftShoulder.position.x, leftShoulder.position.y, leftShoulder.inFrameLikelihood)
                val lElbowPt = PosePoint(leftElbow.position.x, leftElbow.position.y, leftElbow.inFrameLikelihood)
                val lWristPt = PosePoint(leftWrist.position.x, leftWrist.position.y, leftWrist.inFrameLikelihood)
                val elbowAngle = PushupRepDetector.calculateJointAngle(lShoulderPt, lElbowPt, lWristPt)

                // Detect Knee Pushup: knee is lower/grounded and ankle is lifted or bent
                val isKnee = hasKnee && (anklePoint.y < kneePoint.y + 15f || !hasAnkle)

                PoseKeypoints(
                    side = BodySide.LEFT,
                    shoulder = lShoulderPt,
                    elbow = lElbowPt,
                    wrist = lWristPt,
                    hip = PosePoint(leftHip.position.x, leftHip.position.y, leftHip.inFrameLikelihood),
                    knee = kneePoint,
                    ankle = anklePoint,
                    averageConfidence = leftScore,
                    leftShoulder = lShoulderPt,
                    leftElbow = lElbowPt,
                    leftWrist = lWristPt,
                    isBothHandsDetected = true,
                    leftElbowAngle = elbowAngle,
                    hasAnkle = hasAnkle,
                    isKneePushup = isKnee
                )
            } else if (rightShoulder != null && rightElbow != null && rightWrist != null && rightHip != null) {
                val hasAnkle = rightAnkle != null && rightAnkle.inFrameLikelihood >= minConf
                val hasKnee = rightKnee != null && rightKnee.inFrameLikelihood >= minConf

                val anklePoint = if (hasAnkle) {
                    PosePoint(rightAnkle.position.x, rightAnkle.position.y, rightAnkle.inFrameLikelihood)
                } else if (hasKnee) {
                    PosePoint(rightKnee.position.x, rightKnee.position.y, rightKnee.inFrameLikelihood)
                } else {
                    PosePoint(rightHip.position.x + 100f, rightHip.position.y, 0.4f)
                }

                val kneePoint = if (hasKnee) {
                    PosePoint(rightKnee.position.x, rightKnee.position.y, rightKnee.inFrameLikelihood)
                } else {
                    anklePoint
                }

                val rShoulderPt = PosePoint(rightShoulder.position.x, rightShoulder.position.y, rightShoulder.inFrameLikelihood)
                val rElbowPt = PosePoint(rightElbow.position.x, rightElbow.position.y, rightElbow.inFrameLikelihood)
                val rWristPt = PosePoint(rightWrist.position.x, rightWrist.position.y, rightWrist.inFrameLikelihood)
                val elbowAngle = PushupRepDetector.calculateJointAngle(rShoulderPt, rElbowPt, rWristPt)

                val isKnee = hasKnee && (anklePoint.y < kneePoint.y + 15f || !hasAnkle)

                PoseKeypoints(
                    side = BodySide.RIGHT,
                    shoulder = rShoulderPt,
                    elbow = rElbowPt,
                    wrist = rWristPt,
                    hip = PosePoint(rightHip.position.x, rightHip.position.y, rightHip.inFrameLikelihood),
                    knee = kneePoint,
                    ankle = anklePoint,
                    averageConfidence = rightScore,
                    rightShoulder = rShoulderPt,
                    rightElbow = rElbowPt,
                    rightWrist = rWristPt,
                    isBothHandsDetected = true,
                    rightElbowAngle = elbowAngle,
                    hasAnkle = hasAnkle,
                    isKneePushup = isKnee
                )
            } else {
                null
            }
        }
    }
}
