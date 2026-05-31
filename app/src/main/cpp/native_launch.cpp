#include <jni.h>
#include <android/log.h>
#include <string>
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

    // Declare ALL variables at the start to avoid scope issues with returns
    void* handle = nullptr;
    auto createVM = (JNI_CreateJavaVM_t)nullptr;
    JavaVM* vm = nullptr;
    JNIEnv* jni_env = nullptr;
    jint res = -1;
    jclass mainClass = nullptr;
    jmethodID mainMethod = nullptr;
    jobjectArray args = nullptr;
    jclass stringClass = nullptr;

    // 1. Load libjvm.so
    std::string libjvm_path = std::string(c_jrePath) + "/lib/server/libjvm.so";
    handle = dlopen(libjvm_path.c_str(), RTLD_LAZY | RTLD_GLOBAL);
    if (!handle) {
        LOGE("Failed to load libjvm.so: %s", dlerror());
        goto cleanup;
    }

    // Get pointer to JNI_CreateJavaVM function
    createVM = (JNI_CreateJavaVM_t)dlsym(handle, "JNI_CreateJavaVM");
    if (!createVM) {
        LOGE("Failed to find JNI_CreateJavaVM");        goto cleanup;
    }

    // 2. JVM Options - FIX: Store strings in variables first
    std::string classpath_str = std::string(c_jarPath) + ":" + 
                                std::string(c_jrePath) + "/lib/*:" + 
                                std::string(c_jrePath) + "/lib/ext/*";
    
    std::string libPath_str = std::string(c_nativesPath);
    
    std::string opt1_str = "-Djava.class.path";
    std::string opt4_str = "-Djava.library.path";
    std::string opt5_str = "-Dorg.lwjgl.librarypath";
    
    JavaVMOption options[5];
    options[0].optionString = const_cast<char*>(opt1_str.c_str());
    options[0].extraInfo = const_cast<char*>(classpath_str.c_str());
    
    options[1].optionString = const_cast<char*>("-Xmx2G");
    options[1].extraInfo = nullptr;
    
    options[2].optionString = const_cast<char*>("-Xms1G");
    options[2].extraInfo = nullptr;
    
    options[3].optionString = const_cast<char*>(opt4_str.c_str());
    options[3].extraInfo = const_cast<char*>(libPath_str.c_str());
    
    options[4].optionString = const_cast<char*>(opt5_str.c_str());
    options[4].extraInfo = const_cast<char*>(libPath_str.c_str());

    JavaVMInitArgs vm_args;
    vm_args.version = 0x00010008;  // Hardcoded JNI_VERSION_1_8
    vm_args.nOptions = 5;
    vm_args.options = options;
    vm_args.ignoreUnrecognized = JNI_TRUE;

    // 3. Create the Java VM
    res = createVM(&vm, &jni_env, &vm_args);
    if (res != JNI_OK || !vm || !jni_env) {
        LOGE("JVM Creation Failed: %d", res);
        goto cleanup;
    }

    LOGI("JVM Created Successfully");

    // 4. Find Minecraft Main Class
    mainClass = jni_env->FindClass("net/minecraft/client/main/Main");
    if (!mainClass) {
        LOGE("net.minecraft.client.main.Main not found");
        if (jni_env->ExceptionCheck()) jni_env->ExceptionDescribe();        goto cleanup;
    }

    // 5. Get Main Method ID
    mainMethod = jni_env->GetStaticMethodID(mainClass, "main", "([Ljava/lang/String;)V");
    if (!mainMethod) {
        LOGE("main() method not found");
        if (jni_env->ExceptionCheck()) jni_env->ExceptionDescribe();
        goto cleanup;
    }

    // 6. Build Args Array
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

    // 7. Launch Minecraft
    LOGI("Launching Minecraft via JVM...");
    jni_env->CallStaticVoidMethod(mainClass, mainMethod, args);

    if (jni_env->ExceptionCheck()) {
        LOGE("Exception during execution:");
        jni_env->ExceptionDescribe();
        jni_env->ExceptionClear();
    }

    // 8. Cleanup - Destroy JVM
    if (vm) vm->DestroyJavaVM();

cleanup:
    // Close handle
    if (handle) dlclose(handle);
    
    // Release Java strings
    env->ReleaseStringUTFChars(j_jrePath, c_jrePath);
    env->ReleaseStringUTFChars(j_jarPath, c_jarPath);    env->ReleaseStringUTFChars(j_version, c_version);
    env->ReleaseStringUTFChars(j_assetsPath, c_assetsPath);
    env->ReleaseStringUTFChars(j_nativesPath, c_nativesPath);

    return 0;
}
