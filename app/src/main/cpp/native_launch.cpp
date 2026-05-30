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
        env->ReleaseStringUTFChars(j_jrePath, c_jrePath);
        env->ReleaseStringUTFChars(j_jarPath, c_jarPath);
        env->ReleaseStringUTFChars(j_version, c_version);
        env->ReleaseStringUTFChars(j_assetsPath, c_assetsPath);
        return -1;
    }

    auto createVM = (JNI_CreateJavaVM_t)dlsym(handle, "JNI_CreateJavaVM");
    if (!createVM) {
        LOGE("Failed to find JNI_CreateJavaVM");
        dlclose(handle);
        env->ReleaseStringUTFChars(j_jrePath, c_jrePath);
        env->ReleaseStringUTFChars(j_jarPath, c_jarPath);
        env->ReleaseStringUTFChars(j_version, c_version);
        env->ReleaseStringUTFChars(j_assetsPath, c_assetsPath);
        return -1;
    }

    // 2. JVM Options - FIX: Use proper string storage
    std::string classpath_str = std::string(c_jarPath) + ":" + 
                                std::string(c_jrePath) + "/lib/*:" + 
                                std::string(c_jrePath) + "/lib/ext/*";
        std::string libPath_str = std::string(c_jrePath) + "/lib";
    
    // Store option strings in variables to keep them alive
    std::string opt1_str = "-Djava.class.path";
    std::string opt4_str = "-Djava.library.path";
    
    JavaVMOption options[4];
    options[0].optionString = const_cast<char*>(opt1_str.c_str());
    options[0].extraInfo = const_cast<char*>(classpath_str.c_str());
    
    options[1].optionString = const_cast<char*>("-Xmx2G");
    options[1].extraInfo = nullptr;
    
    options[2].optionString = const_cast<char*>("-Xms1G");
    options[2].extraInfo = nullptr;
    
    options[3].optionString = const_cast<char*>(opt4_str.c_str());
    options[3].extraInfo = const_cast<char*>(libPath_str.c_str());

    JavaVMInitArgs vm_args;
    vm_args.version = JNI_VERSION_1_8;  // FIX: Now defined in jni.h
    vm_args.nOptions = 4;
    vm_args.options = options;
    vm_args.ignoreUnrecognized = JNI_TRUE;

    JavaVM* vm = nullptr;
    JNIEnv* jni_env = nullptr;
    jint res = createVM(&vm, &jni_env, &vm_args);
    if (res != JNI_OK || !vm || !jni_env) {
        LOGE("JVM Creation Failed: %d", res);
        dlclose(handle);
        env->ReleaseStringUTFChars(j_jrePath, c_jrePath);
        env->ReleaseStringUTFChars(j_jarPath, c_jarPath);
        env->ReleaseStringUTFChars(j_version, c_version);
        env->ReleaseStringUTFChars(j_assetsPath, c_assetsPath);
        return -1;
    }

    // 3. Call Minecraft Main
    jclass mainClass = jni_env->FindClass("net/minecraft/client/main/Main");
    if (!mainClass) {
        LOGE("net.minecraft.client.main.Main not found");
        vm->DestroyJavaVM();
        dlclose(handle);
        env->ReleaseStringUTFChars(j_jrePath, c_jrePath);
        env->ReleaseStringUTFChars(j_jarPath, c_jarPath);
        env->ReleaseStringUTFChars(j_version, c_version);
        env->ReleaseStringUTFChars(j_assetsPath, c_assetsPath);
        return -1;
    }
    jmethodID mainMethod = jni_env->GetStaticMethodID(mainClass, "main", "([Ljava/lang/String;)V");
    if (!mainMethod) {
        LOGE("main() method not found");
        vm->DestroyJavaVM();
        dlclose(handle);
        env->ReleaseStringUTFChars(j_jrePath, c_jrePath);
        env->ReleaseStringUTFChars(j_jarPath, c_jarPath);
        env->ReleaseStringUTFChars(j_version, c_version);
        env->ReleaseStringUTFChars(j_assetsPath, c_assetsPath);
        return -1;
    }

    // Build minimal args array
    jobjectArray args = jni_env->NewObjectArray(2, jni_env->FindClass("java/lang/String"), nullptr);
    if (args) {
        jni_env->SetObjectArrayElement(args, 0, jni_env->NewStringUTF("--version"));
        jni_env->SetObjectArrayElement(args, 1, jni_env->NewStringUTF(c_version));
    }

    LOGI("Launching Minecraft via JVM...");
    jni_env->CallStaticVoidMethod(mainClass, mainMethod, args);

    // Cleanup
    vm->DestroyJavaVM();
    dlclose(handle);
    env->ReleaseStringUTFChars(j_jrePath, c_jrePath);
    env->ReleaseStringUTFChars(j_jarPath, c_jarPath);
    env->ReleaseStringUTFChars(j_version, c_version);
    env->ReleaseStringUTFChars(j_assetsPath, c_assetsPath);

    return 0;
}
