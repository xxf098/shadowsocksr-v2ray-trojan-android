package tun2socks;

import java.util.Arrays;
import go.Seq;

public final class Vless implements Seq.Proxy
{
    private final int refnum;

    @Override
    public final int incRefnum() {
        Seq.incGoRef(this.refnum, this);
        return this.refnum;
    }

    public Vless(final String s, final long n, final String s2, final String s3, final String s4, final String s5, final String s6, final String s7, final String s8, final String s9, final String s10, final String s11, final byte[] array) {
        Seq.trackGoRef(this.refnum = __NewVless(s, n, s2, s3, s4, s5, s6, s7, s8, s9, s10, s11, array), this);
    }

    private static native int __NewVless(final String p0, final long p1, final String p2, final String p3, final String p4, final String p5, final String p6, final String p7, final String p8, final String p9, final String p10, final String p11, final byte[] p12);

    Vless(final int refnum) {
        Seq.trackGoRef(this.refnum = refnum, this);
    }

    public final native String getTLS();

    public final native void setTLS(final String p0);

    public final native String getAdd();

    public final native void setAdd(final String p0);

    public final native long getPort();

    public final native void setPort(final long p0);

    public final native String getNet();

    public final native void setNet(final String p0);

    public final native String getID();

    public final native void setID(final String p0);

    public final native String getType();

    public final native void setType(final String p0);

    public final native String getSecurity();

    public final native void setSecurity(final String p0);

    public final native String getEncryption();

    public final native void setEncryption(final String p0);

    public final native String getFlow();

    public final native void setFlow(final String p0);

    public final native String getProtocol();

    public final native void setProtocol(final String p0);

    public final native String getPath();

    public final native void setPath(final String p0);

    public final native String getHost();

    public final native void setHost(final String p0);

    public final native String getSNI();

    public final native void setSNI(final String p0);

    @Override
    public boolean equals(final Object o) {
        if (o == null || !(o instanceof Vless)) {
            return false;
        }
        final Vless vless = (Vless)o;
        final String tls = this.getTLS();
        final String tls2 = vless.getTLS();
        if (tls == null) {
            if (tls2 != null) {
                return false;
            }
        }
        else if (!tls.equals(tls2)) {
            return false;
        }
        final String add = this.getAdd();
        final String add2 = vless.getAdd();
        if (add == null) {
            if (add2 != null) {
                return false;
            }
        }
        else if (!add.equals(add2)) {
            return false;
        }
        if (this.getPort() != vless.getPort()) {
            return false;
        }
        final String net = this.getNet();
        final String net2 = vless.getNet();
        if (net == null) {
            if (net2 != null) {
                return false;
            }
        }
        else if (!net.equals(net2)) {
            return false;
        }
        final String id = this.getID();
        final String id2 = vless.getID();
        if (id == null) {
            if (id2 != null) {
                return false;
            }
        }
        else if (!id.equals(id2)) {
            return false;
        }
        final String type = this.getType();
        final String type2 = vless.getType();
        if (type == null) {
            if (type2 != null) {
                return false;
            }
        }
        else if (!type.equals(type2)) {
            return false;
        }
        final String security = this.getSecurity();
        final String security2 = vless.getSecurity();
        if (security == null) {
            if (security2 != null) {
                return false;
            }
        }
        else if (!security.equals(security2)) {
            return false;
        }
        final String encryption = this.getEncryption();
        final String encryption2 = vless.getEncryption();
        if (encryption == null) {
            if (encryption2 != null) {
                return false;
            }
        }
        else if (!encryption.equals(encryption2)) {
            return false;
        }
        final String flow = this.getFlow();
        final String flow2 = vless.getFlow();
        if (flow == null) {
            if (flow2 != null) {
                return false;
            }
        }
        else if (!flow.equals(flow2)) {
            return false;
        }
        final String protocol = this.getProtocol();
        final String protocol2 = vless.getProtocol();
        if (protocol == null) {
            if (protocol2 != null) {
                return false;
            }
        }
        else if (!protocol.equals(protocol2)) {
            return false;
        }
        final String path = this.getPath();
        final String path2 = vless.getPath();
        if (path == null) {
            if (path2 != null) {
                return false;
            }
        }
        else if (!path.equals(path2)) {
            return false;
        }
        final String host = this.getHost();
        final String host2 = vless.getHost();
        if (host == null) {
            if (host2 != null) {
                return false;
            }
        }
        else if (!host.equals(host2)) {
            return false;
        }
        final String sni = this.getSNI();
        final String sni2 = vless.getSNI();
        if (sni == null) {
            if (sni2 != null) {
                return false;
            }
        }
        else if (!sni.equals(sni2)) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(new Object[] { this.getTLS(), this.getAdd(), this.getPort(), this.getNet(), this.getID(), this.getType(), this.getSecurity(), this.getEncryption(), this.getFlow(), this.getProtocol(), this.getPath(), this.getHost(), this.getSNI() });
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("Vless").append("{");
        sb.append("TLS:").append(this.getTLS()).append(",");
        sb.append("Add:").append(this.getAdd()).append(",");
        sb.append("Port:").append(this.getPort()).append(",");
        sb.append("Net:").append(this.getNet()).append(",");
        sb.append("ID:").append(this.getID()).append(",");
        sb.append("Type:").append(this.getType()).append(",");
        sb.append("Security:").append(this.getSecurity()).append(",");
        sb.append("Encryption:").append(this.getEncryption()).append(",");
        sb.append("Flow:").append(this.getFlow()).append(",");
        sb.append("Protocol:").append(this.getProtocol()).append(",");
        sb.append("Path:").append(this.getPath()).append(",");
        sb.append("Host:").append(this.getHost()).append(",");
        sb.append("SNI:").append(this.getSNI()).append(",");
        return sb.append("}").toString();
    }

    static {
        Tun2socks.touch();
    }
}