package tun2socks;

import java.util.Arrays;
import go.Seq;

public final class Shadowsocks implements Seq.Proxy
{
    private final int refnum;

    @Override
    public final int incRefnum() {
        Seq.incGoRef(this.refnum, this);
        return this.refnum;
    }

    Shadowsocks(final int refnum) {
        Seq.trackGoRef(this.refnum = refnum, this);
    }

    public Shadowsocks() {
        Seq.trackGoRef(this.refnum = __New(), this);
    }

    private static native int __New();

    public final native String getAdd();

    public final native void setAdd(final String p0);

    public final native long getPort();

    public final native void setPort(final long p0);

    public final native String getPassword();

    public final native void setPassword(final String p0);

    public final native String getMethod();

    public final native void setMethod(final String p0);

    @Override
    public boolean equals(final Object o) {
        if (o == null || !(o instanceof Shadowsocks)) {
            return false;
        }
        final Shadowsocks shadowsocks = (Shadowsocks)o;
        final String add = this.getAdd();
        final String add2 = shadowsocks.getAdd();
        if (add == null) {
            if (add2 != null) {
                return false;
            }
        }
        else if (!add.equals(add2)) {
            return false;
        }
        if (this.getPort() != shadowsocks.getPort()) {
            return false;
        }
        final String password = this.getPassword();
        final String password2 = shadowsocks.getPassword();
        if (password == null) {
            if (password2 != null) {
                return false;
            }
        }
        else if (!password.equals(password2)) {
            return false;
        }
        final String method = this.getMethod();
        final String method2 = shadowsocks.getMethod();
        if (method == null) {
            if (method2 != null) {
                return false;
            }
        }
        else if (!method.equals(method2)) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(new Object[] { this.getAdd(), this.getPort(), this.getPassword(), this.getMethod() });
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("Shadowsocks").append("{");
        sb.append("Add:").append(this.getAdd()).append(",");
        sb.append("Port:").append(this.getPort()).append(",");
        sb.append("Password:").append(this.getPassword()).append(",");
        sb.append("Method:").append(this.getMethod()).append(",");
        return sb.append("}").toString();
    }

    static {
        Tun2socks.touch();
    }
}