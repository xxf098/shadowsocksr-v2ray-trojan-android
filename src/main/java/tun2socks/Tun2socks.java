package tun2socks;

import go.Seq;

public abstract class Tun2socks
{
    public static final long AddrTypeFQDN = 3L;
    public static final long AddrTypeIPv4 = 1L;
    public static final long AddrTypeIPv6 = 4L;
    public static final long AuthMethodNotRequired = 0L;
    public static final long SocksCmdConnect = 1L;
    public static final long StatusSucceeded = 0L;
    public static final long Version5 = 5L;

    private Tun2socks() {
    }

    public static void touch() {
    }

    private static native void _init();

    public static native String checkVersion();

    public static native Vmess convertJSONToVmess(final byte[] p0) throws Exception;

    public static native void copyAssets(final String p0, final boolean p1) throws Exception;

    public static native String generateVmessString(final Vmess p0) throws Exception;

    public static native long getFreePort() throws Exception;

    public static native void inputPacket(final byte[] p0);

    public static native Vmess newVmess(final String p0, final String p1, final String p2, final String p3, final long p4, final long p5, final String p6, final String p7, final String p8, final String p9, final long p10, final String p11, final String p12);

    public static native long queryStats(final String p0);

    public static native void setLocalDNS(final String p0);

    public static native boolean setNonblock(final long p0, final boolean p1);

    public static native void startTrojan(final byte[] p0, final Tun2socksStartOptions p1);

    public static native void startV2Ray(final PacketFlow p0, final VpnService p1, final LogService p2, final byte[] p3, final String p4) throws Exception;

    public static native void startV2RayWithVmess(final PacketFlow p0, final VpnService p1, final LogService p2, final Vmess p3, final String p4) throws Exception;

    public static native void stopTrojan();

    public static native void stopV2Ray();

    public static native void testConfig(final String p0, final String p1) throws Exception;

    public static native long testConfigLatency(final byte[] p0, final String p1) throws Exception;

    public static native long testTCPPing(final String p0, final long p1) throws Exception;

    public static native long testURLLatency(final String p0) throws Exception;

    public static native long testVmessLatency(final Vmess p0, final String p1, final long p2) throws Exception;

    public static native void startTrojanAndSocks(final byte[] p0, final PacketFlow p1, final String p2, final long p3);

    public static native void stopTrojanAndSocks();

    static {
        Seq.touch();
        _init();
    }

    private static final class proxyLogService implements Seq.Proxy, LogService
    {
        private final int refnum;

        @Override
        public final int incRefnum() {
            Seq.incGoRef(this.refnum, this);
            return this.refnum;
        }

        proxyLogService(final int refnum) {
            Seq.trackGoRef(this.refnum = refnum, this);
        }

        @Override
        public native void writeLog(final String p0) throws Exception;
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
