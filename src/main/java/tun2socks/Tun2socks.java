package tun2socks;

import go.Seq;

public abstract class Tun2socks
{
    public static final long AddrTypeFQDN = 3L;
    public static final long AddrTypeIPv4 = 1L;
    public static final long AddrTypeIPv6 = 4L;
    public static final long AuthMethodNotRequired = 0L;
    public static final String SHADOWSOCKS = "shadowsocks";
    public static final long SocksCmdConnect = 1L;
    public static final long StatusSucceeded = 0L;
    public static final String TROJAN = "trojan";
    public static final String VLESS = "vless";
    public static final String VMESS = "vmess";
    public static final long Version5 = 5L;

    private Tun2socks() {
    }

    public static void touch() {
    }

    private static native void _init();

    public static native void batchRenderTestDownload(final String p0, final long p1, final String p2, final String p3, final String p4, final String p5, final TestDownload p6) throws Exception;

    public static native void batchTestDownload(final String p0, final long p1, final TestDownload p2) throws Exception;

    public static native void batchTestLatency(final String p0, final long p1, final TestLatency p2);

    public static native void batchTestVmessCoreLatency(final String p0, final long p1, final TestLatency p2);

    public static native String checkVersion();

    public static native String checkXVersion();

    public static native Vmess convertJSONToVmess(final byte[] p0) throws Exception;

    public static native void copyAssets(final String p0, final boolean p1) throws Exception;

    public static native String generateVmessString(final Vmess p0) throws Exception;

    public static native long getFreePort() throws Exception;

    public static native void inputPacket(final byte[] p0);

    public static native Vmess newLiteShadowSocks(final String p0, final long p1, final String p2, final String p3, final byte[] p4);

    public static native Shadowsocks newShadowSocks(final String p0, final long p1, final String p2, final String p3, final byte[] p4);

    public static native Trojan newTrojan(final String p0, final long p1, final String p2, final String p3, final boolean p4, final String p5, final String p6, final String p7, final byte[] p8);

    public static native Vmess newVmess(final String p0, final String p1, final String p2, final String p3, final long p4, final long p5, final String p6, final String p7, final String p8, final String p9, final byte[] p10);

    public static native Vless newVless(final String p0, final long p1, final String p2, final String p3, final String p4, final String p5, final String p6, final String p7, final String p8, final String p9, final String p10, final String p11, final byte[] p12);

    public static native String parseClash(final String p0) throws Exception;

    public static native String peekClash(String var0, long var1) throws Exception;

    public static native long queryOutboundStats(final String p0, final String p1);

    public static native long queryOutboundXStats(final String p0, final String p1);

    public static native long queryStats(final String p0);

    public static native String resolve(final String p0, final boolean p1, final String p2) throws Exception;

    public static native void setLocalDNS(final String p0);

    public static native boolean setNonblock(final long p0, final boolean p1);

    public static native void startShadowsocksTunFd(final long p0, final VpnService p1, final LogService p2, final QuerySpeed p3, final Shadowsocks p4, final String p5) throws Exception;

    public static native void startTrojan(final PacketFlow p0, final VpnService p1, final LogService p2, final Trojan p3, final String p4) throws Exception;

    public static native void startTrojanTunFd(final long p0, final VpnService p1, final LogService p2, final QuerySpeed p3, final Trojan p4, final String p5) throws Exception;

    public static native void startV2Ray(final PacketFlow p0, final VpnService p1, final LogService p2, final QuerySpeed p3, final byte[] p4, final String p5) throws Exception;

    public static native void startV2RayLiteWithTunFd(final long p0, final VpnService p1, final LogService p2, final QuerySpeed p3, final Vmess p4, final String p5) throws Exception;

    public static native void startV2RayWithTunFd(final long p0, final VpnService p1, final LogService p2, final QuerySpeed p3, final Vmess p4, final String p5) throws Exception;

    public static native void startV2RayWithVmess(final PacketFlow p0, final VpnService p1, final LogService p2, final Vmess p3, final String p4) throws Exception;

    public static native void startXRay(final PacketFlow p0, final VpnService p1, final LogService p2, final QuerySpeed p3, final byte[] p4, final String p5) throws Exception;

    public static native void startXRayWithTunFd(final long p0, final VpnService p1, final LogService p2, final QuerySpeed p3, final Vmess p4, final String p5) throws Exception;

    public static native void startXShadowsocksTunFd(final long p0, final VpnService p1, final LogService p2, final QuerySpeed p3, final Shadowsocks p4, final String p5) throws Exception;

    public static native void startXTrojanTunFd(final long p0, final VpnService p1, final LogService p2, final QuerySpeed p3, final Trojan p4, final String p5) throws Exception;

    public static native void startXVlessTunFd(final long p0, final VpnService p1, final LogService p2, final QuerySpeed p3, final Vless p4, final String p5) throws Exception;

    public static native void stopV2Ray();

    public static native void testConfig(final String p0, final String p1) throws Exception;

    public static native long testConfigLatency(final byte[] p0, final String p1) throws Exception;

    public static native long testLinkDownloadSpeed(final String p0, final TestLatencyStop p1) throws Exception;

    public static native long testTCPPing(final String p0, final long p1) throws Exception;

    public static native long testTrojanLatency(final Trojan p0) throws Exception;

    public static native long testURLLatency(final String p0) throws Exception;

    public static native long testVmessLatency(final Vmess p0, final long p1) throws Exception;

    public static native long testVmessLatencyDirect(final Vmess p0) throws Exception;

    public static native String updateV2RayDat() throws Exception;

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

    private static final class proxyQuerySpeed implements Seq.Proxy, QuerySpeed
    {
        private final int refnum;

        @Override
        public final int incRefnum() {
            Seq.incGoRef(this.refnum, this);
            return this.refnum;
        }

        proxyQuerySpeed(final int refnum) {
            Seq.trackGoRef(this.refnum = refnum, this);
        }

        @Override
        public native void updateTraffic(final long p0, final long p1);
    }

    private static final class proxyTestDownload implements Seq.Proxy, TestDownload
    {
        private final int refnum;

        @Override
        public final int incRefnum() {
            Seq.incGoRef(this.refnum, this);
            return this.refnum;
        }

        proxyTestDownload(final int refnum) {
            Seq.trackGoRef(this.refnum = refnum, this);
        }

        @Override
        public native void updateSpeed(final long p0, final long p1, final long p2);

        @Override
        public native void updateTraffic(final long p0, final long p1);
    }

    private static final class proxyTestLatency implements Seq.Proxy, TestLatency
    {
        private final int refnum;

        @Override
        public final int incRefnum() {
            Seq.incGoRef(this.refnum, this);
            return this.refnum;
        }

        proxyTestLatency(final int refnum) {
            Seq.trackGoRef(this.refnum = refnum, this);
        }

        @Override
        public native void updateLatency(final long p0, final long p1);
    }

    private static final class proxyTestLatencyStop implements Seq.Proxy, TestLatencyStop
    {
        private final int refnum;

        @Override
        public final int incRefnum() {
            Seq.incGoRef(this.refnum, this);
            return this.refnum;
        }

        proxyTestLatencyStop(final int refnum) {
            Seq.trackGoRef(this.refnum = refnum, this);
        }

        @Override
        public native boolean updateLatency(final long p0, final long p1);
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