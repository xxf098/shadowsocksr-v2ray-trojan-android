package freeport;

import go.Seq;

public abstract class Freeport
{
    private Freeport() {
    }

    public static void touch() {
    }

    private static native void _init();

    public static native long getFreePort() throws Exception;

    static {
        Seq.touch();
        _init();
    }
}