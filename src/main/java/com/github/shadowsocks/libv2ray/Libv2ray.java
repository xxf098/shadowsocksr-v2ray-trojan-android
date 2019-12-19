package com.github.shadowsocks.libv2ray;

import com.github.shadowsocks.go.Seq;

public abstract class Libv2ray {
    private Libv2ray() {
    }

    public static void touch() {
    }

    private static native void _init();

    public static native long checkVersion();

    public static native String checkVersionX();

    public static native V2RayPoint newV2RayPoint(V2RayVPNServiceSupportsSet var0);

    public static native void testConfig(String var0) throws Exception;

    static {
        Seq.touch();
        Libv2ray._init();
    }

    private static final class proxyV2RayVPNServiceSupportsSet
            implements Seq.Proxy,
            V2RayVPNServiceSupportsSet {
        private final int refnum;

        @Override
        public final int incRefnum() {
            Seq.incGoRef(this.refnum, this);
            return this.refnum;
        }

        proxyV2RayVPNServiceSupportsSet(int n) {
            this.refnum = n;
            Seq.trackGoRef(n, this);
        }

        @Override
        public native long onEmitStatus(long var1, String var3);

        @Override
        public native long prepare();

        @Override
        public native long protect(long var1);

        @Override
        public native long sendFd();

        @Override
        public native long setup(String var1);

        @Override
        public native long shutdown();
    }

}
