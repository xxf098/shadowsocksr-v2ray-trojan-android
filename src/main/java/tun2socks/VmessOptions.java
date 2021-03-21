package tun2socks;

import java.util.Arrays;
import go.Seq;

public final class VmessOptions implements Seq.Proxy
{
    private final int refnum;

    @Override
    public final int incRefnum() {
        Seq.incGoRef(this.refnum, this);
        return this.refnum;
    }

    VmessOptions(final int refnum) {
        Seq.trackGoRef(this.refnum = refnum, this);
    }

    public VmessOptions() {
        Seq.trackGoRef(this.refnum = __New(), this);
    }

    private static native int __New();

    public final native boolean getUseIPv6();

    public final native void setUseIPv6(final boolean p0);

    public final native String getLoglevel();

    public final native void setLoglevel(final String p0);

    public final native long getRouteMode();

    public final native void setRouteMode(final long p0);

    public final native boolean getEnableSniffing();

    public final native void setEnableSniffing(final boolean p0);

    public final native String getDNS();

    public final native void setDNS(final String p0);

    public final native boolean getAllowInsecure();

    public final native void setAllowInsecure(final boolean p0);

    public final native long getMux();

    public final native void setMux(final long p0);

    public final native long getLocalPort();

    public final native void setLocalPort(final long p0);

    @Override
    public boolean equals(final Object o) {
        if (o == null || !(o instanceof VmessOptions)) {
            return false;
        }
        final VmessOptions vmessOptions = (VmessOptions)o;
        if (this.getUseIPv6() != vmessOptions.getUseIPv6()) {
            return false;
        }
        final String loglevel = this.getLoglevel();
        final String loglevel2 = vmessOptions.getLoglevel();
        if (loglevel == null) {
            if (loglevel2 != null) {
                return false;
            }
        }
        else if (!loglevel.equals(loglevel2)) {
            return false;
        }
        if (this.getRouteMode() != vmessOptions.getRouteMode()) {
            return false;
        }
        if (this.getEnableSniffing() != vmessOptions.getEnableSniffing()) {
            return false;
        }
        final String dns = this.getDNS();
        final String dns2 = vmessOptions.getDNS();
        if (dns == null) {
            if (dns2 != null) {
                return false;
            }
        }
        else if (!dns.equals(dns2)) {
            return false;
        }
        return this.getAllowInsecure() == vmessOptions.getAllowInsecure() && this.getMux() == vmessOptions.getMux() && this.getLocalPort() == vmessOptions.getLocalPort();
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(new Object[] { this.getUseIPv6(), this.getLoglevel(), this.getRouteMode(), this.getEnableSniffing(), this.getDNS(), this.getAllowInsecure(), this.getMux(), this.getLocalPort() });
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("VmessOptions").append("{");
        sb.append("UseIPv6:").append(this.getUseIPv6()).append(",");
        sb.append("Loglevel:").append(this.getLoglevel()).append(",");
        sb.append("RouteMode:").append(this.getRouteMode()).append(",");
        sb.append("EnableSniffing:").append(this.getEnableSniffing()).append(",");
        sb.append("DNS:").append(this.getDNS()).append(",");
        sb.append("AllowInsecure:").append(this.getAllowInsecure()).append(",");
        sb.append("Mux:").append(this.getMux()).append(",");
        sb.append("LocalPort:").append(this.getLocalPort()).append(",");
        return sb.append("}").toString();
    }

    static {
        Tun2socks.touch();
    }
}
