/*
*
* Julius Davies licenses this file
* to you under the Apache License, Version 2.0 (the
* "License"); you may not use this file except in compliance
* with the License.  You may obtain a copy of the License at
*
*   http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing,
* software distributed under the License is distributed on an
* "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
* KIND, either express or implied.  See the License for the
* specific language governing permissions and limitations
* under the License.
*
* Copyright, Julius Davies, 2008.
*
*/

package ca.juliusdavies.nanotime;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

/**
 * Provides static nativeTime() method which returns current time
 * as two-element long[] array containing {seconds,nanoseconds}.
 * Seconds are measured since epoch (unix time).
 *
 * @author Julius Davies
 * @author juliusdavies@gmail.com
 * @since May 8, 2008
 */
public class Clock {

    private final static boolean IS_WINDOWS;

    static {
        IS_WINDOWS = loadNativeLib();
    }

    /*
    On unix we provide an empty two-element long array, and getTime() fills it
    in with {seconds,nanos} since EPOCH.  On Windows we provide a special
    six-element long array where some of the values come from the most recent
    call.
     */
    private static native void getTime(long[] time);

    // {seconds, nanos, microsoftTime, programCounter, freq, flag}
    // Note:
    // Our windows "hi-res" simulator uses microsoftTime, programCounter, and
    // freq to estimate current time inside the Windows 16ms window.  The flag
    // is 0 if "hi-res" was used, and 1 if Windows time changed.
    private static final long[] cachedWindowsTime = new long[6];

    private static long[] nativeUnixTime() {
        long[] now = new long[2];
        getTime(now);
        return now;
    }

    private static long[] nativeWindowsTime() {
        long[] now = new long[2];

        /*
                
        Volatile isn't good enough.  We need to use synchronize{} on Windows
        platforms (unix is fine).  We don't want situation where a call just
        after windows-time changed (every 16ms) is overwritten by a previous
        call just before windows-time changed.  Two reasons:

        - This will decrease the (already bad) accuracy of our windows
          high-resolution simulator.

        - Two events that happened at different times can get identical
          timestamps to the nanosecond.  (Imagine a 3rd call that thinks
          it's discovered the latest update to windows-time, even though the
          1st call already did).

        */
        synchronized(cachedWindowsTime) {
            getTime(cachedWindowsTime);
            now[0] = cachedWindowsTime[0];
            now[1] = cachedWindowsTime[1];
        }
        return now;
    }

    /**
     * @return current system time as two element
     *         long[] array containing {seconds, nanoseconds}.
     *         Seconds are counted since epoch (unix time).
     */
    public static long[] nativeTime() {
        if (IS_WINDOWS) {
            return nativeWindowsTime();
        } else {
            return nativeUnixTime();
        }
    }

    /**
     * @return current java time as two element
     *         long[] array containing {seconds, nanoseconds}.
     *         Seconds are counted since epoch (unix time).
     *         Nanoseconds will have millisecond granularity,
     *         since that's what System.currentTimeMillis() returns.
     */
    public static long[] javaTime() {
        long[] now = new long[2];
        now[0] = System.currentTimeMillis();
        now[1] = (now[0] % 1000L) * 1000000L;
        now[0] /= 1000L;
        return now;
    }

    public static String format(long[] now) {
        SimpleDateFormat df2 = new SimpleDateFormat("yyyy-MM-dd/HH:mm:ss.");
        SimpleDateFormat tz = new SimpleDateFormat("/zzz");
        Date d2 = new Date((now[0] * 1000L) + (now[1] / 1000000L));
        String nanos = Long.toString(now[1]);
        while (nanos.length() < 9) {
            nanos = "0" + nanos;
        }
        return df2.format(d2) + nanos + tz.format(d2);
    }


    public static void main(String[] args) throws Exception {
        long[] javaTime = javaTime();
        ArrayList list = new ArrayList(10);
        for (int i = 0; i < 10; i++) {
            list.add(nativeTime());
        }

        System.out.println(format(javaTime) + " JavaTime");
        for (int i = 0; i < 10; i++) {
            System.out.println(format((long[]) list.get(i)) + " NativeTime");
        }
    }


    static boolean loadNativeLib() {
        // Thanks to sebbaz@gmail.com for pointing out that toUpperCase()
        // would not work in Turkish locales.
        String os = System.getProperty("os.name");
        String arch = System.getProperty("os.arch");
        String version = System.getProperty("os.version");
        String javaVersion = System.getProperty("java.version");
        String javaVMVersion = System.getProperty("java.vm.version");

        System.out.println("os.name=[" + os + "] os.arch=[" + arch + "] os.version=[" + version + "] java.version=[" + javaVersion + "] java.vm.version=[" + javaVMVersion + "]");
        os = os != null ? os.trim().toUpperCase(Locale.ENGLISH) : "";
        arch = arch != null ? arch.trim().toUpperCase(Locale.ENGLISH) : "";

        boolean isWindows = os.startsWith("WINDOWS");
        boolean isLinux = os.startsWith("LINUX");
        boolean isMac = os.startsWith("MAC");
        boolean isSolaris = os.startsWith("SUN") || os.startsWith("SOLARIS");
        boolean isSparc = arch.startsWith("SPARC");
        boolean isAMD64 = arch.startsWith("X86_64") || arch.startsWith("AMD64") || arch.startsWith("X64");
        boolean isPPC = arch.startsWith("PPC") || arch.startsWith("POWER");

        String defaultLib = isWindows ? "jnt.dll" : "libjnt.so";
        String lib = defaultLib;
        if (isWindows) {
            lib = "winjnt32.dll";
            if (isAMD64) {
                lib = "winjnt64.dll";
            }
        } else if (isLinux) {
            lib = "linux-x86-libjnt32.so";
            if (isAMD64) {
                lib = "linux-amd64-libjnt64.so";
            } else if (isPPC) {
                lib = "linux-ppc-libjnt32.so";
            } else if (isSparc) {
                lib = "linux-sparc-libjnt64.so";
            }
        } else if (isMac) {
            lib = "mac-x86-libjnt32.so";
            if (isAMD64) {
                lib = "mac-amd64-libjnt64.so";
            } else if (isPPC) {
                lib = "mac-ppc-libjnt32.so";
            }
        } else if (isSolaris) {
            lib = "solaris-x86-libjnt32.so";
            if (isAMD64) {
                lib = "solaris-amd64-libjnt64.so";
            } else if (isSparc) {
                lib = "solaris-sparc-libjnt64.so";
            }
        }


        String home = System.getProperty("user.home");
        // Prefer the default (freshly built) binary over the prebuilt ones.
        InputStream defaultStream = Clock.class.getResourceAsStream(defaultLib);
        InputStream archStream = Clock.class.getResourceAsStream(lib);

        String nativeLib = home + "/.libjnt/" + defaultLib;
        boolean success = false;
        Throwable problem = null;
        try {
            success = load(defaultStream, nativeLib);
        } catch (Throwable t) {
            problem = t;
        }
        try {
            success = load(archStream, nativeLib);
        } catch (IOException e) {
            problem = e;
        } catch (RuntimeException re) {
            problem = re;
            throw re;
        } catch (Error err) {
            problem = err;
            throw err;
        } finally {
            if (!success) {
                if (problem != null) {
                    System.err.println("Problem loading " + nativeLib + " - " + problem);
                } else {
                    System.err.println("Unknown problem loading " + nativeLib);
                }
            }
        }
        return isWindows;
    }

    private static boolean load(InputStream stream, String lib) throws IOException {
        if (stream == null) {
            return false;
        }
        File f = new File(lib);
        if (f.exists()) {
            f.delete();
        }
        f.getParentFile().mkdirs();
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(f);
            byte[] buf = new byte[8192];
            int c = stream.read(buf);
            while (c >= 0) {
                if (c > 0) {
                    out.write(buf, 0, c);
                }
                c = stream.read(buf);
            }
            out.flush();
            stream.close();
        }
        finally {
            if (out != null) {
                out.close();
            }
        }

        String path = f.getAbsolutePath();
        try {
            path = f.getCanonicalPath();
        } catch (IOException ioe) {
            // oh well.
        }
        System.load(path);
        return true;
    }

}
