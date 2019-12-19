package com.github.shadowsocks.libv2ray;

import com.github.shadowsocks.go.Seq;

import java.util.Arrays;

public final class V2RayPoint
        implements Seq.Proxy {
    private final int refnum;

    @Override
    public final int incRefnum() {
        Seq.incGoRef(this.refnum, this);
        return this.refnum;
    }

    public V2RayPoint(V2RayVPNServiceSupportsSet v2RayVPNServiceSupportsSet) {
        this.refnum = V2RayPoint.__NewV2RayPoint(v2RayVPNServiceSupportsSet);
        Seq.trackGoRef(this.refnum, this);
    }

    private static native int __NewV2RayPoint(V2RayVPNServiceSupportsSet var0);

    V2RayPoint(int n) {
        this.refnum = n;
        Seq.trackGoRef(n, this);
    }

    public final native V2RayVPNServiceSupportsSet getSupportSet();

    public final native void setSupportSet(V2RayVPNServiceSupportsSet var1);

    public final native String getPackageName();

    public final native void setPackageName(String var1);

    public final native String getDomainName();

    public final native void setDomainName(String var1);

    public final native String getConfigureFileContent();

    public final native void setConfigureFileContent(String var1);

    public final native boolean getEnableLocalDNS();

    public final native void setEnableLocalDNS(boolean var1);

    public final native boolean getForwardIpv6();

    public final native void setForwardIpv6(boolean var1);

    public native boolean getIsRunning();

    public native long queryStats(String var1, String var2);

    public native void runLoop() throws Exception;

    public native void stopLoop() throws Exception;

    public boolean equals(Object object) {
        boolean bl;
        boolean bl2;
        if (object == null || !(object instanceof V2RayPoint)) {
            return false;
        }
        V2RayPoint v2RayPoint = (V2RayPoint)object;
        V2RayVPNServiceSupportsSet v2RayVPNServiceSupportsSet = this.getSupportSet();
        V2RayVPNServiceSupportsSet v2RayVPNServiceSupportsSet2 = v2RayPoint.getSupportSet();
        if (v2RayVPNServiceSupportsSet == null ? v2RayVPNServiceSupportsSet2 != null : !v2RayVPNServiceSupportsSet.equals(v2RayVPNServiceSupportsSet2)) {
            return false;
        }
        String string = this.getPackageName();
        String string2 = v2RayPoint.getPackageName();
        if (string == null ? string2 != null : !string.equals(string2)) {
            return false;
        }
        String string3 = this.getDomainName();
        String string4 = v2RayPoint.getDomainName();
        if (string3 == null ? string4 != null : !string3.equals(string4)) {
            return false;
        }
        String string5 = this.getConfigureFileContent();
        String string6 = v2RayPoint.getConfigureFileContent();
        if (string5 == null ? string6 != null : !string5.equals(string6)) {
            return false;
        }
        boolean bl3 = this.getEnableLocalDNS();
        if (bl3 != (bl = v2RayPoint.getEnableLocalDNS())) {
            return false;
        }
        boolean bl4 = this.getForwardIpv6();
        return bl4 == (bl2 = v2RayPoint.getForwardIpv6());
    }

    public int hashCode() {
        return Arrays.hashCode(new Object[]{this.getSupportSet(), this.getPackageName(), this.getDomainName(), this.getConfigureFileContent(), this.getEnableLocalDNS(), this.getForwardIpv6()});
    }

    public String toString() {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("V2RayPoint").append("{");
        stringBuilder.append("SupportSet:").append(this.getSupportSet()).append(",");
        stringBuilder.append("PackageName:").append(this.getPackageName()).append(",");
        stringBuilder.append("DomainName:").append(this.getDomainName()).append(",");
        stringBuilder.append("ConfigureFileContent:").append(this.getConfigureFileContent()).append(",");
        stringBuilder.append("EnableLocalDNS:").append(this.getEnableLocalDNS()).append(",");
        stringBuilder.append("ForwardIpv6:").append(this.getForwardIpv6()).append(",");
        return stringBuilder.append("}").toString();
    }

    static {
        Libv2ray.touch();
    }
}