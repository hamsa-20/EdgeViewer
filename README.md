# EdgeViewer – Real-Time Edge Detection (Android + Native C++ + Web)

EdgeViewer is a real-time visual processing system built using:

- Android (Camera2 + Jetpack Compose)
- NDK + JNI + C++ (OpenCV pipeline)
- Optional Web Interface (Next.js + TypeScript + OpenCV.js)

The system captures frames from the device camera, processes them natively using C++ OpenCV, and displays the output in real time.

---

## Features

### Android Application
- Real-time camera frame processing using Camera2 API
- Native C++ backend using OpenCV 4.x
- Hardware-accelerated rendering with OpenGL ES
- Toggle between normal camera view and edge detection mode
- Optimized frame pipeline (30 FPS camera, ~3 FPS processing)
- Camera permission handling
- Jetpack Compose UI with:
  - Live preview canvas
  - FPS indicator
  - Processing time
  - Start/Stop controls

### Web Interface
- Modern Next.js application (App Router)
- Drag-and-drop or button-based image upload
- Real-time edge detection using OpenCV.js
- Adjustable threshold and kernel size
- Download processed output image
- Responsive interface for desktop and mobile

---

## Screenshots

| Camera Preview | Edge Output | Additional UI |
|----------------|-------------|----------------|
| ![](images/screen1.jpg) | ![](images/screen2.jpg) | ![](images/screen3.jpg) |

---

## Tech Stack

### Android
- Kotlin
- Jetpack Compose
- Camera2 API
- OpenCV 4.x (native)
- C++17 with NDK + JNI
- OpenGL ES 2.0
- Gradle (Kotlin DSL)
- Target SDK: 34
- Minimum SDK: 24

### Web
- Next.js 14
- TypeScript
- Tailwind CSS
- OpenCV.js
- npm / yarn

### Native Backend
- C++17
- OpenCV
- EGL + OpenGL ES
- CMake 3.22.1

---


## Project directory structure

EdgeViewer/
│
├── app/
│   ├── src/main/
│   │   ├── java/com/hamsa/edgeviewer/
│   │   │   ├── MainActivity.kt
│   │   │   ├── CameraHelper.kt
│   │   │   └── NativeBridge.kt
│   │   │
│   │   ├── cpp/
│   │   │   ├── native-lib.cpp
│   │   │   ├── opencv_processor.cpp
│   │   │   └── opencv_processor.h
│   │   │
│   │   └── res/
│   │
│   ├── build.gradle.kts
│   └── AndroidManifest.xml
│
├── opencv/  (ignored)
│   └── sdk/native/libs/…
│
├── web/
│   ├── src/
│   │   ├── app/
│   │   └── components/
│   ├── package.json
│   ├── tsconfig.json
│   └── README.md
│
├── images/
│   ├── screen1.jpg
│   ├── screen2.jpg
│   └── screen3.jpg
│
├── build.gradle.kts
└── README.md

---

## Prerequisites

### Android Development
- Android Studio 2023+
- Android SDK 34
- NDK 23+
- CMake 3.22.1+
- OpenCV Android SDK 4.x

### Web Development
- Node.js 18+
- npm or yarn

---

## Android Setup

### Clone the repository

git clone https://github.com/yourname/EdgeViewer.git
cd EdgeViewer

Open in Android Studio

Open Android Studio

Choose "Open Existing Project"

Select the EdgeViewer folder

Add OpenCV Android SDK

Download from https://opencv.org/releases/

Place extracted folder at:

app/opencv/sdk/native/

---
Build the project

./gradlew clean
./gradlew :app:assembleDebug

---

Run on device

Enable USB debugging

Connect Android phone

Run the app from Android Studio

---

Web Interface Setup

Navigate to web folder
cd web

Install dependencies
npm install

Start development server
npm start

Open Browser in:
http://localhost:3000

---
Configuration
Android build.gradle.kts

android {
    compileSdk = 34
    minSdk = 24
    targetSdk = 34

    externalNativeBuild {
        cmake {
            cppFlags += "-std=c++17"
        }
    }

    ndk {
        abiFilters += listOf("arm64-v8a", "armeabi-v7a")
    }
}

---
Web package.json includes:

Next.js 14

React 18

TypeScript 5

Tailwind CSS 3

---

Usage
Android App

Open the app on the device

Grant camera permissions

Press "Start Camera"

Toggle edge detection

Observe real-time processed output


---
Web Interface

Open web demo

Upload an image

Adjust parameters (threshold, kernel size)

View processed output

Download image if needed

---

Architecture

Android Processing Pipeline
Jetpack Compose UI
        ↓
Camera2 (YUV_420_888)
        ↓
ImageReader → NV21 conversion
        ↓
JNI bridge → native-lib.cpp
        ↓
OpenCV C++ (Canny, Sobel, Gaussian)
        ↓
OpenGL / Bitmap output
        ↓
Compose Canvas rendering

---

Web Processing Pipeline

Next.js UI
        ↓
Image upload / drag-drop
        ↓
OpenCV.js (Canny)
        ↓
Canvas rendering
        ↓
Download output

---
Algorithms
Implemented

Canny edge detection

Sobel operator

Gaussian blur

Custom thresholding

Optimizations

Frame skipping (process every 10th frame)

Efficient bitmap recycling

Non-blocking worker threads

Native C++ for performance

---

Supported Platforms
Android

API 24 to 34

ARM64-v8a, ARMv7 architectures

Web

Chrome

Firefox

Safari

Edge

---

Development

Build Android

./gradlew clean
./gradlew :app:assembleDebug


Build Web

npm run dev
npm run build

Test Android

./gradlew test
./gradlew connectedAndroidTest

Test Web

npm run lint
npx tsc --noEmit

