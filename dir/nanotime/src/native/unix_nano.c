#include <stdlib.h>
#include <sys/time.h>
#include <time.h>
#include <unistd.h>
#include "ca_juliusdavies_nanotime_Clock.h"

JNIEXPORT void JNICALL Java_ca_juliusdavies_nanotime_Clock_getTime
  (JNIEnv * env, jclass jc, jlongArray time)
{
  jlong* now;
  jboolean jniNoCopy = JNI_FALSE;
  now = (*env)->GetLongArrayElements(env, time, &jniNoCopy);
  struct timeval tv;

#if defined(_POSIX_TIMERS)
  {
    struct timespec ts;
    if ( 0 == clock_gettime(CLOCK_REALTIME, &ts) )
    {
      now[0] = (jlong) ts.tv_sec;
      now[1] = (jlong) ts.tv_nsec;
      (*env)->ReleaseLongArrayElements(env,time,now,0);
      return;
    }
    /* fall back to gettimeofday if error encountered! */
  }
#endif

  gettimeofday(&tv, NULL);
  now[0] = (jlong) tv.tv_sec;
  now[1] = ((jlong) tv.tv_usec) * 1000;
  (*env)->ReleaseLongArrayElements(env,time,now,0);
}
