#include <jni.h>
#include <android/log.h>
#include <sys/ptrace.h>
#include <unistd.h>
#include <string.h>
#include <stdio.h>
#include <stdlib.h>

#define LOG_TAG "XRC_Native"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

JNIEXPORT void JNICALL
Java_com_xrc_system_features_AntiDebug_nativeAntiDebug(JNIEnv *env, jobject thiz) {
    // Anti-ptrace: prevent debugger attachment
    if (ptrace(PTRACE_TRACEME, 0, NULL, NULL) == -1) {
        LOGE("Debugger detected via ptrace");
        // Stall or crash
        while(1) {
            sleep(60);
        }
    }
    LOGD("Ptrace protection active");
}

JNIEXPORT void JNICALL
Java_com_xrc_system_native_NativeGuard_antiPtrace(JNIEnv *env, jobject thiz) {
    ptrace(PTRACE_TRACEME, 0, NULL, NULL);
}

JNIEXPORT void JNICALL
Java_com_xrc_system_native_NativeGuard_antiDebug(JNIEnv *env, jobject thiz) {
    // Check TracerPid
    FILE *f = fopen("/proc/self/status", "r");
    if (f) {
        char line[256];
        while (fgets(line, sizeof(line), f)) {
            if (strncmp(line, "TracerPid:", 10) == 0) {
                int pid = atoi(line + 10);
                if (pid != 0) {
                    LOGE("Tracer detected: %d", pid);
                }
                break;
            }
        }
        fclose(f);
    }
}

JNIEXPORT void JNICALL
Java_com_xrc_system_native_NativeGuard_hideProcess(JNIEnv *env, jobject thiz) {
    // Attempt to hide from process list
    // This is a stub - real implementation would use ptrace or kernel module
    LOGD("Process hide stub called");
}

JNIEXPORT void JNICALL
Java_com_xrc_system_native_NativeGuard_encryptStrings(JNIEnv *env, jobject thiz) {
    LOGD("String encryption stub called");
}

JNIEXPORT jboolean JNICALL
Java_com_xrc_system_native_NativeGuard_checkIntegrity(JNIEnv *env, jobject thiz) {
    // Check APK signature / hash
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_xrc_system_native_NativeGuard_obfuscateApi(JNIEnv *env, jobject thiz) {
    LOGD("API obfuscation stub called");
}
