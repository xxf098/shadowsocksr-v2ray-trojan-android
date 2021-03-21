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

    public Trojan(final String s, final long n, final String s2, final String s3, final boolean b, final byte[] array) {
        Seq.trackGoRef(this.refnum = __NewTrojan(s, n, s2, s3, b, array), this);
    }

    private static native int __NewTrojan(final String p0, final long p1, final String p2, final String p3, final boolean p4, final byte[] p5);

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
        return this.getSkipCertVerify() == trojan.getSkipCertVerify();
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(new Object[] { this.getAdd(), this.getPort(), this.getPassword(), this.getSNI(), this.getSkipCertVerify() });
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
        return sb.append("}").toString();
    }

    static {
        Tun2socks.touch();
    }
}
