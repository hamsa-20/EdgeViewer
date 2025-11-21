# EdgeViewer – Real-Time Edge Detection (Android + Native C++ + Web)

EdgeViewer is a real-time visual processing system that captures frames from an Android device camera, processes them using native C++ OpenCV, and displays the output instantly.  
It also includes an optional modern web interface using Next.js and OpenCV.js.

---

## 🚀 Features

### 📱 Android Application
- Real-time camera processing (Camera2 API)
- Native C++ backend using OpenCV 4.x
- Smooth UI built with Jetpack Compose
- Displays:
  - Live camera preview
  - Real-time edge detection output
  - FPS counter
  - Processing time per frame
- Start/Stop camera controls
- Optimized frame pipeline

### 🌐 Web Interface (Optional)
- Next.js 14 + TypeScript
- Drag-and-drop image upload
- Real-time edge detection with OpenCV.js
- Adjustable parameters
- Download processed image
- Responsive UI

### ⚙️ Native C++ Backend
- C++17 with OpenCV 4.x
- JNI bridge for Android
- Canny / Sobel / Gaussian filters
- Fast NV21 → RGBA conversion
- Optimized for mobile hardware

---

## 📸 Screenshots

| Camera Preview | Edge Output | Additional UI |
|----------------|-------------|----------------|
| ![](images/screen1.jpg) | ![](images/screen2.jpg) | ![](images/screen3.jpg) |

---

## 📂 Project Structure

