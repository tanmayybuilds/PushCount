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
                val result = repDetector.processKeypoints(keypoints)
                onResult(result, imageWidth, imageHeight, rotationDegrees == 90 || rotationDegrees == 270)
            }
            .addOnFailureListener {
                val result = repDetector.processKeypoints(null)
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

            val minConf = 0.35f

            val hasLeftShoulder = leftShoulder != null && leftShoulder.inFrameLikelihood >= minConf
            val hasRightShoulder = rightShoulder != null && rightShoulder.inFrameLikelihood >= minConf
            val hasLeftElbow = leftElbow != null && leftElbow.inFrameLikelihood >= minConf
            val hasRightElbow = rightElbow != null && rightElbow.inFrameLikelihood >= minConf
            val hasLeftWrist = leftWrist != null && leftWrist.inFrameLikelihood >= minConf
            val hasRightWrist = rightWrist != null && rightWrist.inFrameLikelihood >= minConf

            // Check if user is positioned facing the camera (FRONT orientation)
            if (hasLeftShoulder && hasRightShoulder) {
                val shoulderWidth = kotlin.math.abs(leftShoulder.position.x - rightShoulder.position.x)
                val isFrontOrientation = shoulderWidth >= 50f && (
                    (hasLeftElbow && hasRightElbow) ||
                    (hasLeftWrist && hasRightWrist) ||
                    (leftShoulder.inFrameLikelihood >= 0.5f && rightShoulder.inFrameLikelihood >= 0.5f && shoulderWidth >= 70f)
                )

                if (isFrontOrientation) {
                    val lShoulderPt = PosePoint(leftShoulder.position.x, leftShoulder.position.y, leftShoulder.inFrameLikelihood)
                    val rShoulderPt = PosePoint(rightShoulder.position.x, rightShoulder.position.y, rightShoulder.inFrameLikelihood)

                    val lElbowPt = if (leftElbow != null) {
                        PosePoint(leftElbow.position.x, leftElbow.position.y, leftElbow.inFrameLikelihood)
                    } else {
                        PosePoint(lShoulderPt.x - 40f, lShoulderPt.y + 60f, 0f)
                    }

                    val rElbowPt = if (rightElbow != null) {
                        PosePoint(rightElbow.position.x, rightElbow.position.y, rightElbow.inFrameLikelihood)
                    } else {
                        PosePoint(rShoulderPt.x + 40f, rShoulderPt.y + 60f, 0f)
                    }

                    val lWristPt = if (leftWrist != null) {
                        PosePoint(leftWrist.position.x, leftWrist.position.y, leftWrist.inFrameLikelihood)
                    } else {
                        PosePoint(lElbowPt.x, lElbowPt.y + 60f, 0f)
                    }

                    val rWristPt = if (rightWrist != null) {
                        PosePoint(rightWrist.position.x, rightWrist.position.y, rightWrist.inFrameLikelihood)
                    } else {
                        PosePoint(rElbowPt.x, rElbowPt.y + 60f, 0f)
                    }

                    val lHipPt = leftHip?.let { PosePoint(it.position.x, it.position.y, it.inFrameLikelihood) }
                    val rHipPt = rightHip?.let { PosePoint(it.position.x, it.position.y, it.inFrameLikelihood) }

                    val isBothHandsDetected = hasLeftWrist && hasRightWrist && hasLeftElbow && hasRightElbow

                    val leftElbowAngle = if (hasLeftShoulder && hasLeftElbow && hasLeftWrist) {
                        PushupRepDetector.calculateJointAngle(lShoulderPt, lElbowPt, lWristPt)
                    } else 0f

                    val rightElbowAngle = if (hasRightShoulder && hasRightElbow && hasRightWrist) {
                        PushupRepDetector.calculateJointAngle(rShoulderPt, rElbowPt, rWristPt)
                    } else 0f

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
                        hasAnkle = false
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
                val anklePoint = if (hasAnkle) {
                    PosePoint(leftAnkle.position.x, leftAnkle.position.y, leftAnkle.inFrameLikelihood)
                } else if (leftKnee != null && leftKnee.inFrameLikelihood >= minConf) {
                    PosePoint(leftKnee.position.x, leftKnee.position.y, leftKnee.inFrameLikelihood)
                } else {
                    PosePoint(leftHip.position.x + 100f, leftHip.position.y, 0.4f)
                }

                val kneePoint = if (leftKnee != null) {
                    PosePoint(leftKnee.position.x, leftKnee.position.y, leftKnee.inFrameLikelihood)
                } else {
                    anklePoint
                }

                val lShoulderPt = PosePoint(leftShoulder.position.x, leftShoulder.position.y, leftShoulder.inFrameLikelihood)
                val lElbowPt = PosePoint(leftElbow.position.x, leftElbow.position.y, leftElbow.inFrameLikelihood)
                val lWristPt = PosePoint(leftWrist.position.x, leftWrist.position.y, leftWrist.inFrameLikelihood)
                val elbowAngle = PushupRepDetector.calculateJointAngle(lShoulderPt, lElbowPt, lWristPt)

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
                    hasAnkle = hasAnkle
                )
            } else if (rightShoulder != null && rightElbow != null && rightWrist != null && rightHip != null) {
                val hasAnkle = rightAnkle != null && rightAnkle.inFrameLikelihood >= minConf
                val anklePoint = if (hasAnkle) {
                    PosePoint(rightAnkle.position.x, rightAnkle.position.y, rightAnkle.inFrameLikelihood)
                } else if (rightKnee != null && rightKnee.inFrameLikelihood >= minConf) {
                    PosePoint(rightKnee.position.x, rightKnee.position.y, rightKnee.inFrameLikelihood)
                } else {
                    PosePoint(rightHip.position.x + 100f, rightHip.position.y, 0.4f)
                }

                val kneePoint = if (rightKnee != null) {
                    PosePoint(rightKnee.position.x, rightKnee.position.y, rightKnee.inFrameLikelihood)
                } else {
                    anklePoint
                }

                val rShoulderPt = PosePoint(rightShoulder.position.x, rightShoulder.position.y, rightShoulder.inFrameLikelihood)
                val rElbowPt = PosePoint(rightElbow.position.x, rightElbow.position.y, rightElbow.inFrameLikelihood)
                val rWristPt = PosePoint(rightWrist.position.x, rightWrist.position.y, rightWrist.inFrameLikelihood)
                val elbowAngle = PushupRepDetector.calculateJointAngle(rShoulderPt, rElbowPt, rWristPt)

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
                    hasAnkle = hasAnkle
                )
            } else {
                null
            }
        }
    }
}
