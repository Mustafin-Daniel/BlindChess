#include "RtbProbe.h"
#include "tbprobe.hpp"
#include <algorithm>


static bool initOk = false;

JNIEXPORT jboolean
JNICALL Java_com_example_caecuschess_tb_RtbProbe_init(
        JNIEnv* env, jclass cls, jstring jTbPath)
{
    initOk = false;
    const char* tbPath = env->GetStringUTFChars(jTbPath, NULL);
    if (!tbPath)
        return false;
    std::string rtbPath(tbPath);
    env->ReleaseStringUTFChars(jTbPath, tbPath);

    TBProbe::initialize(rtbPath);
    initOk = true;
    return true;
}

JNIEXPORT void
JNICALL Java_com_example_caecuschess_tb_RtbProbe_probe(
        JNIEnv* env, jobject ths, jbyteArray jSquares, jboolean wtm,
        jint epSq, jint castleMask,
        jint halfMoveClock, jint fullMoveCounter,
        jintArray result)
{
    if (env->GetArrayLength(result) < 2)
        return;

    jint res[2];
    res[0] = 1000;
    res[1] = 1000;
    env->SetIntArrayRegion(result, 0, 2, res);

    if (!initOk)
        return;

    const int len = env->GetArrayLength(jSquares);
    if (len != 64)
        return;

    Position pos;
    jbyte* jbPtr = env->GetByteArrayElements(jSquares, NULL);
    for (int i = 0; i < 64; i++)
        pos.setPiece(i, jbPtr[i]);
    env->ReleaseByteArrayElements(jSquares, jbPtr, 0);

    pos.setWhiteMove(wtm);
    pos.setEpSquare(epSq);
    pos.setCastleMask(castleMask);
    pos.setHalfMoveClock(halfMoveClock);
    pos.setFullMoveCounter(fullMoveCounter);

    int score;
    if (TBProbe::rtbProbeWDL(pos, score))
        res[0] = score;
    if (TBProbe::rtbProbeDTZ(pos, score))
        res[1] = score;

    env->SetIntArrayRegion(result, 0, 2, res);
}
