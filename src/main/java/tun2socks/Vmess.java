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

    public Vmess() {
        Seq.trackGoRef(this.refnum = __NewVmess(), this);
    }

    private static native int __NewVmess();

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
        return Arrays.hashCode(new Object[] { this.getHost(), this.getPath(), this.getTLS(), this.getAdd(), this.getPort(), this.getAid(), this.getNet(), this.getID(), this.getLoglevel() });
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
        sb.append("Loglevel:").append(this.getLoglevel()).append(",");
        return sb.append("}").toString();
    }

    static {
        Tun2socks.touch();
    }
}