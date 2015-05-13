#include <windows.h>
#include "ca_juliusdavies_nanotime_Clock.h"


JNIEXPORT void JNICALL Java_ca_juliusdavies_nanotime_Clock_getTime
  (JNIEnv * env, jclass jc, jlongArray time)
{
  unsigned long long freq;
  unsigned long long msTime;
  unsigned long long seconds;
  unsigned long long nanos;

  /*
     Even though PC might be 64bit, the lower 32bits are good enough
     (assuming PC is 150ghz or slower), since windows changes the time
     every 15ms.  Helps us make sure rollover situation is properly handled,
     too!
  */
  unsigned long prev_pc, pc;  
  
  LARGE_INTEGER li;
  FILETIME ft;
  jlong* now;
  jboolean jniNoCopy = JNI_FALSE;

  /* Array is:  {secs, nanos, msTime, qpc, freq, flag} */
  now = (*env)->GetLongArrayElements(env, time, &jniNoCopy);
  prev_pc = (unsigned long) now[3];

  /*
    The QueryPerformanceCounter/GetSystemTimeAsFileTime calls need to be close
    together so that they are well correlated.
  */
  QueryPerformanceCounter(&li);  
  GetSystemTimeAsFileTime(&ft);

  pc = (unsigned long) li.QuadPart;
  now[3] = (jlong) pc;    /* new reading of QPC replaces old reading. */
  
  msTime = ((unsigned long long) ft.dwHighDateTime) << 32;
  msTime += ft.dwLowDateTime;

  if ( now[2] == msTime )
  {
    /* If Windows time didn't change, let's use our hi-resolution simulator. */  
    seconds = (unsigned long long) now[0];
    nanos = (unsigned long long) now[1];
    freq = (unsigned long long) now[4];
    if (freq <= 0) {
      /* This is all pointless is frequency doesn't make sense. */
      return;
    }

    /* This unsigned math works on underflow if the PC in the CPU is 32bit
       or larger. */
    pc = pc - prev_pc;

    /* Convert PC delta to nanos and add it to our current nanos. */
    nanos += (1000000000LL * pc) / freq;
    seconds += nanos / 1000000000L;
    nanos %= 1000000000L;
    now[0] = (jlong) seconds;
    now[1] = (jlong) nanos;
    now[5] = 0;             /* flag to say windows time didn't change. */
  }
  else
  {  
    /* Windows time did change.  Let's use this official time source. */
    seconds = (msTime / 10000000L) - 11644473600LL; /* Subtract 1601-Jan-01 */
    nanos = (msTime % 10000000L) * 100;    

    now[0] = (jlong) seconds;
    now[1] = (jlong) nanos;
    now[2] = (jlong) msTime;  /* windows time changed! */
    /* Set frequency for first time if necessary. */
    if (now[4] <= 0) {
      QueryPerformanceFrequency(&li);
      now[4] = (jlong) li.QuadPart;
    }
    now[5] = 1;               /* flag to say windows time did change! */
  }

  (*env)->ReleaseLongArrayElements(env,time,now,0);

}
