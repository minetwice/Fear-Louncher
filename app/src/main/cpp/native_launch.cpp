#include <jni.h>
#include <android/log.h>
#include <cstdio>
#include <cstring>
#include <dlfcn.h>

#define TAG "NativeLaunch"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

typedef jint (*JNI_CreateJavaVM_t)(JavaVM**, JNIEnv**, void*);

extern "C" JNIEXPORT jint JNICALL
Java_com_fearlauncher_launcher_GameLauncher_startMinecraftNative(
        JNIEnv* env, jobject thiz,
        jstring j_jrePath,
        jstring j_jarPath,
        jstring j_version,
        jstring j_assetsPath,
        jstring j_nativesPath) {

    // Convert Java strings to C strings
    const char* c_jrePath = env->GetStringUTFChars(j_jrePath, nullptr);
    const char* c_jarPath = env->GetStringUTFChars(j_jarPath, nullptr);
    const char* c_version = env->GetStringUTFChars(j_version, nullptr);
    const char* c_assetsPath = env->GetStringUTFChars(j_assetsPath, nullptr);
    const char* c_nativesPath = env->GetStringUTFChars(j_nativesPath, nullptr);

    // Declare ALL variables at the start
    void* handle = nullptr;
    JNI_CreateJavaVM_t createVM = nullptr;
    JavaVM* vm = nullptr;
    JNIEnv* jni_env = nullptr;
    jint res = -1;
    jclass mainClass = nullptr;
    jmethodID mainMethod = nullptr;
    jobjectArray args = nullptr;
    jclass stringClass = nullptr;
    
    // C-style buffers for paths
    char libjvm_path[1024] = {0};
    char classpath[4096] = {0};
    char libPath[1024] = {0};
    
    // JVM option strings (static)
    char opt_classpath[] = "-Djava.class.path";
    char opt_libpath[] = "-Djava.library.path";
    char opt_lwjglpath[] = "-Dorg.lwjgl.librarypath";
    char opt_xmx[] = "-Xmx2G";
    char opt_xms[] = "-Xms1G";    
    JavaVMOption options[5];

    // 1. Build paths using sprintf (safe with fixed buffer sizes)
    sprintf(libjvm_path, "%s/lib/server/libjvm.so", c_jrePath);
    sprintf(libPath, "%s", c_nativesPath);
    
    // Build classpath: jarPath:jrePath/lib/*:jrePath/lib/ext/*
    strcpy(classpath, c_jarPath);
    strcat(classpath, ":");
    strcat(classpath, c_jrePath);
    strcat(classpath, "/lib/*:");
    strcat(classpath, c_jrePath);
    strcat(classpath, "/lib/ext/*");

    // 2. Load libjvm.so
    handle = dlopen(libjvm_path, RTLD_LAZY | RTLD_GLOBAL);
    if (!handle) {
        LOGE("Failed to load libjvm.so: %s", dlerror());
        goto cleanup;
    }

    // Get pointer to JNI_CreateJavaVM function
    createVM = (JNI_CreateJavaVM_t)dlsym(handle, "JNI_CreateJavaVM");
    if (!createVM) {
        LOGE("Failed to find JNI_CreateJavaVM");
        goto cleanup;
    }

    // 3. Setup JVM Options
    options[0].optionString = opt_classpath;
    options[0].extraInfo = classpath;
    
    options[1].optionString = opt_xmx;
    options[1].extraInfo = nullptr;
    
    options[2].optionString = opt_xms;
    options[2].extraInfo = nullptr;
    
    options[3].optionString = opt_libpath;
    options[3].extraInfo = libPath;
    
    options[4].optionString = opt_lwjglpath;
    options[4].extraInfo = libPath;

    JavaVMInitArgs vm_args;
    vm_args.version = 0x00010008;  // Hardcoded JNI_VERSION_1_8
    vm_args.nOptions = 5;
    vm_args.options = options;
    vm_args.ignoreUnrecognized = JNI_TRUE;
    // 4. Create the Java VM
    res = createVM(&vm, &jni_env, &vm_args);
    if (res != JNI_OK || !vm || !jni_env) {
        LOGE("JVM Creation Failed: %d", res);
        goto cleanup;
    }

    LOGI("JVM Created Successfully");

    // 5. Find Minecraft Main Class
    mainClass = jni_env->FindClass("net/minecraft/client/main/Main");
    if (!mainClass) {
        LOGE("net.minecraft.client.main.Main not found");
        if (jni_env->ExceptionCheck()) jni_env->ExceptionDescribe();
        goto cleanup;
    }

    // 6. Get Main Method ID
    mainMethod = jni_env->GetStaticMethodID(mainClass, "main", "([Ljava/lang/String;)V");
    if (!mainMethod) {
        LOGE("main() method not found");
        if (jni_env->ExceptionCheck()) jni_env->ExceptionDescribe();
        goto cleanup;
    }

    // 7. Build Args Array
    stringClass = jni_env->FindClass("java/lang/String");
    if (stringClass) {
        args = jni_env->NewObjectArray(10, stringClass, nullptr);
        if (args) {
            int idx = 0;
            jni_env->SetObjectArrayElement(args, idx++, jni_env->NewStringUTF("--version"));
            jni_env->SetObjectArrayElement(args, idx++, jni_env->NewStringUTF(c_version));
            jni_env->SetObjectArrayElement(args, idx++, jni_env->NewStringUTF("--gameDir"));
            jni_env->SetObjectArrayElement(args, idx++, jni_env->NewStringUTF(c_jrePath));
            jni_env->SetObjectArrayElement(args, idx++, jni_env->NewStringUTF("--assetsDir"));
            jni_env->SetObjectArrayElement(args, idx++, jni_env->NewStringUTF(c_assetsPath));
            jni_env->SetObjectArrayElement(args, idx++, jni_env->NewStringUTF("--assetIndex"));
            jni_env->SetObjectArrayElement(args, idx++, jni_env->NewStringUTF(c_version));
            jni_env->SetObjectArrayElement(args, idx++, jni_env->NewStringUTF("--username"));
            jni_env->SetObjectArrayElement(args, idx++, jni_env->NewStringUTF("FearPlayer"));
        }
    }

    // 8. Launch Minecraft
    LOGI("Launching Minecraft via JVM...");
    jni_env->CallStaticVoidMethod(mainClass, mainMethod, args);

    if (jni_env->ExceptionCheck()) {        LOGE("Exception during execution:");
        jni_env->ExceptionDescribe();
        jni_env->ExceptionClear();
    }

    // 9. Cleanup - Destroy JVM
    if (vm) vm->DestroyJavaVM();

cleanup:
    // Close handle
    if (handle) dlclose(handle);
    
    // Release Java strings
    env->ReleaseStringUTFChars(j_jrePath, c_jrePath);
    env->ReleaseStringUTFChars(j_jarPath, c_jarPath);
    env->ReleaseStringUTFChars(j_version, c_version);
    env->ReleaseStringUTFChars(j_assetsPath, c_assetsPath);
    env->ReleaseStringUTFChars(j_nativesPath, c_nativesPath);

    return 0;
}
