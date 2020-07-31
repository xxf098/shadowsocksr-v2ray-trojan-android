package tun2socks;

import java.util.Arrays;
import go.Seq;

public final class Tun2socksStartOptions implements Seq.Proxy
{
    private final int refnum;

    @Override
    public final int incRefnum() {
        Seq.incGoRef(this.refnum, this);
        return this.refnum;
    }

    Tun2socksStartOptions(final int refnum) {
        Seq.trackGoRef(this.refnum = refnum, this);
    }

    public Tun2socksStartOptions() {
        Seq.trackGoRef(this.refnum = __New(), this);
    }

    private static native int __New();

    public final native long getTunFd();

    public final native void setTunFd(final long p0);

    public final native String getSocks5Server();

    public final native void setSocks5Server(final String p0);

    public final native String getFakeIPRange();

    public final native void setFakeIPRange(final String p0);

    public final native long getMTU();

    public final native void setMTU(final long p0);

    public final native boolean getEnableIPv6();

    public final native void setEnableIPv6(final boolean p0);

    @Override
    public boolean equals(final Object o) {
        if (o == null || !(o instanceof Tun2socksStartOptions)) {
            return false;
        }
        final Tun2socksStartOptions tun2socksStartOptions = (Tun2socksStartOptions)o;
        if (this.getTunFd() != tun2socksStartOptions.getTunFd()) {
            return false;
        }
        final String socks5Server = this.getSocks5Server();
        final String socks5Server2 = tun2socksStartOptions.getSocks5Server();
        if (socks5Server == null) {
            if (socks5Server2 != null) {
                return false;
            }
        }
        else if (!socks5Server.equals(socks5Server2)) {
            return false;
        }
        final String fakeIPRange = this.getFakeIPRange();
        final String fakeIPRange2 = tun2socksStartOptions.getFakeIPRange();
        if (fakeIPRange == null) {
            if (fakeIPRange2 != null) {
                return false;
            }
        }
        else if (!fakeIPRange.equals(fakeIPRange2)) {
            return false;
        }
        return this.getMTU() == tun2socksStartOptions.getMTU() && this.getEnableIPv6() == tun2socksStartOptions.getEnableIPv6();
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(new Object[] { this.getTunFd(), this.getSocks5Server(), this.getFakeIPRange(), this.getMTU(), this.getEnableIPv6() });
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("Tun2socksStartOptions").append("{");
        sb.append("TunFd:").append(this.getTunFd()).append(",");
        sb.append("Socks5Server:").append(this.getSocks5Server()).append(",");
        sb.append("FakeIPRange:").append(this.getFakeIPRange()).append(",");
        sb.append("MTU:").append(this.getMTU()).append(",");
        sb.append("EnableIPv6:").append(this.getEnableIPv6()).append(",");
        return sb.append("}").toString();
    }

    static {
        Tun2socks.touch();
    }
}
