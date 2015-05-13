#include <stdlib.h>
#include <sys/time.h>
#include "ca_juliusdavies_nanotime_Clock.h"

JNIEXPORT void JNICALL Java_ca_juliusdavies_nanotime_Clock_getTime
  (JNIEnv * env, jclass jc, jlongArray time)
{
  jlong* now;
  jboolean jniNoCopy = JNI_FALSE;
  now = (*env)->GetLongArrayElements(env, time, &jniNoCopy);
  struct timeval tv;

  /* Mac only offers gettimeofday(), with its microsecond precision. */
  gettimeofday(&tv, NULL);
  now[0] = (jlong) tv.tv_sec;
  now[1] = ((jlong) tv.tv_usec) * 1000;
  (*env)->ReleaseLongArrayElements(env,time,now,0);
}
