package io.getunleash.android;
import android.util.Log;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

@Implements(Log.class)
public class ShadowLog {

    public static int log(String level, String tag, String msg) {
        System.out.println(level + ": [" + tag + "] " + msg);
        return 0; // Return value is typically the number of chars written, similar to Android's Log.d
    }

    @Implementation
    public static int d(String tag, String msg) {
        return log("debug", tag, msg);
    }

    @Implementation
    public static int i(String tag, String msg) {
        return log("info", tag, msg);
    }

    @Implementation
    public static int w(String tag, String msg) {
        return log("warn", tag, msg);
    }

    @Implementation
    public static int e(String tag, String msg) {
        return log("error", tag, msg);
    }
}
