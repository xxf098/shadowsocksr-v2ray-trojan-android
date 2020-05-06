package tun2socks;

import java.util.Arrays;
import go.Seq;

public final class Vmess implements Seq.Proxy
{
    private final int refnum;

    @Override
    public final int incRefnum() {
        Seq.incGoRef(this.refnum, this);
        return this.refnum;
    }

    public Vmess(final String s, final String s2, final String s3, final String s4, final long n, final long n2, final String s5, final String s6, final String s7, final String s8, final long n3, final String s9, final String s10) {
        Seq.trackGoRef(this.refnum = __NewVmess(s, s2, s3, s4, n, n2, s5, s6, s7, s8, n3, s9, s10), this);
    }

    private static native int __NewVmess(final String p0, final String p1, final String p2, final String p3, final long p4, final long p5, final String p6, final String p7, final String p8, final String p9, final long p10, final String p11, final String p12);

    Vmess(final int refnum) {
        Seq.trackGoRef(this.refnum = refnum, this);
    }

    public final native String getHost();

    public final native void setHost(final String p0);

    public final native String getPath();

    public final native void setPath(final String p0);

    public final native String getTLS();

    public final native void setTLS(final String p0);

    public final native String getAdd();

    public final native void setAdd(final String p0);

    public final native long getPort();

    public final native void setPort(final long p0);

    public final native long getAid();

    public final native void setAid(final long p0);

    public final native String getNet();

    public final native void setNet(final String p0);

    public final native String getID();

    public final native void setID(final String p0);

    public final native String getType();

    public final native void setType(final String p0);

    public final native String getSecurity();

    public final native void setSecurity(final String p0);

    public final native long getRouteMode();

    public final native void setRouteMode(final long p0);

    public final native String getDNS();

    public final native void setDNS(final String p0);

    public final native String getLoglevel();

    public final native void setLoglevel(final String p0);

    @Override
    public boolean equals(final Object o) {
        if (o == null || !(o instanceof Vmess)) {
            return false;
        }
        final Vmess vmess = (Vmess)o;
        final String host = this.getHost();
        final String host2 = vmess.getHost();
        if (host == null) {
            if (host2 != null) {
                return false;
            }
        }
        else if (!host.equals(host2)) {
            return false;
        }
        final String path = this.getPath();
        final String path2 = vmess.getPath();
        if (path == null) {
            if (path2 != null) {
                return false;
            }
        }
        else if (!path.equals(path2)) {
            return false;
        }
        final String tls = this.getTLS();
        final String tls2 = vmess.getTLS();
        if (tls == null) {
            if (tls2 != null) {
                return false;
            }
        }
        else if (!tls.equals(tls2)) {
            return false;
        }
        final String add = this.getAdd();
        final String add2 = vmess.getAdd();
        if (add == null) {
            if (add2 != null) {
                return false;
            }
        }
        else if (!add.equals(add2)) {
            return false;
        }
        if (this.getPort() != vmess.getPort()) {
            return false;
        }
        if (this.getAid() != vmess.getAid()) {
            return false;
        }
        final String net = this.getNet();
        final String net2 = vmess.getNet();
        if (net == null) {
            if (net2 != null) {
                return false;
            }
        }
        else if (!net.equals(net2)) {
            return false;
        }
        final String id = this.getID();
        final String id2 = vmess.getID();
        if (id == null) {
            if (id2 != null) {
                return false;
            }
        }
        else if (!id.equals(id2)) {
            return false;
        }
        final String type = this.getType();
        final String type2 = vmess.getType();
        if (type == null) {
            if (type2 != null) {
                return false;
            }
        }
        else if (!type.equals(type2)) {
            return false;
        }
        final String security = this.getSecurity();
        final String security2 = vmess.getSecurity();
        if (security == null) {
            if (security2 != null) {
                return false;
            }
        }
        else if (!security.equals(security2)) {
            return false;
        }
        if (this.getRouteMode() != vmess.getRouteMode()) {
            return false;
        }
        final String dns = this.getDNS();
        final String dns2 = vmess.getDNS();
        if (dns == null) {
            if (dns2 != null) {
                return false;
            }
        }
        else if (!dns.equals(dns2)) {
            return false;
        }
        final String loglevel = this.getLoglevel();
        final String loglevel2 = vmess.getLoglevel();
        if (loglevel == null) {
            if (loglevel2 != null) {
                return false;
            }
        }
        else if (!loglevel.equals(loglevel2)) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(new Object[] { this.getHost(), this.getPath(), this.getTLS(), this.getAdd(), this.getPort(), this.getAid(), this.getNet(), this.getID(), this.getType(), this.getSecurity(), this.getRouteMode(), this.getDNS(), this.getLoglevel() });
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("Vmess").append("{");
        sb.append("Host:").append(this.getHost()).append(",");
        sb.append("Path:").append(this.getPath()).append(",");
        sb.append("TLS:").append(this.getTLS()).append(",");
        sb.append("Add:").append(this.getAdd()).append(",");
        sb.append("Port:").append(this.getPort()).append(",");
        sb.append("Aid:").append(this.getAid()).append(",");
        sb.append("Net:").append(this.getNet()).append(",");
        sb.append("ID:").append(this.getID()).append(",");
        sb.append("Type:").append(this.getType()).append(",");
        sb.append("Security:").append(this.getSecurity()).append(",");
        sb.append("RouteMode:").append(this.getRouteMode()).append(",");
        sb.append("DNS:").append(this.getDNS()).append(",");
        sb.append("Loglevel:").append(this.getLoglevel()).append(",");
        return sb.append("}").toString();
    }

    static {
        Tun2socks.touch();
    }
}