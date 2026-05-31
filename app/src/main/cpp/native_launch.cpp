#include <jni.h>
#include <android/log.h>
#include <string>
#include <dlfcn.h>

#define TAG "NativeLaunch"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

// Function pointer type for JNI_CreateJavaVM
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

    // 1. Load libjvm.so from extracted JRE
    std::string libjvm_path = std::string(c_jrePath) + "/lib/server/libjvm.so";
    void* handle = dlopen(libjvm_path.c_str(), RTLD_LAZY | RTLD_GLOBAL);
    
    if (!handle) {
        LOGE("❌ Failed to load libjvm.so: %s", dlerror());
        env->ReleaseStringUTFChars(j_jrePath, c_jrePath);
        env->ReleaseStringUTFChars(j_jarPath, c_jarPath);
        env->ReleaseStringUTFChars(j_version, c_version);
        env->ReleaseStringUTFChars(j_assetsPath, c_assetsPath);
        env->ReleaseStringUTFChars(j_nativesPath, c_nativesPath);
        return -1;
    }

    // Get pointer to JNI_CreateJavaVM function
    auto createVM = (JNI_CreateJavaVM_t)dlsym(handle, "JNI_CreateJavaVM");
    if (!createVM) {
        LOGE("❌ Failed to find JNI_CreateJavaVM symbol");
        dlclose(handle);
        env->ReleaseStringUTFChars(j_jrePath, c_jrePath);
        env->ReleaseStringUTFChars(j_jarPath, c_jarPath);
        env->ReleaseStringUTFChars(j_version, c_version);        env->ReleaseStringUTFChars(j_assetsPath, c_assetsPath);
        env->ReleaseStringUTFChars(j_nativesPath, c_nativesPath);
        return -1;
    }

    // 2. Prepare JVM Options
    // FIX: Store strings in variables first to avoid const_cast errors
    std::string classpath_str = std::string(c_jarPath) + ":" + 
                                std::string(c_jrePath) + "/lib/*:" + 
                                std::string(c_jrePath) + "/lib/ext/*";
    
    std::string libPath_str = std::string(c_nativesPath);
    
    // Store option string names in variables
    std::string opt_classpath = "-Djava.class.path";
    std::string opt_libpath = "-Djava.library.path";
    std::string opt_lwjglpath = "-Dorg.lwjgl.librarypath";
    
    JavaVMOption options[5];
    
    // Option 0: Classpath
    options[0].optionString = const_cast<char*>(opt_classpath.c_str());
    options[0].extraInfo = const_cast<char*>(classpath_str.c_str());
    
    // Option 1: Max Heap Size (2GB)
    options[1].optionString = const_cast<char*>("-Xmx2G");
    options[1].extraInfo = nullptr;
    
    // Option 2: Initial Heap Size (1GB)
    options[2].optionString = const_cast<char*>("-Xms1G");
    options[2].extraInfo = nullptr;
    
    // Option 3: Java Library Path (for natives)
    options[3].optionString = const_cast<char*>(opt_libpath.c_str());
    options[3].extraInfo = const_cast<char*>(libPath_str.c_str());
    
    // Option 4: LWJGL Library Path (for OpenGL/GLFW)
    options[4].optionString = const_cast<char*>(opt_lwjglpath.c_str());
    options[4].extraInfo = const_cast<char*>(libPath_str.c_str());

    // Initialize JVM arguments
    JavaVMInitArgs vm_args;
    // FIX: Use hardcoded hex value for JNI_VERSION_1_8 to avoid macro issues
    vm_args.version = 0x00010008; 
    vm_args.nOptions = 5;
    vm_args.options = options;
    vm_args.ignoreUnrecognized = JNI_TRUE;

    // 3. Create the Java VM
    JavaVM* vm = nullptr;    JNIEnv* jni_env = nullptr;
    jint res = createVM(&vm, &jni_env, &vm_args);
    
    if (res != JNI_OK || !vm || !jni_env) {
        LOGE("❌ JVM Creation Failed: %d", res);
        dlclose(handle);
        env->ReleaseStringUTFChars(j_jrePath, c_jrePath);
        env->ReleaseStringUTFChars(j_jarPath, c_jarPath);
        env->ReleaseStringUTFChars(j_version, c_version);
        env->ReleaseStringUTFChars(j_assetsPath, c_assetsPath);
        env->ReleaseStringUTFChars(j_nativesPath, c_nativesPath);
        return -1;
    }

    LOGI("✅ JVM Created Successfully");

    // 4. Find Minecraft Main Class
    jclass mainClass = jni_env->FindClass("net/minecraft/client/main/Main");
    if (!mainClass) {
        LOGE("❌ net.minecraft.client.main.Main class not found");
        // Clear exception if any
        if (jni_env->ExceptionCheck()) jni_env->ExceptionDescribe();
        vm->DestroyJavaVM();
        dlclose(handle);
        goto cleanup;
    }

    // 5. Get Main Method ID
    jmethodID mainMethod = jni_env->GetStaticMethodID(mainClass, "main", "([Ljava/lang/String;)V");
    if (!mainMethod) {
        LOGE("❌ main() method not found");
        if (jni_env->ExceptionCheck()) jni_env->ExceptionDescribe();
        vm->DestroyJavaVM();
        dlclose(handle);
        goto cleanup;
    }

    // 6. Build Command Line Arguments for Minecraft
    // Minimal args for testing - expand as needed
    jobjectArray args = jni_env->NewObjectArray(10, jni_env->FindClass("java/lang/String"), nullptr);
    
    if (args) {
        int idx = 0;
        jni_env->SetObjectArrayElement(args, idx++, jni_env->NewStringUTF("--version"));
        jni_env->SetObjectArrayElement(args, idx++, jni_env->NewStringUTF(c_version));
        jni_env->SetObjectArrayElement(args, idx++, jni_env->NewStringUTF("--gameDir"));
        jni_env->SetObjectArrayElement(args, idx++, jni_env->NewStringUTF(c_jrePath)); // Use base dir
        jni_env->SetObjectArrayElement(args, idx++, jni_env->NewStringUTF("--assetsDir"));
        jni_env->SetObjectArrayElement(args, idx++, jni_env->NewStringUTF(c_assetsPath));
        jni_env->SetObjectArrayElement(args, idx++, jni_env->NewStringUTF("--assetIndex"));        jni_env->SetObjectArrayElement(args, idx++, jni_env->NewStringUTF(c_version));
        jni_env->SetObjectArrayElement(args, idx++, jni_env->NewStringUTF("--username"));
        jni_env->SetObjectArrayElement(args, idx++, jni_env->NewStringUTF("FearPlayer"));
        // Add more args like accessToken, uuid, width, height as needed
    }

    // 7. Launch Minecraft!
    LOGI("🚀 Launching Minecraft %s...", c_version);
    jni_env->CallStaticVoidMethod(mainClass, mainMethod, args);

    // Check for exceptions during execution
    if (jni_env->ExceptionCheck()) {
        LOGE("⚠️ Exception during Minecraft execution:");
        jni_env->ExceptionDescribe();
        jni_env->ExceptionClear();
    }

    // 8. Cleanup - Destroy JVM
    vm->DestroyJavaVM();
    dlclose(handle);

cleanup:
    // Release Java strings
    env->ReleaseStringUTFChars(j_jrePath, c_jrePath);
    env->ReleaseStringUTFChars(j_jarPath, c_jarPath);
    env->ReleaseStringUTFChars(j_version, c_version);
    env->ReleaseStringUTFChars(j_assetsPath, c_assetsPath);
    env->ReleaseStringUTFChars(j_nativesPath, c_nativesPath);

    return 0;
}
