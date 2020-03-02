package tun2socks;

import go.Seq;
import go.Seq.Proxy;
import java.util.Arrays;

public final class Vmess implements Proxy {
    private final int refnum;

    public final int incRefnum() {
        Seq.incGoRef(this.refnum, this);
        return this.refnum;
    }

    public Vmess(String var1, String var2, String var3, String var4, long var5, long var7, String var9, String var10, String var11, String var12, String var13) {
        this.refnum = __NewVmess(var1, var2, var3, var4, var5, var7, var9, var10, var11, var12, var13);
        Seq.trackGoRef(this.refnum, this);
    }

    private static native int __NewVmess(String var0, String var1, String var2, String var3, long var4, long var6, String var8, String var9, String var10, String var11, String var12);

    Vmess(int var1) {
        this.refnum = var1;
        Seq.trackGoRef(var1, this);
    }

    public final native String getHost();

    public final native void setHost(String var1);

    public final native String getPath();

    public final native void setPath(String var1);

    public final native String getTLS();

    public final native void setTLS(String var1);

    public final native String getAdd();

    public final native void setAdd(String var1);

    public final native long getPort();

    public final native void setPort(long var1);

    public final native long getAid();

    public final native void setAid(long var1);

    public final native String getNet();

    public final native void setNet(String var1);

    public final native String getID();

    public final native void setID(String var1);

    public final native String getType();

    public final native void setType(String var1);

    public final native String getSecurity();

    public final native void setSecurity(String var1);

    public final native String getLoglevel();

    public final native void setLoglevel(String var1);

    public boolean equals(Object var1) {
        if (var1 != null && var1 instanceof Vmess) {
            Vmess var2 = (Vmess)var1;
            String var3 = this.getHost();
            String var4 = var2.getHost();
            if (var3 == null) {
                if (var4 != null) {
                    return false;
                }
            } else if (!var3.equals(var4)) {
                return false;
            }

            String var5 = this.getPath();
            String var6 = var2.getPath();
            if (var5 == null) {
                if (var6 != null) {
                    return false;
                }
            } else if (!var5.equals(var6)) {
                return false;
            }

            String var7 = this.getTLS();
            String var8 = var2.getTLS();
            if (var7 == null) {
                if (var8 != null) {
                    return false;
                }
            } else if (!var7.equals(var8)) {
                return false;
            }

            String var9 = this.getAdd();
            String var10 = var2.getAdd();
            if (var9 == null) {
                if (var10 != null) {
                    return false;
                }
            } else if (!var9.equals(var10)) {
                return false;
            }

            long var11 = this.getPort();
            long var13 = var2.getPort();
            if (var11 != var13) {
                return false;
            } else {
                long var15 = this.getAid();
                long var17 = var2.getAid();
                if (var15 != var17) {
                    return false;
                } else {
                    String var19 = this.getNet();
                    String var20 = var2.getNet();
                    if (var19 == null) {
                        if (var20 != null) {
                            return false;
                        }
                    } else if (!var19.equals(var20)) {
                        return false;
                    }

                    String var21 = this.getID();
                    String var22 = var2.getID();
                    if (var21 == null) {
                        if (var22 != null) {
                            return false;
                        }
                    } else if (!var21.equals(var22)) {
                        return false;
                    }

                    String var23 = this.getType();
                    String var24 = var2.getType();
                    if (var23 == null) {
                        if (var24 != null) {
                            return false;
                        }
                    } else if (!var23.equals(var24)) {
                        return false;
                    }

                    String var25 = this.getSecurity();
                    String var26 = var2.getSecurity();
                    if (var25 == null) {
                        if (var26 != null) {
                            return false;
                        }
                    } else if (!var25.equals(var26)) {
                        return false;
                    }

                    String var27 = this.getLoglevel();
                    String var28 = var2.getLoglevel();
                    if (var27 == null) {
                        if (var28 != null) {
                            return false;
                        }
                    } else if (!var27.equals(var28)) {
                        return false;
                    }

                    return true;
                }
            }
        } else {
            return false;
        }
    }

    public int hashCode() {
        return Arrays.hashCode(new Object[]{this.getHost(), this.getPath(), this.getTLS(), this.getAdd(), this.getPort(), this.getAid(), this.getNet(), this.getID(), this.getType(), this.getSecurity(), this.getLoglevel()});
    }

    public String toString() {
        StringBuilder var1 = new StringBuilder();
        var1.append("Vmess").append("{");
        var1.append("Host:").append(this.getHost()).append(",");
        var1.append("Path:").append(this.getPath()).append(",");
        var1.append("TLS:").append(this.getTLS()).append(",");
        var1.append("Add:").append(this.getAdd()).append(",");
        var1.append("Port:").append(this.getPort()).append(",");
        var1.append("Aid:").append(this.getAid()).append(",");
        var1.append("Net:").append(this.getNet()).append(",");
        var1.append("ID:").append(this.getID()).append(",");
        var1.append("Type:").append(this.getType()).append(",");
        var1.append("Security:").append(this.getSecurity()).append(",");
        var1.append("Loglevel:").append(this.getLoglevel()).append(",");
        return var1.append("}").toString();
    }

    static {
        Tun2socks.touch();
    }
}