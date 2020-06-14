package tun2sockstrojan;

import go.Seq;

public abstract class Tun2sockstrojan
{
    private Tun2sockstrojan() {
    }

    public static void touch() {
    }

    private static native void _init();

    public static native void setLoglevel(final String p0);

    public static native long start(final Tun2socksStartOptions p0);

    public static native void stop();

    static {
        Seq.touch();
        _init();
    }
}