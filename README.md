# 👁️ OpticAlarm

## 📖 Brief Introduction
OpticAlarm is an advanced, AI-driven Android application built with Jetpack Compose that revolutionizes the traditional wake-up experience by requiring real-world, physical interaction. Instead of simply pressing a button, users must actively engage with their environment and computer vision to silence the alarm.

## ⚠️ Problem Being Addressed
Conventional smartphone alarms suffer from "alarm fatigue" and muscle memory dismissal. Users frequently turn off alarms subconsciously or sleep through them without achieving full cognitive wakefulness.

## 🎯 Main Objective
To guarantee the user physically leaves their bed and engages their cognitive functions by enforcing a dynamic, machine-learning-based challenge to silence the alarm, while ensuring foolproof execution on highly restrictive Android operating systems.

## 💡 Proposed Solution
OpticAlarm utilizes on-device machine learning and camera hardware to enforce an object-scanning protocol. The custom ringtone will not silence until the user physically locates and scans a specific pre-selected household item (like a cup or toothbrush). If the user fails to do so within 15 seconds, a cognitive fallback challenge forces them to type a visible quote with the system keyboard autocorrect explicitly disabled.

## 🛠️ Tech Stack

| Category | Technology | Description |
| :--- | :--- | :--- |
| **Language** | Kotlin | Primary programming language. |
| **UI Framework** | Jetpack Compose | Declarative UI toolkit for building native reactive Android interfaces. |
| **Machine Learning** | TensorFlow Lite | On-device ML inference engine running a YOLOv8 Nano model. |
| **Vision API** | CameraX | Android Jetpack API for live preview and real-time image analysis. |
| **Background Processing** | AlarmManager & BroadcastReceiver | OS-level exact scheduling and system-level event interception. |
| **Build Optimization** | ProGuard & ABI Filters | `arm64-v8a` native library filtering and code shrinking to drastically reduce APK size. |

## ✨ Key Features
*   **Real-Time Object Detection Engine:** Integrates the Ultralytics YOLOv8 Nano model via TensorFlow Lite, processing live frames to detect distinct COCO dataset classes in real-time.
*   **Strict Onboarding State Machine:** A sequential 5-step permission funnel uniquely designed to override custom manufacturer (Vivo/MIUI) lock-screen, battery, and autostart restrictions.
*   **Hybrid Cognitive Fallback:** A 15-second emergency countdown triggering a quote-typing challenge.
*   **Zero-Cloud Privacy:** All ML inference runs locally on the user's `arm64-v8a` hardware without requiring any network transmission or external API calls.

## 🚀 How It Addresses the Problem
By requiring the user to navigate to a different room to locate a physical object, the system forces physical movement and environmental interaction. This effectively breaks the cycle of unconscious snoozing.

## 🏗️ System/Project Architecture
OpticAlarm bridges a reactive declarative UI with a continuous ML processing loop:
*   **Presentation Layer:** Built entirely in Jetpack Compose to seamlessly handle real-time countdowns and the dynamic `AndroidView` camera viewports.
*   **Vision Layer:** CameraX captures continuous high-resolution image frames and feeds them directly to the YOLOv8 ML pipeline for bounding box and confidence score generation.
*   **Background Execution:** Relies on exact system alarms combined with OS window overlay flags (`FLAG_SHOW_WHEN_LOCKED` and `SYSTEM_ALERT_WINDOW`) to instantly bypass lock screens.

## 🧩 Main Components & Modules
The core architecture inside the `com.mohan.alarm` package consists of:
*   **`ui.theme` (`Color.kt`, `Theme.kt`, `Type.kt`):** Defines the app's cyberpunk neon blue color palette and global Jetpack Compose material typography.
*   **`MainActivity.kt`:** The primary UI entry point handling the target object selection and background execution permission flow.
*   **`AlarmScheduler`:** Registers exact time intents with the native Android `AlarmManager`.
*   **`AlarmReceiver`:** The `BroadcastReceiver` that intercepts the OS tripwire, triggers the ringtone, and forces the app to the foreground.
*   **`AlarmActivity`:** The window container responsible for enforcing the lock-screen bypass.
*   **`AlarmRingScreen.kt`:** The Compose interface rendering the active CameraX feed, real-time YOLO bounding boxes, and the fallback challenge.
*   **`YoloDetectorHelper`:** The ML engine converting CameraX image frames into mathematical Tensors and processing them through the TFLite runtime.

## 📊 Expected/Obtained Results
The application successfully bypasses restrictive manufacturer lock screens to overlay the camera UI natively over locked devices. The implementation of specific ABI filters and ProGuard minification drastically reduced the compiled app size from an unoptimized 430 MB down to a lightweight, production-ready footprint.

## 🏆 Key Outcomes
OpticAlarm delivers a fully functional, highly optimized APK capable of real-time computer vision inference and persistent background execution without memory leaks or OS-level Doze mode interference.

## 🌟 Benefits and Impact
OpticAlarm establishes rigorous morning discipline, improves overall sleep hygiene, and provides a foolproof, technologically advanced solution to chronic oversleeping.
