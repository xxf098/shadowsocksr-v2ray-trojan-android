package tun2socks;

import go.Seq;

public abstract class Tun2socks
{
    private Tun2socks() {
    }

    public static void touch() {
    }

    private static native void _init();

    public static native String checkVersion();

    public static native void inputPacket(final byte[] p0);

    public static native void setLocalDNS(final String p0);

    public static native boolean setNonblock(final long p0, final boolean p1);

    public static native void startV2Ray(final PacketFlow p0, final VpnService p1, final byte[] p2, final String p3, final String p4) throws Exception;

    public static native void stopV2Ray();

    public static native void testConfig(final String p0, final String p1) throws Exception;

    static {
        Seq.touch();
        _init();
    }

    private static final class proxyPacketFlow implements Seq.Proxy, PacketFlow
    {
        private final int refnum;

        @Override
        public final int incRefnum() {
            Seq.incGoRef(this.refnum, this);
            return this.refnum;
        }

        proxyPacketFlow(final int refnum) {
            Seq.trackGoRef(this.refnum = refnum, this);
        }

        @Override
        public native void writePacket(final byte[] p0);
    }

    private static final class proxyVpnService implements Seq.Proxy, VpnService
    {
        private final int refnum;

        @Override
        public final int incRefnum() {
            Seq.incGoRef(this.refnum, this);
            return this.refnum;
        }

        proxyVpnService(final int refnum) {
            Seq.trackGoRef(this.refnum = refnum, this);
        }

        @Override
        public native boolean protect(final long p0);
    }
}
