package tun2socks;
import go.Seq;

public abstract class Tun2socks
{
    private Tun2socks() {
    }

    public static void touch() {
    }

    private static native void _init();

    public static native void inputPacket(final byte[] p0);

    public static native void setLocalDNS(final String p0);

    public static native boolean setNonblock(final long p0, final boolean p1);

    public static native long startV2Ray(final PacketFlow p0, final VpnService p1, final DBService p2, final byte[] p3, final String p4, final String p5, final String p6);

    public static native void stopV2Ray();

    static {
        Seq.touch();
        _init();
    }

    private static final class proxyDBService implements Seq.Proxy, DBService
    {
        private final int refnum;

        @Override
        public final int incRefnum() {
            Seq.incGoRef(this.refnum, this);
            return this.refnum;
        }

        proxyDBService(final int refnum) {
            Seq.trackGoRef(this.refnum = refnum, this);
        }

        @Override
        public native void insertProxyLog(final String p0, final String p1, final long p2, final long p3, final int p4, final int p5, final int p6, final int p7, final String p8, final String p9, final int p10);
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
