#include <jni.h>
#include <string>
#include <opencv2/opencv.hpp>
#include <android/log.h>

#define LOG_TAG "edgeviewer"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

using namespace cv;

extern "C" {

JNIEXPORT jbyteArray JNICALL
Java_com_hamsa_edgeviewer_NativeBridge_processFrameNV21(
        JNIEnv *env,
        jobject /* this */,
        jbyteArray nv21,
        jint width,
        jint height,
        jint rotation) {

    jbyte *nv21Bytes = env->GetByteArrayElements(nv21, nullptr);
    if (!nv21Bytes) {
        LOGE("Failed to get nv21 bytes");
        return nullptr;
    }

    jbyteArray outArray = nullptr;
    try {
        LOGI("Native process: %d x %d rot=%d", width, height, rotation);

        // Create YUV Mat - NV21 layout: height + height/2 rows, width cols
        Mat yuv(height + height/2, width, CV_8UC1, (uchar*)nv21Bytes);

        // Convert to RGBA
        Mat rgba;
        cvtColor(yuv, rgba, COLOR_YUV2RGBA_NV21);

        // Rotate if needed (rotation is 0/90/180/270)
        Mat rotated;
        if (rotation == 90) {
            // rotate 90 clockwise
            transpose(rgba, rotated);
            flip(rotated, rotated, 1);
        } else if (rotation == 270) {
            // rotate 270 clockwise (or 90 ccw)
            transpose(rgba, rotated);
            flip(rotated, rotated, 0);
        } else if (rotation == 180) {
            flip(rgba, rotated, -1);
        } else {
            rotated = rgba;
        }

        // Convert to gray and run Canny
        Mat gray, edges;
        cvtColor(rotated, gray, COLOR_RGBA2GRAY);
        Canny(gray, edges, 50, 150);

        // Convert edges to RGBA so Android can display
        Mat edgesRGBA;
        cvtColor(edges, edgesRGBA, COLOR_GRAY2RGBA);

        // Create output byte array (RGBA: 4 bytes per pixel)
        int outWidth = edgesRGBA.cols;
        int outHeight = edgesRGBA.rows;
        int outSize = outWidth * outHeight * 4;
        outArray = env->NewByteArray(outSize);
        env->SetByteArrayRegion(outArray, 0, outSize, (jbyte*)edgesRGBA.data);

        // release and return
        env->ReleaseByteArrayElements(nv21, nv21Bytes, JNI_ABORT);
        LOGI("Native processing done");
        return outArray;

    } catch (const cv::Exception &e) {
        LOGE("OpenCV exception: %s", e.what());
        if (nv21Bytes) env->ReleaseByteArrayElements(nv21, nv21Bytes, JNI_ABORT);
        return nullptr;
    } catch (...) {
        LOGE("Unknown exception");
        if (nv21Bytes) env->ReleaseByteArrayElements(nv21, nv21Bytes, JNI_ABORT);
        return nullptr;
    }
}

} // extern "C"
