# PushCount 🏋️‍♂️

> A lightweight, open-source Android pushup counter powered by on-device AI pose detection and strict posture validation. Zero telemetry, zero network calls, 100% on-device.

---

## Highlights

- **Real-Time Pose Tracking**: Powered by ML Kit Pose Detection running entirely on-device via CameraX.
- **Strict Posture Gating**: Only counts reps completed with a straight plank posture. Hips sagging or piking too high invalidate the rep immediately.
- **5-State Motion Engine**: State machine (`IDLE` ➔ `GOING_DOWN` ➔ `BOTTOM` ➔ `GOING_UP` ➔ `REP_COMPLETE`) with moving-average noise filtering.
- **Privacy-First (Zero Network)**: No network permissions, no external server calls, no telemetry. Everything is stored locally via Room and Jetpack DataStore.
- **Creative Minimalist Design**: Material 3 theming with full light/dark mode support, confident typography, monthly activity heatmaps, and streak tracking.

---

## Architecture & Code Structure

```
app/src/main/java/com/example/
├── data/
│   ├── local/          # Room database, entity, and DAO definitions
│   ├── model/          # Workout session models
│   ├── preferences/    # Jetpack DataStore user preferences
│   └── repository/     # WorkoutRepository abstraction with Flow streams
├── posedetection/
│   ├── model/          # Keypoint landmarks, rep state machine enums
│   ├── MovingAverageFilter.kt  # Frame noise smoothing
│   ├── PoseDetectorProcessor.kt # CameraX ImageAnalysis adapter
│   ├── PushupDetectorConfig.kt  # Centralized threshold tuning
│   └── PushupRepDetector.kt     # Pure-logic 5-state rep counter & angle math
├── di/                 # Manual dependency injection container
├── ui/
│   ├── components/     # StatCard, GoalProgressRing, StreakBadge, PostureBanner
│   ├── navigation/     # Jetpack Navigation Compose routes
│   ├── screens/        # Home, Workout, History, Settings screens
│   └── theme/          # Material 3 colors, typography, and themes
└── MainActivity.kt     # App entry point and root NavHost
```

---

## Posture Validation Algorithm

1. **Active Side Tracking**: Evaluates Left vs. Right joint landmarks on each frame, dynamically selecting the side with higher detection confidence.
2. **Elbow Joint Angle**: Computed at the elbow vertex ($\vec{SE}$ and $\vec{WE}$).
   - **Lockout / Extension**: $> 160^\circ$
   - **Full Depth**: $\le 90^\circ$
3. **Plank Straightness Angle**: Computed at the hip vertex between shoulder, hip, and ankle.
   - **Tolerance Band**: Form is considered valid if the hip angle remains within $180^\circ \pm 25^\circ$.
   - **Hips Sagging vs Hips Too High**: Signed perpendicular displacement from the Shoulder-Ankle line determines the exact corrective cue.

---

## Building and Running

Prerequisites:
- Android Studio Ladybug or newer
- JDK 17
- Android SDK 35 (minimum SDK 24)

```bash
# Build debug APK
./gradlew assembleDebug

# Run unit tests
./gradlew testDebugUnitTest
```

---

## License

PushCount is licensed under the [Apache License, Version 2.0](LICENSE).
