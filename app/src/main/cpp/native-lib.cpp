#include <jni.h>
#include <string>
#include <opencv2/opencv.hpp>
#include <android/log.h>

#define TAG "NativeLib"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

using namespace cv;

// --- Helper to release JNI byte array safely ---
class JNI_BA_Guard {
public:
    JNI_BA_Guard(JNIEnv *env, jbyteArray arr) : pEnv(env), pArr(arr) {
        pBytes = pEnv->GetByteArrayElements(pArr, nullptr);
    }
    ~JNI_BA_Guard() {
        if (pBytes) {
            pEnv->ReleaseByteArrayElements(pArr, pBytes, JNI_ABORT);
        }
    }
    jbyte* pBytes;
private:
    JNIEnv *pEnv;
    jbyteArray pArr;
};

extern "C"
JNIEXPORT jbyteArray JNICALL
Java_com_hamsa_edgeviewer_NativeBridge_processFrameNV21(
        JNIEnv *env,
        jobject thiz,
        jbyteArray nv21,
        jint width,
        jint height
) {
    JNI_BA_Guard nv21Guard(env, nv21);
    if (!nv21Guard.pBytes) {
        LOGE("Failed to get NV21 byte array elements");
        return nullptr;
    }

    try {
        // --- Wrap NV21 data in a Mat ---
        Mat yuv(height + height / 2, width, CV_8UC1, (unsigned char *)nv21Guard.pBytes);

        // --- Convert to RGBA ---
        Mat rgba;
        cvtColor(yuv, rgba, COLOR_YUV2RGBA_NV21);

        // --- Edge Detection ---
        Mat gray, edges;
        cvtColor(rgba, gray, COLOR_RGBA2GRAY);
        Canny(gray, edges, 80, 150);
        cvtColor(edges, rgba, COLOR_GRAY2RGBA); // Convert back to RGBA

        // --- Create and return result ---
        int outSize = width * height * 4;
        jbyteArray out = env->NewByteArray(outSize);
        env->SetByteArrayRegion(out, 0, outSize, (jbyte*)rgba.data);

        return out;

    } catch (const cv::Exception& e) {
        LOGE("OpenCV exception: %s", e.what());
        return nullptr;
    } catch (...) {
        LOGE("Unknown native exception");
        return nullptr;
    }
}

extern "C"
JNIEXPORT jbyteArray JNICALL
Java_com_hamsa_edgeviewer_NativeBridge_passthroughNV21(
        JNIEnv *env,
        jobject thiz,
        jbyteArray nv21,
        jint width,
        jint height
) {
    JNI_BA_Guard nv21Guard(env, nv21);
    if (!nv21Guard.pBytes) {
        LOGE("Failed to get NV21 byte array elements for passthrough");
        return nullptr;
    }

    try {
        // --- Wrap NV21 data in a Mat ---
        Mat yuv(height + height / 2, width, CV_8UC1, (unsigned char *)nv21Guard.pBytes);

        // --- Convert to RGBA ---
        Mat rgba;
        cvtColor(yuv, rgba, COLOR_YUV2RGBA_NV21);

        // --- Create and return result ---
        int outSize = width * height * 4;
        jbyteArray out = env->NewByteArray(outSize);
        env->SetByteArrayRegion(out, 0, outSize, (jbyte*)rgba.data);

        return out;

    } catch (const cv::Exception& e) {
        LOGE("OpenCV exception in passthrough: %s", e.what());
        return nullptr;
    } catch (...) {
        LOGE("Unknown native exception in passthrough");
        return nullptr;
    }
}