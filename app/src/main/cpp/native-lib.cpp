#include <jni.h>
#include <string>
#include <opencv2/opencv.hpp>
#include <android/log.h>

#define LOG_TAG "EdgeViewerNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

using namespace cv;

extern "C" {

JNIEXPORT jbyteArray JNICALL
Java_com_hamsa_edgeviewer_NativeBridge_processFrameNV21(
        JNIEnv *env,
        jobject thiz,
        jbyteArray nv21,
        jint width,
        jint height) {

    jbyte *nv21Bytes = env->GetByteArrayElements(nv21, nullptr);
    if (!nv21Bytes) {
        LOGE("Failed to get NV21 byte array");
        return nullptr;
    }

    try {
        LOGI("Processing frame: %dx%d", width, height);

        // Create YUV matrix
        Mat yuv(height + height/2, width, CV_8UC1, (uchar*)nv21Bytes);

        // Convert YUV to RGBA
        Mat rgba;
        cvtColor(yuv, rgba, COLOR_YUV2RGBA_NV21);

        // Edge detection pipeline
        Mat gray, edges, edgesRGBA;
        cvtColor(rgba, gray, COLOR_RGBA2GRAY);
        Canny(gray, edges, 50, 150);
        cvtColor(edges, edgesRGBA, COLOR_GRAY2RGBA);

        // Create output byte array
        jsize outSize = width * height * 4;
        jbyteArray out = env->NewByteArray(outSize);
        env->SetByteArrayRegion(out, 0, outSize, (jbyte*)edgesRGBA.data);

        // Cleanup
        env->ReleaseByteArrayElements(nv21, nv21Bytes, JNI_ABORT);

        LOGI("Frame processed successfully");
        return out;

    } catch (const cv::Exception& e) {
        LOGE("OpenCV exception: %s", e.what());
        env->ReleaseByteArrayElements(nv21, nv21Bytes, JNI_ABORT);
        return nullptr;
    } catch (...) {
        LOGE("Unknown exception in processFrameNV21");
        env->ReleaseByteArrayElements(nv21, nv21Bytes, JNI_ABORT);
        return nullptr;
    }
}

JNIEXPORT jbyteArray JNICALL
Java_com_hamsa_edgeviewer_NativeBridge_passthroughNV21(
        JNIEnv *env,
        jobject thiz,
        jbyteArray nv21,
        jint width,
        jint height) {

    jbyte *nv21Bytes = env->GetByteArrayElements(nv21, nullptr);
    if (!nv21Bytes) {
        LOGE("Failed to get NV21 byte array for passthrough");
        return nullptr;
    }

    try {
        LOGI("Passthrough frame: %dx%d", width, height);

        // Create YUV matrix
        Mat yuv(height + height/2, width, CV_8UC1, (uchar*)nv21Bytes);

        // Convert YUV to RGBA (passthrough - no edge detection)
        Mat rgba;
        cvtColor(yuv, rgba, COLOR_YUV2RGBA_NV21);

        // Create output byte array
        jsize outSize = width * height * 4;
        jbyteArray out = env->NewByteArray(outSize);
        env->SetByteArrayRegion(out, 0, outSize, (jbyte*)rgba.data);

        // Cleanup
        env->ReleaseByteArrayElements(nv21, nv21Bytes, JNI_ABORT);

        LOGI("Passthrough completed successfully");
        return out;

    } catch (const cv::Exception& e) {
        LOGE("OpenCV exception in passthrough: %s", e.what());
        env->ReleaseByteArrayElements(nv21, nv21Bytes, JNI_ABORT);
        return nullptr;
    } catch (...) {
        LOGE("Unknown exception in passthroughNV21");
        env->ReleaseByteArrayElements(nv21, nv21Bytes, JNI_ABORT);
        return nullptr;
    }
}

} // extern "C"