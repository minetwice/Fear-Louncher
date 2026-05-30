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
        return -1;
    }

    // 2. JVM Options
    std::string classpath = std::string(c_jarPath) + ":" + 
                            std::string(c_jrePath) + "/lib/*:" + 
                            std::string(c_jrePath) + "/lib/ext/*";
    
    std::vector<JavaVMOption> options;
    options.push_back({"-Djava.class.path", const_cast<char*>(classpath.c_str())});
    options.push_back({"-Xmx2G", nullptr});
    options.push_back({"-Xms1G", nullptr});
    options.push_back({"-Djava.library.path", const_cast<char*>(std::string(c_jrePath) + "/lib").c_str()});
    JavaVMInitArgs vm_args;
    vm_args.version = JNI_VERSION_1_8;
    vm_args.nOptions = options.size();
    vm_args.options = options.data();
    vm_args.ignoreUnrecognized = JNI_TRUE;

    JavaVM* vm;
    JNIEnv* jni_env;
    jint res = createVM(&vm, &jni_env, &vm_args);
    if (res != JNI_OK) {
        LOGE("JVM Creation Failed: %d", res);
        dlclose(handle);
        return -1;
    }

    // 3. Call Minecraft Main
    jclass mainClass = jni_env->FindClass("net/minecraft/client/main/Main");
    if (!mainClass) {
        LOGE("net.minecraft.client.main.Main not found");
        vm->DestroyJavaVM();
        dlclose(handle);
        return -1;
    }

    jmethodID mainMethod = jni_env->GetStaticMethodID(mainClass, "main", "([Ljava/lang/String;)V");
    if (!mainMethod) {
        LOGE("main() method not found");
        vm->DestroyJavaVM();
        dlclose(handle);
        return -1;
    }

    // Build args array
    jobjectArray args = jni_env->NewObjectArray(10, jni_env->FindClass("java/lang/String"), nullptr);
    std::vector<std::string> mc_args = {
        "--version", c_version,
        "--gameDir", c_jrePath, // ya baseDir
        "--assetsDir", c_assetsPath,
        "--assetIndex", c_version,
        "--username", "FearPlayer"
    };
    // (Note: Full args list requires accessToken, uuid, width, height, etc. Yahan simplified diya hai)

    for (size_t i = 0; i < mc_args.size(); i += 2) {
        jni_env->SetObjectArrayElement(args, i/2, jni_env->NewStringUTF(mc_args[i].c_str()));
        jni_env->SetObjectArrayElement(args, i/2 + 1, jni_env->NewStringUTF(mc_args[i+1].c_str()));
    }

    LOGI("Launching Minecraft via JVM...");    jni_env->CallStaticVoidMethod(mainClass, mainMethod, args);

    // Cleanup
    vm->DestroyJavaVM();
    dlclose(handle);
    env->ReleaseStringUTFChars(j_jrePath, c_jrePath);
    env->ReleaseStringUTFChars(j_jarPath, c_jarPath);
    env->ReleaseStringUTFChars(j_version, c_version);
    env->ReleaseStringUTFChars(j_assetsPath, c_assetsPath);

    return 0;
}
