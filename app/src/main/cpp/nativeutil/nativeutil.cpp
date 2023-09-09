#include <jni.h>
#include <unistd.h>
#include <stdlib.h>
#include <sys/stat.h>
#include <sys/time.h>
#include <sys/resource.h>

#if defined(__arm__)
  #include "cpuinfo_arm.h"
#elif defined(__i386__)
  #include "cpuinfo_x86.h"
#endif

/*
 * Class:     com_example_caecuschess_engine_EngineUtil
 * Method:    chmod
 * Signature: (Ljava/lang/String;)Z
 */
extern "C" JNIEXPORT jboolean JNICALL Java_com_example_caecuschess_engine_EngineUtil_chmod
  (JNIEnv *env, jclass, jstring jExePath) {
    const char* exePath = env->GetStringUTFChars(jExePath, NULL);
    if (!exePath)
        return static_cast<jboolean>(false);
    bool ret = chmod(exePath, 0744) == 0;
    env->ReleaseStringUTFChars(jExePath, exePath);
    return static_cast<jboolean>(ret);
}

/*
 * Class:     com_example_caecuschess_engine_EngineUtil
 * Method:    reNice
 * Signature: (II)V
 */
extern "C" JNIEXPORT void JNICALL Java_com_example_caecuschess_engine_EngineUtil_reNice
  (JNIEnv *env, jclass, jint pid, jint prio) {
    setpriority(PRIO_PROCESS, pid, prio);
}

/*
 * Class:     com_example_caecuschess_engine_EngineUtil
 * Method:    isSimdSupported
 * Signature: ()Z
 */
extern "C" JNIEXPORT jboolean JNICALL Java_com_example_caecuschess_engine_EngineUtil_isSimdSupported
    (JNIEnv *env, jclass) {
#if defined(__arm__)
    using namespace cpu_features;
    ArmFeatures features = GetArmInfo().features;
    return features.neon ? JNI_TRUE : JNI_FALSE;
#elif defined(__i386__)
    using namespace cpu_features;
    X86Features features = GetX86Info().features;
    return features.sse4_1 ? JNI_TRUE : JNI_FALSE;
#endif
    return true;
}
