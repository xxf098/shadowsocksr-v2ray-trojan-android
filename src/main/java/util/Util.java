package util;

import go.Seq;

public abstract class Util
{
    public static final long LogLineBufLen = 1024L;
    public static final long MaxLogBufLen = 16777216L;

    private Util() {
    }

    public static void touch() {
    }

    private static native void _init();

    public static native void logGoRoutineCount();

    public static native void logGoroutineStackTrace();

    static {
        Seq.touch();
        _init();
    }
}
