#include <jni.h>
#include <android/log.h>
#include <string>
#include <vector>
#include <dlfcn.h>

#define TAG "NativeLaunch"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

typedef jint (*JNI_CreateJavaVM_t)(JavaVM**, JNIEnv**, void*);

extern "C" JNIEXPORT jint JNICALL
Java_com_fearlauncher_launcher_GameLauncher_startMinecraftNative(
        JNIEnv* env, jobject thiz,
        jstring j_jrePath, jstring j_jarPath, jstring j_version, jstring j_assetsPath) {

    const char* c_jrePath = env->GetStringUTFChars(j_jrePath, nullptr);
    const char* c_jarPath = env->GetStringUTFChars(j_jarPath, nullptr);
    const char* c_version = env->GetStringUTFChars(j_version, nullptr);
    const char* c_assetsPath = env->GetStringUTFChars(j_assetsPath, nullptr);

    // 1. Load libjvm.so
    std::string libjvm_path = std::string(c_jrePath) + "/lib/server/libjvm.so";
    void* handle = dlopen(libjvm_path.c_str(), RTLD_LAZY | RTLD_GLOBAL);
    if (!handle) {
        LOGE("Failed to load libjvm.so: %s", dlerror());
        goto cleanup;
    }

    auto createVM = (JNI_CreateJavaVM_t)dlsym(handle, "JNI_CreateJavaVM");
    if (!createVM) {
        LOGE("Failed to find JNI_CreateJavaVM");
        goto cleanup;
    }

    // 2. JVM Options - FIX: Store strings in variables first to avoid const_cast error
    std::string classpath = std::string(c_jarPath) + ":" + 
                            std::string(c_jrePath) + "/lib/*:" + 
                            std::string(c_jrePath) + "/lib/ext/*";
    
    std::string libPath = std::string(c_jrePath) + "/lib";
    
    // FIX: Use proper JavaVMOption initialization
    JavaVMOption opt1, opt2, opt3, opt4;
    opt1.optionString = const_cast<char*>("-Djava.class.path");
    opt1.extraInfo = const_cast<char*>(classpath.c_str());
    
    opt2.optionString = const_cast<char*>("-Xmx2G");
    opt2.extraInfo = nullptr;    
    opt3.optionString = const_cast<char*>("-Xms1G");
    opt3.extraInfo = nullptr;
    
    opt4.optionString = const_cast<char*>("-Djava.library.path");
    opt4.extraInfo = const_cast<char*>(libPath.c_str());
    
    JavaVMOption options[] = {opt1, opt2, opt3, opt4};

    JavaVMInitArgs vm_args;
    vm_args.version = JNI_VERSION_1_8;  // FIX: Now jni.h is included so this works
    vm_args.nOptions = 4;
    vm_args.options = options;
    vm_args.ignoreUnrecognized = JNI_TRUE;

    JavaVM* vm;
    JNIEnv* jni_env;
    jint res = createVM(&vm, &jni_env, &vm_args);
    if (res != JNI_OK) {
        LOGE("JVM Creation Failed: %d", res);
        goto cleanup;
    }

    // 3. Call Minecraft Main
    jclass mainClass = jni_env->FindClass("net/minecraft/client/main/Main");
    if (!mainClass) {
        LOGE("net.minecraft.client.main.Main not found");
        vm->DestroyJavaVM();
        goto cleanup;
    }

    jmethodID mainMethod = jni_env->GetStaticMethodID(mainClass, "main", "([Ljava/lang/String;)V");
    if (!mainMethod) {
        LOGE("main() method not found");
        vm->DestroyJavaVM();
        goto cleanup;
    }

    // Build minimal args array
    jobjectArray args = jni_env->NewObjectArray(2, jni_env->FindClass("java/lang/String"), nullptr);
    jni_env->SetObjectArrayElement(args, 0, jni_env->NewStringUTF("--version"));
    jni_env->SetObjectArrayElement(args, 1, jni_env->NewStringUTF(c_version));

    LOGI("Launching Minecraft via JVM...");
    jni_env->CallStaticVoidMethod(mainClass, mainMethod, args);

    // Cleanup
    vm->DestroyJavaVM();

cleanup:    if (handle) dlclose(handle);
    env->ReleaseStringUTFChars(j_jrePath, c_jrePath);
    env->ReleaseStringUTFChars(j_jarPath, c_jarPath);
    env->ReleaseStringUTFChars(j_version, c_version);
    env->ReleaseStringUTFChars(j_assetsPath, c_assetsPath);

    return 0;
}
