#include <jni.h>
#include <algorithm>
#include <cmath>
extern "C" JNIEXPORT jfloatArray JNICALL Java_org_example_espport_NativeMath_project(JNIEnv* env,jobject,jfloatArray pos,jfloatArray mat,jfloat width,jfloat height) {
 jfloat p[3]{},m[16]{};
 if(env->GetArrayLength(pos)<3 || env->GetArrayLength(mat)<16) return env->NewFloatArray(0);
 env->GetFloatArrayRegion(pos,0,3,p);env->GetFloatArrayRegion(mat,0,16,m);
 // Ported from esp/Core/UnityMath.mm (column-major projection).
 float w=m[3]*p[0]+m[7]*p[1]+m[11]*p[2]+m[15];
 w=std::max(w,0.5f);
 float out[2]={width*0.5f+(m[0]*p[0]+m[4]*p[1]+m[8]*p[2]+m[12])/w*width*0.5f,
 height*0.5f-(m[1]*p[0]+m[5]*p[1]+m[9]*p[2]+m[13])/w*height*0.5f};
 auto result=env->NewFloatArray(2);env->SetFloatArrayRegion(result,0,2,out);return result;
}
