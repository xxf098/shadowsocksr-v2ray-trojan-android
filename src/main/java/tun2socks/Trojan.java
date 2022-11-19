package tun2socks;

import java.util.Arrays;
import go.Seq;

public final class Trojan implements Seq.Proxy
{
    private final int refnum;

    @Override
    public final int incRefnum() {
        Seq.incGoRef(this.refnum, this);
        return this.refnum;
    }

    public Trojan(final String s, final long n, final String s2, final String s3, final boolean b, final String s4, final String s5, final String s6, final byte[] array) {
        Seq.trackGoRef(this.refnum = __NewTrojan(s, n, s2, s3, b, s4, s5, s6, array), this);
    }

    private static native int __NewTrojan(final String p0, final long p1, final String p2, final String p3, final boolean p4, final String p5, final String p6, final String p7, final byte[] p8);

    Trojan(final int refnum) {
        Seq.trackGoRef(this.refnum = refnum, this);
    }

    public final native String getAdd();

    public final native void setAdd(final String p0);

    public final native long getPort();

    public final native void setPort(final long p0);

    public final native String getPassword();

    public final native void setPassword(final String p0);

    public final native String getSNI();

    public final native void setSNI(final String p0);

    public final native boolean getSkipCertVerify();

    public final native void setSkipCertVerify(final boolean p0);

    public final native String getNet();

    public final native void setNet(final String p0);

    public final native String getPath();

    public final native void setPath(final String p0);

    public final native String getHost();

    public final native void setHost(final String p0);

    @Override
    public boolean equals(final Object o) {
        if (o == null || !(o instanceof Trojan)) {
            return false;
        }
        final Trojan trojan = (Trojan)o;
        final String add = this.getAdd();
        final String add2 = trojan.getAdd();
        if (add == null) {
            if (add2 != null) {
                return false;
            }
        }
        else if (!add.equals(add2)) {
            return false;
        }
        if (this.getPort() != trojan.getPort()) {
            return false;
        }
        final String password = this.getPassword();
        final String password2 = trojan.getPassword();
        if (password == null) {
            if (password2 != null) {
                return false;
            }
        }
        else if (!password.equals(password2)) {
            return false;
        }
        final String sni = this.getSNI();
        final String sni2 = trojan.getSNI();
        if (sni == null) {
            if (sni2 != null) {
                return false;
            }
        }
        else if (!sni.equals(sni2)) {
            return false;
        }
        if (this.getSkipCertVerify() != trojan.getSkipCertVerify()) {
            return false;
        }
        final String net = this.getNet();
        final String net2 = trojan.getNet();
        if (net == null) {
            if (net2 != null) {
                return false;
            }
        }
        else if (!net.equals(net2)) {
            return false;
        }
        final String path = this.getPath();
        final String path2 = trojan.getPath();
        if (path == null) {
            if (path2 != null) {
                return false;
            }
        }
        else if (!path.equals(path2)) {
            return false;
        }
        final String host = this.getHost();
        final String host2 = trojan.getHost();
        if (host == null) {
            if (host2 != null) {
                return false;
            }
        }
        else if (!host.equals(host2)) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(new Object[] { this.getAdd(), this.getPort(), this.getPassword(), this.getSNI(), this.getSkipCertVerify(), this.getNet(), this.getPath(), this.getHost() });
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("Trojan").append("{");
        sb.append("Add:").append(this.getAdd()).append(",");
        sb.append("Port:").append(this.getPort()).append(",");
        sb.append("Password:").append(this.getPassword()).append(",");
        sb.append("SNI:").append(this.getSNI()).append(",");
        sb.append("SkipCertVerify:").append(this.getSkipCertVerify()).append(",");
        sb.append("Net:").append(this.getNet()).append(",");
        sb.append("Path:").append(this.getPath()).append(",");
        sb.append("Host:").append(this.getHost()).append(",");
        return sb.append("}").toString();
    }

    static {
        Tun2socks.touch();
    }
}