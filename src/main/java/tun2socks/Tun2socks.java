package tun2socks;

import go.Seq;
import go.Seq.Proxy;

public abstract class Tun2socks {
    public static void touch() {
    }

    private static native void _init();

    public static native String checkVersion();

    public static native void copyAssets(String var0, boolean var1) throws Exception;

    public static native String generateVmessString(Vmess var0) throws Exception;

    public static native void inputPacket(byte[] var0);

    public static native Vmess newVmess(String var0, String var1, String var2, String var3, long var4, long var6, String var8, String var9, String var10, String var11, String var12);

    public static native void setLocalDNS(String var0);

    public static native boolean setNonblock(long var0, boolean var2);

    public static native void startV2Ray(PacketFlow var0, VpnService var1, LogService var2, byte[] var3, String var4) throws Exception;

    public static native void startV2RayWithVmess(PacketFlow var0, VpnService var1, LogService var2, Vmess var3, String var4) throws Exception;

    public static native void stopV2Ray();

    public static native void testConfig(String var0, String var1) throws Exception;

    public static native long testConfigLatency(byte[] var0, String var1) throws Exception;

    public static native long testVmessLatency(Vmess var0, String var1) throws Exception;

    static {
        Seq.touch();
        _init();
    }

    private static final class proxyVpnService implements Proxy, VpnService {
        private final int refnum;

        public final int incRefnum() {
            Seq.incGoRef(this.refnum, this);
            return this.refnum;
        }

        proxyVpnService(int var1) {
            this.refnum = var1;
            Seq.trackGoRef(var1, this);
        }

        public native boolean protect(long var1);
    }

    private static final class proxyPacketFlow implements Proxy, PacketFlow {
        private final int refnum;

        public final int incRefnum() {
            Seq.incGoRef(this.refnum, this);
            return this.refnum;
        }

        proxyPacketFlow(int var1) {
            this.refnum = var1;
            Seq.trackGoRef(var1, this);
        }

        public native void writePacket(byte[] var1);
    }

    private static final class proxyLogService implements Proxy, LogService {
        private final int refnum;

        public final int incRefnum() {
            Seq.incGoRef(this.refnum, this);
            return this.refnum;
        }

        proxyLogService(int var1) {
            this.refnum = var1;
            Seq.trackGoRef(var1, this);
        }

        public native void writeLog(String var1) throws Exception;
    }
}